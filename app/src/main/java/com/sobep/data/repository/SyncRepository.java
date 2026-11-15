package com.sobep.data.repository;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.Blob;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.Source;
import com.google.firebase.firestore.WriteBatch;
import com.sobep.data.local.AppDatabase;
import com.sobep.data.local.dao.RecipeDao;
import com.sobep.data.local.entity.Ingredient;
import com.sobep.data.local.entity.Recipe;
import com.sobep.data.local.entity.Step;
import com.sobep.util.CloudPhoto;
import com.sobep.util.ImageStorage;
import com.sobep.util.SyncMerge;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * CN14: đồng bộ món ăn giữa Room (trong máy) và Cloud Firestore (trên mây), theo uid người đang đăng nhập.
 *
 * Dữ liệu trên Firestore: users/{uid}/recipes/{cloudId}, mỗi món một document,
 * nguyên liệu và các bước lưu thành 2 mảng ngay trong document (đọc 1 món = 1 lượt đọc, đỡ tốn hạn mức miễn phí).
 *
 * Ảnh món (14/11/2026): không dùng Firebase Storage (cần gói trả phí) mà làm giống ảnh Cộng đồng (bước 8d):
 * nén JPEG ≤ 300 KB (CloudPhoto) để ở document riêng users/{uid}/recipes/{cloudId}/photo/main { data: Blob }.
 * Document món có thêm field photoId = tên file ảnh (null = không có ảnh). Máy tải ảnh về lưu đúng tên đó,
 * nên hai máy cùng photoId là cùng ảnh → sửa chữ / bấm yêu thích không phải gửi hay tải lại ảnh (SyncMerge.decidePhoto).
 * Ảnh luôn được ghi TRƯỚC document món: document đã ghi photoId thì chắc chắn ảnh đã có trên mây.
 *
 * Xoá món: không xoá hẳn document mà ghi "bia mộ" { deleted: true, updatedAt } → máy khác đồng bộ sẽ biết món đã
 * bị xoá (nếu xoá hẳn, máy khác còn món đó sẽ gửi lên lại, món "sống lại").
 *
 * Chạy trên databaseWriteExecutor (luồng nền) nên được phép chờ Firestore bằng Tasks.await.
 */
public class SyncRepository {

    private static final String TAG = "SyncRepository";

    // Tên collection / field trên Firestore
    private static final String COLLECTION_USERS = "users";
    private static final String COLLECTION_RECIPES = "recipes";
    private static final String F_NAME = "name";
    private static final String F_CATEGORY = "category";
    private static final String F_COOK_TIME = "cookTimeMinutes";
    private static final String F_SERVINGS = "servings";
    private static final String F_VIDEO_URL = "videoUrl";
    private static final String F_NOTE = "note";
    private static final String F_FAVORITE = "favorite";
    private static final String F_SOURCE_SAMPLE_ID = "sourceSampleId";
    private static final String F_CREATED_AT = "createdAt";
    private static final String F_UPDATED_AT = "updatedAt";
    private static final String F_DELETED = "deleted";
    private static final String F_INGREDIENTS = "ingredients";
    private static final String F_STEPS = "steps";
    private static final String F_UNIT = "unit";
    private static final String F_QUANTITY = "quantity";
    private static final String F_CONTENT = "content";
    private static final String F_TIMER = "timerMinutes";
    // Ảnh món: field photoId trong document món + document con photo/main { data: Blob }
    private static final String F_PHOTO_ID = "photoId";
    private static final String COLLECTION_PHOTO = "photo";
    private static final String DOC_PHOTO_MAIN = "main";
    private static final String F_PHOTO_DATA = "data";

    // Chờ máy chủ tối đa 30 giây; quá thì coi như mất mạng (Firestore không tự báo lỗi khi mất mạng lúc ghi)
    private static final long TIMEOUT_SECONDS = 30;
    // Một WriteBatch của Firestore tối đa 500 thao tác; chia nhỏ hơn cho chắc
    private static final int BATCH_LIMIT = 400;

    private static final String PREFS = "sync";
    private static final String KEY_LAST_SYNC = "lastSyncAt_";

    /** Số món đã xử lý trong một lần đồng bộ, để báo cho người dùng. */
    public static class SyncResult {
        public int uploaded;
        public int downloaded;
        public int deleted;

        public boolean nothingChanged() {
            return uploaded == 0 && downloaded == 0 && deleted == 0;
        }
    }

    /** Kết quả đồng bộ. Lưu ý: được gọi ở LUỒNG NỀN, ViewModel phải dùng postValue. */
    public interface SyncCallback {
        void onSuccess(@NonNull SyncResult result);

        void onError(@NonNull Exception error);
    }

    private final Application application;
    private final RecipeDao recipeDao;
    private final ExecutorService executor;
    private final FirebaseAuth auth;
    private final FirebaseFirestore firestore;
    private final SharedPreferences prefs;

    public SyncRepository(Application application) {
        this.application = application;
        recipeDao = AppDatabase.getInstance(application).recipeDao();
        executor = AppDatabase.databaseWriteExecutor;
        auth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();
        prefs = application.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Thời điểm đồng bộ thành công gần nhất của tài khoản đang đăng nhập; 0 = chưa từng. */
    public long getLastSyncAt() {
        FirebaseUser user = auth.getCurrentUser();
        return user == null ? 0 : prefs.getLong(KEY_LAST_SYNC + user.getUid(), 0);
    }

    /** Bấm "Đồng bộ ngay": gộp 2 chiều máy ↔ mây. */
    public void syncNow(@NonNull SyncCallback callback) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            callback.onError(new IllegalStateException("Chưa đăng nhập"));
            return;
        }
        String uid = user.getUid();
        executor.execute(() -> {
            try {
                SyncResult result = runSync(uid);
                prefs.edit().putLong(KEY_LAST_SYNC + uid, System.currentTimeMillis()).apply();
                callback.onSuccess(result);
            } catch (Exception e) {
                Log.w(TAG, "Đồng bộ thất bại", e);
                callback.onError(unwrap(e));
            }
        });
    }

    /**
     * Xoá món trong máy → ghi bia mộ lên mây (nếu đang đăng nhập).
     * Không chờ kết quả: mất mạng thì Firestore tự giữ lệnh ghi trong máy và gửi khi có mạng lại.
     * Chưa đăng nhập thì bỏ qua (giới hạn đã biết: món có thể được tải lại từ mây ở lần đồng bộ sau).
     */
    public void markDeleted(@Nullable String cloudId) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null || cloudId == null) {
            return;
        }
        Map<String, Object> tombstone = new HashMap<>();
        tombstone.put(F_DELETED, true);
        tombstone.put(F_UPDATED_AT, System.currentTimeMillis());
        // Bia mộ + xoá ảnh trên mây chung một WriteBatch (món đã xoá thì không giữ ảnh tốn chỗ)
        WriteBatch batch = firestore.batch();
        batch.set(recipesOf(user.getUid()).document(cloudId), tombstone);
        batch.delete(photoOf(user.getUid(), cloudId));
        batch.commit()
                .addOnFailureListener(e -> Log.w(TAG, "Không ghi được bia mộ cho " + cloudId, e));
    }

    // ---------- Gộp máy ↔ mây ----------

    private SyncResult runSync(String uid) throws Exception {
        CollectionReference recipes = recipesOf(uid);
        // Source.SERVER: bắt buộc hỏi máy chủ. Mất mạng thì báo lỗi luôn,
        // thay vì đọc bộ nhớ đệm (có thể cũ / trống) rồi gộp sai
        QuerySnapshot snapshot = Tasks.await(recipes.get(Source.SERVER), TIMEOUT_SECONDS, TimeUnit.SECONDS);
        Map<String, DocumentSnapshot> remoteById = new HashMap<>();
        for (DocumentSnapshot doc : snapshot.getDocuments()) {
            remoteById.put(doc.getId(), doc);
        }

        SyncResult result = new SyncResult();
        // Các document cần gửi lên: cloudId → dữ liệu. Gửi gộp ở cuối bằng WriteBatch
        Map<String, Map<String, Object>> uploads = new HashMap<>();
        // Ảnh trên mây cần xoá (món đã bỏ ảnh ở máy này) – xoá chung batch với document món
        List<String> photoDeletes = new ArrayList<>();

        // 1. Duyệt từng món trong máy, so với bản trên mây cùng cloudId
        for (Recipe local : recipeDao.getAllNow()) {
            if (local.cloudId == null) {
                // Không xảy ra sau MIGRATION_2_3, phòng hờ
                local.cloudId = Recipe.newCloudId(local.sourceSampleId);
                recipeDao.updateRecipe(local);
            }
            DocumentSnapshot remote = remoteById.remove(local.cloudId);
            Long remoteUpdatedAt = remote == null ? null : remote.getLong(F_UPDATED_AT);
            boolean remoteDeleted = remote != null && Boolean.TRUE.equals(remote.getBoolean(F_DELETED));
            String localPhotoId = localPhotoIdOf(local);
            boolean remoteHasPhotoField = remote != null && remote.contains(F_PHOTO_ID);
            String remotePhotoId = remote == null ? null : stringOr(remote.get(F_PHOTO_ID), null);

            SyncMerge.Action action = SyncMerge.decide(local.updatedAt, remoteUpdatedAt, remoteDeleted);
            if (action == SyncMerge.Action.NONE && remote != null && !remoteDeleted
                    && !remoteHasPhotoField && localPhotoId != null) {
                // Món đã đồng bộ từ trước khi có đồng bộ ảnh (trên mây chưa có photoId) → gửi lên lại kèm ảnh
                action = SyncMerge.Action.UPLOAD;
            }

            if (action == SyncMerge.Action.UPLOAD) {
                Map<String, Object> doc = toDocument(local,
                        recipeDao.getIngredientsNow(local.id), recipeDao.getStepsNow(local.id));
                putPhoto(uid, local, localPhotoId, remotePhotoId, doc, photoDeletes);
                uploads.put(local.cloudId, doc);
                result.uploaded++;
            } else if (action == SyncMerge.Action.DOWNLOAD && remote != null) {
                Recipe fromCloud = toRecipe(remote);
                fromCloud.id = local.id;
                fromCloud.imagePath = photoForDownload(uid, remote, local.imagePath, localPhotoId);
                recipeDao.replaceSyncedRecipe(fromCloud, toIngredients(remote), toSteps(remote));
                // Ảnh đã đổi / bị bỏ → xoá file ảnh cũ trong máy (sau khi CSDL đã ghi xong)
                if (local.imagePath != null && !local.imagePath.equals(fromCloud.imagePath)) {
                    ImageStorage.deleteRecipePhoto(local.imagePath);
                }
                result.downloaded++;
            } else if (action == SyncMerge.Action.DELETE_LOCAL) {
                recipeDao.deleteRecipe(local);
                ImageStorage.deleteRecipePhoto(local.imagePath);
                result.deleted++;
            }
        }

        // 2. Còn lại: món chỉ có trên mây (vd thêm ở máy khác) → tải về (kèm ảnh nếu có), trừ bia mộ
        for (DocumentSnapshot remote : remoteById.values()) {
            boolean remoteDeleted = Boolean.TRUE.equals(remote.getBoolean(F_DELETED));
            if (SyncMerge.decide(null, remote.getLong(F_UPDATED_AT), remoteDeleted) == SyncMerge.Action.DOWNLOAD) {
                Recipe fromCloud = toRecipe(remote);
                fromCloud.imagePath = photoForDownload(uid, remote, null, null);
                recipeDao.insertSyncedRecipe(fromCloud, toIngredients(remote), toSteps(remote));
                result.downloaded++;
            }
        }

        // 3. Gửi các món cần gửi lên (+ xoá ảnh cũ trên mây), mỗi batch tối đa BATCH_LIMIT thao tác
        WriteBatch batch = firestore.batch();
        int count = 0;
        for (Map.Entry<String, Map<String, Object>> entry : uploads.entrySet()) {
            batch.set(recipes.document(entry.getKey()), entry.getValue());
            count++;
            if (count == BATCH_LIMIT) {
                Tasks.await(batch.commit(), TIMEOUT_SECONDS, TimeUnit.SECONDS);
                batch = firestore.batch();
                count = 0;
            }
        }
        for (String cloudId : photoDeletes) {
            batch.delete(photoOf(uid, cloudId));
            count++;
            if (count == BATCH_LIMIT) {
                Tasks.await(batch.commit(), TIMEOUT_SECONDS, TimeUnit.SECONDS);
                batch = firestore.batch();
                count = 0;
            }
        }
        if (count > 0) {
            Tasks.await(batch.commit(), TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }
        return result;
    }

    // ---------- Ảnh món ----------

    /** Mã ảnh của món trong máy (tên file); không có ảnh hoặc file đã mất → null. */
    @Nullable
    private static String localPhotoIdOf(Recipe recipe) {
        if (recipe.imagePath == null || !new File(recipe.imagePath).exists()) {
            return null;
        }
        return CloudPhoto.photoIdOf(recipe.imagePath);
    }

    /**
     * Gửi món lên: ghi field photoId vào document món, và nếu trên mây chưa có đúng ảnh này thì nén rồi ghi
     * photo/main NGAY (trước document món – xem chú thích đầu lớp). Ghi từng ảnh một, không gom vào batch,
     * vì mỗi ảnh tới 300 KB (một lần ghi của Firestore tối đa 10 MB).
     * Món không có ảnh mà trên mây còn ảnh cũ → đưa vào danh sách xoá.
     */
    private void putPhoto(String uid, Recipe local, @Nullable String localPhotoId, @Nullable String remotePhotoId,
                          Map<String, Object> doc, List<String> photoDeletes) throws Exception {
        if (localPhotoId == null) {
            doc.put(F_PHOTO_ID, null);
            if (remotePhotoId != null) {
                photoDeletes.add(local.cloudId);
            }
            return;
        }
        if (SyncMerge.needsPhotoUpload(localPhotoId, remotePhotoId)) {
            byte[] bytes = ImageStorage.compressForUpload(application, local.imagePath,
                    CloudPhoto.PHOTO_MAX_SIZE, CloudPhoto.PHOTO_QUALITY, CloudPhoto.PHOTO_MAX_BYTES);
            if (bytes == null) {
                // Nén lỗi (hiếm): không ghi field photoId → máy khác giữ nguyên ảnh của nó
                Log.w(TAG, "Không nén được ảnh của món " + local.cloudId);
                return;
            }
            Map<String, Object> photoDoc = new HashMap<>();
            photoDoc.put(F_PHOTO_DATA, Blob.fromBytes(bytes));
            Tasks.await(photoOf(uid, local.cloudId).set(photoDoc), TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }
        doc.put(F_PHOTO_ID, localPhotoId);
    }

    /**
     * Tải món về: trả về đường dẫn ảnh món sẽ ghi vào CSDL (SyncMerge.decidePhoto quyết định giữ / tải / bỏ).
     * Tải ảnh lỗi mạng → ném lỗi, cả lần đồng bộ báo lỗi (lần sau làm lại). Trên mây thiếu ảnh → giữ ảnh cũ.
     */
    @Nullable
    private String photoForDownload(String uid, DocumentSnapshot remote, @Nullable String currentPath,
                                    @Nullable String localPhotoId) throws Exception {
        String remotePhotoId = stringOr(remote.get(F_PHOTO_ID), null);
        SyncMerge.PhotoAction action = SyncMerge.decidePhoto(localPhotoId, remote.contains(F_PHOTO_ID), remotePhotoId);
        if (action == SyncMerge.PhotoAction.REMOVE) {
            return null;
        }
        if (action == SyncMerge.PhotoAction.KEEP) {
            return currentPath;
        }
        DocumentSnapshot photo = Tasks.await(photoOf(uid, remote.getId()).get(Source.SERVER),
                TIMEOUT_SECONDS, TimeUnit.SECONDS);
        Object data = photo.get(F_PHOTO_DATA);
        if (!(data instanceof Blob)) {
            return currentPath;
        }
        String saved = ImageStorage.saveRecipePhotoBytes(application, ((Blob) data).toBytes(), remotePhotoId);
        return saved != null ? saved : currentPath;
    }

    private CollectionReference recipesOf(String uid) {
        return firestore.collection(COLLECTION_USERS).document(uid).collection(COLLECTION_RECIPES);
    }

    /** users/{uid}/recipes/{cloudId}/photo/main – nơi để ảnh của món. */
    private DocumentReference photoOf(String uid, String cloudId) {
        return recipesOf(uid).document(cloudId).collection(COLLECTION_PHOTO).document(DOC_PHOTO_MAIN);
    }

    /** Tasks.await bọc lỗi thật trong ExecutionException → lấy lỗi gốc để ViewModel đổi thành câu báo. */
    private static Exception unwrap(Exception e) {
        if (e instanceof ExecutionException && e.getCause() instanceof Exception) {
            return (Exception) e.getCause();
        }
        return e;
    }

    // ---------- Chuyển đổi Room ↔ Firestore ----------
    // Viết tay từng field (không dùng toObject) để thấy rõ trên mây lưu gì, và KHÔNG gửi id / imagePath của máy

    private static Map<String, Object> toDocument(Recipe recipe, List<Ingredient> ingredients, List<Step> steps) {
        Map<String, Object> doc = new HashMap<>();
        doc.put(F_NAME, recipe.name);
        doc.put(F_CATEGORY, recipe.category);
        doc.put(F_COOK_TIME, recipe.cookTimeMinutes);
        doc.put(F_SERVINGS, recipe.servings);
        doc.put(F_VIDEO_URL, recipe.videoUrl);
        doc.put(F_NOTE, recipe.note);
        doc.put(F_FAVORITE, recipe.favorite);
        doc.put(F_SOURCE_SAMPLE_ID, recipe.sourceSampleId);
        doc.put(F_CREATED_AT, recipe.createdAt);
        doc.put(F_UPDATED_AT, recipe.updatedAt);
        doc.put(F_DELETED, false);

        List<Map<String, Object>> ingredientList = new ArrayList<>();
        for (Ingredient ingredient : ingredients) {
            Map<String, Object> item = new HashMap<>();
            item.put(F_NAME, ingredient.name);
            item.put(F_QUANTITY, ingredient.quantity);
            item.put(F_UNIT, ingredient.unit);
            ingredientList.add(item);
        }
        doc.put(F_INGREDIENTS, ingredientList);

        List<Map<String, Object>> stepList = new ArrayList<>();
        for (Step step : steps) {
            Map<String, Object> item = new HashMap<>();
            item.put(F_CONTENT, step.content);
            item.put(F_TIMER, step.timerMinutes);
            stepList.add(item);
        }
        doc.put(F_STEPS, stepList);
        return doc;
    }

    private static Recipe toRecipe(DocumentSnapshot doc) {
        Recipe recipe = new Recipe();
        recipe.cloudId = doc.getId();
        recipe.name = stringOr(doc.getString(F_NAME), "");
        recipe.category = stringOr(doc.getString(F_CATEGORY), Recipe.CATEGORY_MAIN);
        recipe.cookTimeMinutes = (int) longOr(doc.getLong(F_COOK_TIME), 0);
        recipe.servings = (int) longOr(doc.getLong(F_SERVINGS), 2);
        recipe.videoUrl = doc.getString(F_VIDEO_URL);
        recipe.note = doc.getString(F_NOTE);
        recipe.favorite = Boolean.TRUE.equals(doc.getBoolean(F_FAVORITE));
        recipe.sourceSampleId = doc.getString(F_SOURCE_SAMPLE_ID);
        recipe.createdAt = longOr(doc.getLong(F_CREATED_AT), 0);
        recipe.updatedAt = longOr(doc.getLong(F_UPDATED_AT), 0);
        return recipe;
    }

    // Thứ tự trong mảng = thứ tự hiển thị; DAO tự đánh lại position khi lưu
    private static List<Ingredient> toIngredients(DocumentSnapshot doc) {
        List<Ingredient> result = new ArrayList<>();
        for (Map<?, ?> item : mapList(doc.get(F_INGREDIENTS))) {
            Ingredient ingredient = new Ingredient();
            ingredient.name = stringOr(item.get(F_NAME), "");
            ingredient.quantity = numberOr(item.get(F_QUANTITY)).doubleValue();
            ingredient.unit = stringOr(item.get(F_UNIT), "");
            result.add(ingredient);
        }
        return result;
    }

    private static List<Step> toSteps(DocumentSnapshot doc) {
        List<Step> result = new ArrayList<>();
        for (Map<?, ?> item : mapList(doc.get(F_STEPS))) {
            Step step = new Step();
            step.content = stringOr(item.get(F_CONTENT), "");
            step.timerMinutes = numberOr(item.get(F_TIMER)).intValue();
            result.add(step);
        }
        return result;
    }

    // Firestore trả mảng dạng List<Object>; chỉ lấy phần tử là Map (bỏ qua dữ liệu hỏng)
    private static List<Map<?, ?>> mapList(@Nullable Object value) {
        List<Map<?, ?>> result = new ArrayList<>();
        if (value instanceof List) {
            for (Object item : (List<?>) value) {
                if (item instanceof Map) {
                    result.add((Map<?, ?>) item);
                }
            }
        }
        return result;
    }

    private static String stringOr(@Nullable Object value, String fallback) {
        return value instanceof String ? (String) value : fallback;
    }

    private static long longOr(@Nullable Long value, long fallback) {
        return value != null ? value : fallback;
    }

    // Số trên Firestore có thể về dạng Long (2) hoặc Double (2.5) → đọc chung qua Number
    private static Number numberOr(@Nullable Object value) {
        return value instanceof Number ? (Number) value : 0;
    }
}
