package com.sobep.ui.discover;

import android.app.Application;
import android.graphics.Bitmap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import com.sobep.R;
import com.sobep.data.community.CommunityRecipe;
import com.sobep.data.local.entity.Ingredient;
import com.sobep.data.local.model.SavedSample;
import com.sobep.data.repository.CommunityRepository;
import com.sobep.data.repository.RecipeRepository;
import com.sobep.data.sample.SampleRecipe;
import com.sobep.data.sample.SampleRecipeLoader;
import com.sobep.data.local.entity.Recipe;
import com.sobep.util.CloudPhoto;
import com.sobep.util.FirestoreErrors;
import com.sobep.util.ImageStorage;
import com.sobep.util.VietnameseText;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeoutException;

/**
 * ViewModel màn Khám phá.
 * - Tab Món mẫu (CN06): đọc món mẫu từ assets, lọc theo từ khoá / ô gợi ý.
 * - Tab Cộng đồng (CN15, chỉ khi đăng nhập): tải bài từ Firestore, lọc theo từ khoá, gỡ bài của mình, báo cáo bài khác.
 * Cả 2 tab dùng chung: biết món nào đã lưu vào sổ (qua cột recipes.sourceSampleId) và lưu món thành món thật.
 */
public class DiscoverViewModel extends AndroidViewModel {

    public static final int TAB_SAMPLES = 0;
    public static final int TAB_COMMUNITY = 1;

    /** Báo cho Activity khi vừa lưu xong một món (sự kiện một lần). */
    public static final class SavedEvent {
        public final String sampleName;

        SavedEvent(String sampleName) {
            this.sampleName = sampleName;
        }
    }

    /** Bước 8d: ảnh lớn đã tải + giải mã cho bảng xem trước; photoKey để Activity biết là ảnh của bài nào. */
    public static final class PreviewPhoto {
        public final String photoKey;
        public final Bitmap bitmap;

        PreviewPhoto(String photoKey, Bitmap bitmap) {
            this.photoKey = photoKey;
            this.bitmap = bitmap;
        }
    }

    /** Nhận ảnh lớn (byte JPEG) của một bài; null = bài không có ảnh hoặc tải lỗi. */
    private interface PhotoBytesListener {
        void onPhoto(@Nullable byte[] bytes);
    }

    private final RecipeRepository repository;
    private final CommunityRepository communityRepository;
    // Luồng nền riêng để đọc file JSON (không đọc trên main thread)
    private final ExecutorService fileExecutor = Executors.newSingleThreadExecutor();

    private final MutableLiveData<List<SampleRecipe>> samples = new MutableLiveData<>();
    private final MutableLiveData<Boolean> loadError = new MutableLiveData<>(false);
    private final MutableLiveData<String> keyword = new MutableLiveData<>("");
    // Key của ô gợi ý đang chọn; null = không chọn ô nào
    private final MutableLiveData<String> selectedTile = new MutableLiveData<>(null);

    // ---- Tab Cộng đồng ----
    private final MutableLiveData<Integer> tab = new MutableLiveData<>(TAB_SAMPLES);
    // Có đăng nhập không (Activity hỏi lại mỗi lần quay về màn này); chưa đăng nhập → ẩn tab Cộng đồng
    private final MutableLiveData<Boolean> signedIn = new MutableLiveData<>(false);
    // null = chưa tải lần nào
    private final MutableLiveData<List<SampleRecipe>> communityPosts = new MutableLiveData<>(null);
    private final MutableLiveData<Boolean> communityLoading = new MutableLiveData<>(false);
    // uid đã tải danh sách Cộng đồng (đổi tài khoản → tải lại)
    private String loadedForUid;
    // Câu báo một lần (mã chuỗi): lỗi mạng, đã báo cáo, đã gỡ bài…
    private final MutableLiveData<Integer> message = new MutableLiveData<>(null);
    // Bước 8d: ảnh lớn của bài đang xem trước
    private final MutableLiveData<PreviewPhoto> previewPhoto = new MutableLiveData<>(null);
    // Ảnh lớn vừa tải gần nhất (byte JPEG): xem trước rồi bấm "Lưu vào sổ" thì không phải tải lại
    private String lastPhotoKey;
    private byte[] lastPhotoBytes;

    // Món sau khi lọc — kết hợp các nguồn: tab, danh sách mẫu / bài cộng đồng, từ khoá, ô đang chọn
    private final MediatorLiveData<List<SampleRecipe>> results = new MediatorLiveData<>();
    // Số món của từng ô gợi ý (key → số món)
    private final LiveData<Map<String, Integer>> tileCounts;
    // Món đã lưu: mã món mẫu / "c-" + mã bài → mã món trong sổ
    private final LiveData<Map<String, Long>> savedSamples;

    private final MutableLiveData<SavedEvent> savedEvent = new MutableLiveData<>();
    // Món đang lưu dở (chưa thấy trong CSDL) → chặn bấm lưu 2 lần thành 2 món trùng
    private final Set<String> savingIds = new HashSet<>();

    public DiscoverViewModel(@NonNull Application application) {
        super(application);
        repository = new RecipeRepository(application);
        communityRepository = new CommunityRepository(application);

        results.addSource(samples, list -> filter());
        results.addSource(communityPosts, list -> filter());
        results.addSource(tab, t -> filter());
        results.addSource(keyword, k -> filter());
        results.addSource(selectedTile, key -> filter());

        tileCounts = Transformations.map(samples, DiscoverViewModel::countTiles);
        savedSamples = Transformations.map(repository.getSavedSamples(), list -> {
            Map<String, Long> map = new HashMap<>();
            for (SavedSample saved : list) {
                map.put(saved.sourceSampleId, saved.id);
                // Đã thấy trong CSDL → không còn "đang lưu"
                savingIds.remove(saved.sourceSampleId);
            }
            return map;
        });

        loadSamples();
    }

    private void loadSamples() {
        fileExecutor.execute(() -> {
            try {
                samples.postValue(SampleRecipeLoader.load(getApplication()));
            } catch (Exception e) {
                // File hỏng hoặc thiếu → báo lỗi, danh sách trống, app không crash
                loadError.postValue(true);
                samples.postValue(new ArrayList<>());
            }
        });
    }

    public LiveData<List<SampleRecipe>> getResults() {
        return results;
    }

    public LiveData<Map<String, Integer>> getTileCounts() {
        return tileCounts;
    }

    public LiveData<Map<String, Long>> getSavedSamples() {
        return savedSamples;
    }

    public LiveData<String> getSelectedTile() {
        return selectedTile;
    }

    public LiveData<String> getKeyword() {
        return keyword;
    }

    public LiveData<Boolean> getLoadError() {
        return loadError;
    }

    public LiveData<SavedEvent> getSavedEvent() {
        return savedEvent;
    }

    public void consumeSavedEvent() {
        savedEvent.setValue(null);
    }

    public LiveData<Integer> getTab() {
        return tab;
    }

    public LiveData<Boolean> isSignedIn() {
        return signedIn;
    }

    public LiveData<Boolean> isCommunityLoading() {
        return communityLoading;
    }

    public LiveData<Integer> getMessage() {
        return message;
    }

    public void consumeMessage() {
        message.setValue(null);
    }

    /** uid người đang đăng nhập, để Adapter / bảng xem trước biết bài nào là của mình. */
    @Nullable
    public String getCurrentUid() {
        return communityRepository.getCurrentUid();
    }

    // ---------- Chuyển tab / đăng nhập ----------

    /**
     * Activity gọi trong onResume: người dùng có thể vừa đăng nhập / đăng xuất ở màn Tài khoản.
     * Đăng xuất → ẩn tab Cộng đồng, quay về Món mẫu, xoá danh sách bài đã tải.
     */
    public void refreshSignIn() {
        String uid = communityRepository.getCurrentUid();
        signedIn.setValue(uid != null);
        if (uid == null) {
            loadedForUid = null;
            communityPosts.setValue(null);
            if (Integer.valueOf(TAB_COMMUNITY).equals(tab.getValue())) {
                tab.setValue(TAB_SAMPLES);
            }
        } else if (Integer.valueOf(TAB_COMMUNITY).equals(tab.getValue()) && !uid.equals(loadedForUid)) {
            // Đang ở tab Cộng đồng mà đổi sang tài khoản khác → tải lại
            loadCommunity();
        }
    }

    public void selectTab(int newTab) {
        if (newTab == TAB_COMMUNITY && !Boolean.TRUE.equals(signedIn.getValue())) {
            return;
        }
        if (Integer.valueOf(newTab).equals(tab.getValue())) {
            return;
        }
        tab.setValue(newTab);
        // Lần đầu mở tab Cộng đồng (hoặc vừa đổi tài khoản) → tải bài
        String uid = communityRepository.getCurrentUid();
        if (newTab == TAB_COMMUNITY && (communityPosts.getValue() == null || uid == null || !uid.equals(loadedForUid))) {
            loadCommunity();
        }
    }

    // ---------- Tab Cộng đồng ----------

    /** Tải 50 bài mới nhất. Đang tải thì bỏ qua lần bấm tiếp. */
    public void loadCommunity() {
        if (Boolean.TRUE.equals(communityLoading.getValue())) {
            return;
        }
        communityLoading.setValue(true);
        String uid = communityRepository.getCurrentUid();
        communityRepository.loadLatest(new CommunityRepository.Callback<List<CommunityRecipe>>() {
            @Override
            public void onSuccess(List<CommunityRecipe> result) {
                loadedForUid = uid;
                communityPosts.setValue(new ArrayList<>(result));
                communityLoading.setValue(false);
            }

            @Override
            public void onError(@NonNull Exception error) {
                if (communityPosts.getValue() == null) {
                    communityPosts.setValue(new ArrayList<>());
                }
                communityLoading.setValue(false);
                message.setValue(FirestoreErrors.messageFor(error, R.string.error_community_unknown));
            }
        });
    }

    /** Báo cáo bài của người khác; bài bị ẩn trên máy này ngay. */
    public void reportPost(CommunityRecipe post, String reason) {
        removeFromList(post);
        communityRepository.report(post, reason, new CommunityRepository.Callback<Void>() {
            @Override
            public void onSuccess(Void result) {
                message.setValue(R.string.msg_community_reported);
            }

            @Override
            public void onError(@NonNull Exception error) {
                // Mạng chậm: Firestore vẫn giữ lệnh ghi và tự gửi khi có mạng
                message.setValue(error instanceof TimeoutException
                        ? R.string.msg_community_reported
                        : FirestoreErrors.messageFor(error, R.string.error_community_unknown));
            }
        });
    }

    /** Gỡ bài của chính mình (Activity hỏi xác nhận trước). */
    public void deletePost(CommunityRecipe post) {
        communityRepository.delete(post, new CommunityRepository.Callback<Void>() {
            @Override
            public void onSuccess(Void result) {
                removeFromList(post);
                message.setValue(R.string.msg_community_deleted);
            }

            @Override
            public void onError(@NonNull Exception error) {
                message.setValue(errorMessageForWrite(error));
            }
        });
    }

    private void removeFromList(CommunityRecipe post) {
        List<SampleRecipe> current = communityPosts.getValue();
        if (current == null) {
            return;
        }
        List<SampleRecipe> updated = new ArrayList<>();
        for (SampleRecipe item : current) {
            if (!(item instanceof CommunityRecipe) || !((CommunityRecipe) item).postId.equals(post.postId)) {
                updated.add(item);
            }
        }
        communityPosts.setValue(updated);
    }

    // ---------- Ảnh bài cộng đồng (bước 8d) ----------

    public LiveData<PreviewPhoto> getPreviewPhoto() {
        return previewPhoto;
    }

    /**
     * Mở bảng xem trước bài có ảnh → tải ảnh lớn (photo/main), giải mã ở luồng nền rồi báo Activity.
     * Trong lúc chờ, Activity hiện tạm ảnh nhỏ. Tải lỗi thì giữ ảnh nhỏ, không báo lỗi.
     */
    public void loadPreviewPhoto(CommunityRecipe post) {
        if (!post.hasPhoto()) {
            return;
        }
        PreviewPhoto current = previewPhoto.getValue();
        if (current != null && current.photoKey.equals(post.photoKey())) {
            return;
        }
        fetchPhoto(post, bytes -> {
            if (bytes == null) {
                return;
            }
            runInBackground(() -> {
                Bitmap bitmap = ImageStorage.decodeBytes(bytes, CloudPhoto.PHOTO_MAX_SIZE);
                if (bitmap != null) {
                    previewPhoto.postValue(new PreviewPhoto(post.photoKey(), bitmap));
                }
            });
        });
    }

    /** Lấy byte ảnh lớn: dùng lại ảnh vừa tải nếu đúng bài đó, không thì hỏi Firestore. Gọi listener trên main thread. */
    private void fetchPhoto(CommunityRecipe post, PhotoBytesListener listener) {
        String key = post.photoKey();
        if (key.equals(lastPhotoKey) && lastPhotoBytes != null) {
            listener.onPhoto(lastPhotoBytes);
            return;
        }
        communityRepository.loadPhoto(post.postId, new CommunityRepository.Callback<byte[]>() {
            @Override
            public void onSuccess(@Nullable byte[] result) {
                if (result != null) {
                    lastPhotoKey = key;
                    lastPhotoBytes = result;
                }
                listener.onPhoto(result);
            }

            @Override
            public void onError(@NonNull Exception error) {
                listener.onPhoto(null);
            }
        });
    }

    /** Chạy việc nặng (giải mã ảnh, ghi file) ở luồng nền; ViewModel đã huỷ thì bỏ qua. */
    private void runInBackground(Runnable task) {
        try {
            fileExecutor.execute(task);
        } catch (RejectedExecutionException e) {
            // onCleared đã tắt luồng nền (người dùng rời màn Khám phá) → không cần làm nữa
        }
    }

    /** Lệnh ghi hết giờ chờ = mạng chậm / mất mạng; Firestore sẽ tự gửi khi có mạng lại. */
    @StringRes
    private static int errorMessageForWrite(Exception error) {
        if (error instanceof TimeoutException) {
            return R.string.msg_community_queued;
        }
        return FirestoreErrors.messageFor(error, R.string.error_community_unknown);
    }

    // ---------- Lọc ----------

    public void setKeyword(String text) {
        String value = text == null ? "" : text.trim();
        if (!value.equals(keyword.getValue())) {
            keyword.setValue(value);
        }
    }

    /** Bấm ô gợi ý: chọn ô đó; bấm lại ô đang chọn thì bỏ chọn (hiện tất cả). */
    public void toggleTile(String key) {
        selectedTile.setValue(key.equals(selectedTile.getValue()) ? null : key);
    }

    /** Tab Cộng đồng không có ô gợi ý (bài đăng không có nhãn) → chỉ lọc theo từ khoá. */
    private void filter() {
        boolean community = Integer.valueOf(TAB_COMMUNITY).equals(tab.getValue());
        List<SampleRecipe> all = community ? communityPosts.getValue() : samples.getValue();
        if (all == null) {
            // Chưa đọc xong; tab Cộng đồng chưa tải thì hiện danh sách trống
            if (community) {
                results.setValue(new ArrayList<>());
            }
            return;
        }
        String query = VietnameseText.normalize(keyword.getValue());
        KeywordTile tile = community || selectedTile.getValue() == null
                ? null : KeywordTile.find(selectedTile.getValue());

        List<SampleRecipe> filtered = new ArrayList<>();
        for (SampleRecipe recipe : all) {
            boolean matchTile = tile == null || tile.matches(recipe);
            boolean matchKeyword = query.isEmpty() || matchesKeyword(recipe, query);
            if (matchTile && matchKeyword) {
                filtered.add(recipe);
            }
        }
        results.setValue(filtered);
    }

    /** Khớp tên món hoặc tên một nguyên liệu, không phân biệt hoa thường và dấu. */
    private static boolean matchesKeyword(SampleRecipe recipe, String query) {
        if (VietnameseText.normalize(recipe.name).contains(query)) {
            return true;
        }
        for (Ingredient ingredient : recipe.ingredients) {
            if (VietnameseText.normalize(ingredient.name).contains(query)) {
                return true;
            }
        }
        return false;
    }

    private static Map<String, Integer> countTiles(List<SampleRecipe> list) {
        Map<String, Integer> counts = new HashMap<>();
        for (KeywordTile tile : KeywordTile.ALL) {
            int count = 0;
            for (SampleRecipe recipe : list) {
                if (tile.matches(recipe)) {
                    count++;
                }
            }
            counts.put(tile.key, count);
        }
        return counts;
    }

    // ---------- Lưu vào sổ ----------

    public boolean isSaved(SampleRecipe sample) {
        Map<String, Long> saved = savedSamples.getValue();
        return saved != null && saved.containsKey(sample.id);
    }

    /** Mã món trong sổ của món đã lưu; null nếu chưa lưu. */
    @Nullable
    public Long getSavedRecipeId(SampleRecipe sample) {
        Map<String, Long> saved = savedSamples.getValue();
        return saved == null ? null : saved.get(sample.id);
    }

    /**
     * CN06 / CN15: chép món mẫu hoặc bài cộng đồng vào bảng recipes (kèm nguyên liệu, các bước),
     * ghi sourceSampleId (món mẫu: "m01"; bài cộng đồng: "c-" + mã bài).
     * Bước 8d: bài có ảnh → tải ảnh lớn, ghi thành file trong recipe_photos rồi gắn vào món (recipes.imagePath).
     * Tải ảnh lỗi (mất mạng…) → vẫn lưu món, chỉ không có ảnh.
     */
    public void saveToBook(SampleRecipe sample) {
        if (isSaved(sample) || savingIds.contains(sample.id)) {
            return;
        }
        savingIds.add(sample.id);
        Recipe recipe = sample.toRecipe();
        if (sample instanceof CommunityRecipe && ((CommunityRecipe) sample).hasPhoto()) {
            fetchPhoto((CommunityRecipe) sample, bytes -> runInBackground(() -> {
                recipe.imagePath = ImageStorage.saveRecipePhotoBytes(getApplication(), bytes);
                insertRecipe(sample, recipe);
            }));
        } else {
            insertRecipe(sample, recipe);
        }
    }

    private void insertRecipe(SampleRecipe sample, Recipe recipe) {
        repository.saveRecipe(recipe, sample.copyIngredients(), sample.copySteps(),
                // Gọi ở luồng nền → postValue
                recipeId -> savedEvent.postValue(new SavedEvent(sample.name)));
    }

    @Override
    protected void onCleared() {
        fileExecutor.shutdown();
        super.onCleared();
    }
}
