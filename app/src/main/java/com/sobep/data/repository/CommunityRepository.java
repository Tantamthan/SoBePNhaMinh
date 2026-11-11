package com.sobep.data.repository;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.Source;
import com.sobep.data.community.CommunityRecipe;
import com.sobep.data.local.entity.Ingredient;
import com.sobep.data.local.entity.Recipe;
import com.sobep.data.local.entity.Step;
import com.sobep.util.CommunityMapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * CN15: tab Cộng đồng – đăng món (chỉ chữ), tải bài mới nhất, gỡ bài của mình, báo cáo bài của người khác.
 *
 * Dữ liệu trên Firestore:
 *   community_recipes/{uid}_{cloudId}                 một bài (xem CommunityMapper)
 *   community_recipes/{postId}/reports/{uid người báo} một báo cáo; chỉ xem được trên Firebase Console
 * Quyền đọc / ghi do Security Rules (file firestore.rules) kiểm tra ở MÁY CHỦ.
 *
 * Không đụng Room: lưu bài vào sổ đi qua RecipeRepository giống món mẫu.
 * Firebase gọi callback trên main thread → ViewModel setValue được ngay.
 */
public class CommunityRepository {

    private static final String TAG = "CommunityRepository";

    private static final String COLLECTION_COMMUNITY = "community_recipes";
    private static final String COLLECTION_REPORTS = "reports";
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

    /** Kết quả trả về ViewModel (gọi trên main thread). */
    public interface Callback<T> {
        void onSuccess(T result);

        void onError(@NonNull Exception error);
    }

    private final FirebaseAuth auth;
    private final FirebaseFirestore firestore;
    private final SharedPreferences prefs;

    public CommunityRepository(Application application) {
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

    /** 50 bài mới nhất, đã bỏ các bài người dùng từng báo cáo. */
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
                if (!hidden.contains(doc.getId())) {
                    posts.add(CommunityMapper.fromDocument(doc.getId(), doc.getData()));
                }
            }
            callback.onSuccess(posts);
        });
    }

    // ---------- Ghi ----------

    /**
     * Đăng món lên cộng đồng. Mã bài = "{uid}_{cloudId}" nên đăng lại cùng món sẽ cập nhật bài cũ.
     * Chỉ gửi chữ: tên, loại, thời gian, khẩu phần, ghi chú, nguyên liệu, các bước.
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
        withTimeout(community().document(postId).set(doc)).addOnCompleteListener(result -> {
            if (result.isSuccessful()) {
                callback.onSuccess(null);
            } else {
                callback.onError(errorOf(result));
            }
        });
    }

    /** Gỡ bài của chính mình (Rules chặn nếu không phải tác giả). */
    public void delete(@NonNull CommunityRecipe post, @NonNull Callback<Void> callback) {
        withTimeout(community().document(post.postId).delete()).addOnCompleteListener(result -> {
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

    /** Hết TIMEOUT_SECONDS mà máy chủ chưa trả lời → Task lỗi với TimeoutException. */
    private static <T> Task<T> withTimeout(Task<T> task) {
        return Tasks.withTimeout(task, TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    private static Exception errorOf(Task<?> task) {
        Exception error = task.getException();
        return error != null ? error : new IllegalStateException("Không có kết quả");
    }
}
