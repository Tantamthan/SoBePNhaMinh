package com.sobep.data.repository;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.Blob;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.Source;
import com.google.firebase.firestore.WriteBatch;
import com.sobep.data.community.CommunityRecipe;
import com.sobep.data.local.entity.Ingredient;
import com.sobep.data.local.entity.Recipe;
import com.sobep.data.local.entity.Step;
import com.sobep.util.CloudPhoto;
import com.sobep.util.CommunityMapper;
import com.sobep.util.ImageStorage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * CN15: tab Cộng đồng – đăng món, tải bài mới nhất, gỡ bài của mình, báo cáo bài của người khác.
 *
 * Dữ liệu trên Firestore:
 *   community_recipes/{uid}_{cloudId}                 một bài (xem CommunityMapper), kèm "thumb" nếu có ảnh
 *   community_recipes/{postId}/photo/main             ảnh lớn { data: Blob } (bước 8d, xem CloudPhoto)
 *   community_recipes/{postId}/reports/{uid người báo} một báo cáo; chỉ xem được trên Firebase Console
 * Quyền đọc / ghi do Security Rules (file firestore.rules) kiểm tra ở MÁY CHỦ.
 *
 * Bước 8d – ảnh KHÔNG dùng Firebase Storage (bắt buộc gói Blaze trả phí) mà nén nhỏ rồi lưu thẳng vào
 * Firestore dưới dạng Blob. Bài + ảnh ghi chung một WriteBatch: thành công cả hai hoặc không ghi gì.
 *
 * Không đụng Room: lưu bài vào sổ đi qua RecipeRepository giống món mẫu.
 * Firebase gọi callback trên main thread → ViewModel setValue được ngay.
 */
public class CommunityRepository {

    private static final String TAG = "CommunityRepository";

    private static final String COLLECTION_COMMUNITY = "community_recipes";
    private static final String COLLECTION_REPORTS = "reports";
    private static final String COLLECTION_PHOTO = "photo";
    private static final String DOC_PHOTO_MAIN = "main";
    private static final String F_REASON = "reason";
    private static final String F_REPORTED_AT = "reportedAt";

    /** Lý do báo cáo – phải khớp danh sách trong firestore.rules. */
    public static final String REASON_INAPPROPRIATE = "inappropriate";
    public static final String REASON_SPAM = "spam";
    public static final String REASON_OTHER = "other";

    // Chỉ tải 50 bài mới nhất: đủ cho đồ án, đỡ tốn lượt đọc miễn phí (50.000 lượt/ngày)
    private static final int PAGE_SIZE = 50;
    // Chờ máy chủ tối đa 15 giây. Lưu ý: khi mất mạng, lệnh GHI của Firestore không báo lỗi mà chờ mãi
    // (giữ trong máy, có mạng thì tự gửi) → hết giờ sẽ báo TimeoutException
    private static final long TIMEOUT_SECONDS = 15;

    // Bài đã báo cáo → ẩn trên máy này (mỗi tài khoản một danh sách)
    private static final String PREFS = "community";
    private static final String KEY_HIDDEN = "hidden_";

    // Luồng nền nén ảnh (đọc file + xử lý Bitmap không được chạy trên main thread), dùng chung cho mọi đối tượng
    private static final ExecutorService PHOTO_EXECUTOR = Executors.newSingleThreadExecutor();

    /** Kết quả trả về ViewModel (gọi trên main thread). */
    public interface Callback<T> {
        void onSuccess(T result);

        void onError(@NonNull Exception error);
    }

    private final Application application;
    private final FirebaseAuth auth;
    private final FirebaseFirestore firestore;
    private final SharedPreferences prefs;

    public CommunityRepository(Application application) {
        this.application = application;
        auth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();
        prefs = application.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** uid người đang đăng nhập; null = chưa đăng nhập (tab Cộng đồng bị ẩn). */
    @Nullable
    public String getCurrentUid() {
        FirebaseUser user = auth.getCurrentUser();
        return user == null ? null : user.getUid();
    }

    /** Tên sẽ hiện dưới bài đăng (tên Google hoặc phần trước @ của email, không lộ cả email). */
    public String getAuthorName() {
        FirebaseUser user = auth.getCurrentUser();
        return user == null ? "" : CommunityMapper.authorNameFor(user.getDisplayName(), user.getEmail());
    }

    // ---------- Đọc ----------

    /** 50 bài mới nhất (kèm ảnh nhỏ nếu có), đã bỏ các bài người dùng từng báo cáo. */
    public void loadLatest(@NonNull Callback<List<CommunityRecipe>> callback) {
        String uid = getCurrentUid();
        if (uid == null) {
            callback.onError(new IllegalStateException("Chưa đăng nhập"));
            return;
        }
        Set<String> hidden = getHiddenPostIds(uid);
        Task<QuerySnapshot> task = community()
                .orderBy(CommunityMapper.F_POSTED_AT, Query.Direction.DESCENDING)
                .limit(PAGE_SIZE)
                // Bắt buộc hỏi máy chủ: mất mạng thì báo lỗi, không hiện danh sách cũ trong bộ nhớ đệm
                .get(Source.SERVER);
        withTimeout(task).addOnCompleteListener(result -> {
            if (!result.isSuccessful() || result.getResult() == null) {
                callback.onError(errorOf(result));
                return;
            }
            List<CommunityRecipe> posts = new ArrayList<>();
            for (DocumentSnapshot doc : result.getResult().getDocuments()) {
                if (hidden.contains(doc.getId())) {
                    continue;
                }
                CommunityRecipe post = CommunityMapper.fromDocument(doc.getId(), doc.getData());
                // Field "thumb" sai kiểu (không phải Blob) thì coi như bài không có ảnh
                Object thumb = doc.get(CommunityMapper.F_THUMB);
                if (thumb instanceof Blob) {
                    post.thumb = ((Blob) thumb).toBytes();
                }
                posts.add(post);
            }
            callback.onSuccess(posts);
        });
    }

    /**
     * Bước 8d: tải ảnh lớn của một bài (document photo/main). Bài không có ảnh → onSuccess(null).
     * Không ép hỏi máy chủ: ảnh đã xem rồi thì Firestore lấy được từ bộ nhớ đệm, kể cả khi mất mạng.
     */
    public void loadPhoto(@NonNull String postId, @NonNull Callback<byte[]> callback) {
        withTimeout(photoOf(postId).get()).addOnCompleteListener(result -> {
            if (!result.isSuccessful() || result.getResult() == null) {
                callback.onError(errorOf(result));
                return;
            }
            Object data = result.getResult().get(CommunityMapper.F_PHOTO_DATA);
            callback.onSuccess(data instanceof Blob ? ((Blob) data).toBytes() : null);
        });
    }

    // ---------- Ghi ----------

    /**
     * Đăng món lên cộng đồng. Mã bài = "{uid}_{cloudId}" nên đăng lại cùng món sẽ cập nhật bài cũ.
     * Gửi chữ (tên, loại, thời gian, khẩu phần, ghi chú, nguyên liệu, các bước) và ảnh món nếu có:
     *   1. Luồng nền: nén ảnh thành ảnh lớn 800 px (≤ 300 KB) + ảnh nhỏ 240 px (≤ 30 KB).
     *   2. Main thread: một WriteBatch ghi bài (kèm thumb) + photo/main. Món không có ảnh (hoặc vừa xoá ảnh)
     *      thì xoá photo/main cũ để bài không còn ảnh cũ.
     * Nén ảnh lỗi → vẫn đăng phần chữ.
     */
    public void publish(@NonNull Recipe recipe, @NonNull List<Ingredient> ingredients, @NonNull List<Step> steps,
                        @NonNull Callback<Void> callback) {
        String uid = getCurrentUid();
        if (uid == null || recipe.cloudId == null) {
            callback.onError(new IllegalStateException("Chưa đăng nhập"));
            return;
        }
        Map<String, Object> doc = CommunityMapper.toDocument(uid, getAuthorName(), recipe, ingredients, steps,
                System.currentTimeMillis());
        String postId = CommunityMapper.postIdFor(uid, recipe.cloudId);
        String photoPath = recipe.imagePath;

        // Bước 1: nén ảnh ở luồng nền, xong thì báo kết quả qua TaskCompletionSource
        TaskCompletionSource<EncodedPhotos> encoding = new TaskCompletionSource<>();
        PHOTO_EXECUTOR.execute(() -> {
            try {
                encoding.setResult(encodePhotos(photoPath));
            } catch (RuntimeException e) {
                encoding.setException(e);
            }
        });
        encoding.getTask()
                // Bước 2: continueWithTask không chỉ định Executor → chạy tiếp trên main thread
                .continueWithTask(encoded -> {
                    EncodedPhotos photos = null;
                    if (encoded.isSuccessful()) {
                        photos = encoded.getResult();
                    } else {
                        Log.w(TAG, "Không nén được ảnh, đăng không kèm ảnh", encoded.getException());
                    }
                    WriteBatch batch = firestore.batch();
                    if (photos != null) {
                        doc.put(CommunityMapper.F_THUMB, Blob.fromBytes(photos.thumb));
                        Map<String, Object> photoDoc = new HashMap<>();
                        photoDoc.put(CommunityMapper.F_PHOTO_DATA, Blob.fromBytes(photos.full));
                        batch.set(photoOf(postId), photoDoc);
                    } else {
                        batch.delete(photoOf(postId));
                    }
                    batch.set(community().document(postId), doc);
                    return withTimeout(batch.commit());
                })
                .addOnCompleteListener(result -> {
                    if (result.isSuccessful()) {
                        callback.onSuccess(null);
                    } else {
                        callback.onError(errorOf(result));
                    }
                });
    }

    /** Hai bản ảnh đã nén, sẵn sàng gửi lên. */
    private static final class EncodedPhotos {
        final byte[] full;
        final byte[] thumb;

        EncodedPhotos(byte[] full, byte[] thumb) {
            this.full = full;
            this.thumb = thumb;
        }
    }

    /** Chạy ở luồng nền. Món không có ảnh / nén không đạt giới hạn → null. Phải có đủ CẢ hai bản mới gửi. */
    @Nullable
    private EncodedPhotos encodePhotos(@Nullable String path) {
        byte[] full = ImageStorage.compressForUpload(application, path,
                CloudPhoto.PHOTO_MAX_SIZE, CloudPhoto.PHOTO_QUALITY, CloudPhoto.PHOTO_MAX_BYTES);
        if (full == null) {
            return null;
        }
        byte[] thumb = ImageStorage.compressForUpload(application, path,
                CloudPhoto.THUMB_MAX_SIZE, CloudPhoto.THUMB_QUALITY, CloudPhoto.THUMB_MAX_BYTES);
        return thumb == null ? null : new EncodedPhotos(full, thumb);
    }

    /**
     * Gỡ bài của chính mình (Rules chặn nếu không phải tác giả). Firestore KHÔNG tự xoá document con,
     * nên xoá luôn ảnh lớn photo/main trong cùng một WriteBatch.
     */
    public void delete(@NonNull CommunityRecipe post, @NonNull Callback<Void> callback) {
        WriteBatch batch = firestore.batch();
        batch.delete(photoOf(post.postId));
        batch.delete(community().document(post.postId));
        withTimeout(batch.commit()).addOnCompleteListener(result -> {
            if (result.isSuccessful()) {
                callback.onSuccess(null);
            } else {
                callback.onError(errorOf(result));
            }
        });
    }

    /**
     * Báo cáo bài của người khác: ghi reports/{uid của mình} (mỗi người một báo cáo cho mỗi bài).
     * Ẩn bài trên máy này ngay, kể cả khi mạng chậm (lệnh ghi sẽ tự gửi khi có mạng).
     */
    public void report(@NonNull CommunityRecipe post, @NonNull String reason, @NonNull Callback<Void> callback) {
        String uid = getCurrentUid();
        if (uid == null) {
            callback.onError(new IllegalStateException("Chưa đăng nhập"));
            return;
        }
        hidePost(uid, post.postId);
        Map<String, Object> data = new HashMap<>();
        data.put(F_REASON, reason);
        data.put(F_REPORTED_AT, System.currentTimeMillis());
        withTimeout(community().document(post.postId).collection(COLLECTION_REPORTS).document(uid).set(data))
                .addOnCompleteListener(result -> {
                    if (result.isSuccessful()) {
                        callback.onSuccess(null);
                    } else {
                        Log.w(TAG, "Không gửi được báo cáo " + post.postId, result.getException());
                        callback.onError(errorOf(result));
                    }
                });
    }

    // ---------- Bài đã ẩn (SharedPreferences) ----------

    public Set<String> getHiddenPostIds(@NonNull String uid) {
        // Trả bản sao: không được sửa trực tiếp Set do SharedPreferences trả về
        return new HashSet<>(prefs.getStringSet(KEY_HIDDEN + uid, new HashSet<>()));
    }

    private void hidePost(String uid, String postId) {
        Set<String> hidden = getHiddenPostIds(uid);
        hidden.add(postId);
        prefs.edit().putStringSet(KEY_HIDDEN + uid, hidden).apply();
    }

    // ---------- Tiện ích ----------

    private CollectionReference community() {
        return firestore.collection(COLLECTION_COMMUNITY);
    }

    /** community_recipes/{postId}/photo/main – nơi để ảnh lớn của bài. */
    private DocumentReference photoOf(String postId) {
        return community().document(postId).collection(COLLECTION_PHOTO).document(DOC_PHOTO_MAIN);
    }

    /** Hết TIMEOUT_SECONDS mà máy chủ chưa trả lời → Task lỗi với TimeoutException. */
    private static <T> Task<T> withTimeout(Task<T> task) {
        return Tasks.withTimeout(task, TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    private static Exception errorOf(Task<?> task) {
        Exception error = task.getException();
        return error != null ? error : new IllegalStateException("Không có kết quả");
    }
}
