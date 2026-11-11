package com.sobep.ui.detail;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import com.sobep.data.local.entity.Ingredient;
import com.sobep.data.local.entity.Recipe;
import com.sobep.R;
import com.sobep.data.local.entity.Step;
import com.sobep.data.repository.CommunityRepository;
import com.sobep.data.repository.RecipeRepository;
import com.sobep.util.FirestoreErrors;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;

/**
 * ViewModel màn Chi tiết món (CN02): đọc món theo mã, giữ số người đang chọn
 * và tính lại số lượng nguyên liệu theo số người (quy đổi khẩu phần).
 * CN15: đăng món lên Cộng đồng (chỉ khi đã đăng nhập). Trạng thái "đang đăng" giữ trong ViewModel
 * → xoay màn hình khi đang đăng vẫn khoá nút, không gửi 2 lần.
 */
public class RecipeDetailViewModel extends AndroidViewModel {

    public static final int MIN_SERVINGS = 1;
    public static final int MAX_SERVINGS = 20;

    private final RecipeRepository repository;
    private final CommunityRepository communityRepository;

    private final MutableLiveData<Boolean> publishing = new MutableLiveData<>(false);
    // Câu báo một lần (mã chuỗi) sau khi đăng: thành công / lỗi
    private final MutableLiveData<Integer> publishMessage = new MutableLiveData<>(null);

    // Mã món do Activity truyền vào (lấy từ Intent). Đổi mã → các LiveData bên dưới tự đọc lại
    private final MutableLiveData<Long> recipeId = new MutableLiveData<>();

    private final LiveData<Recipe> recipe;
    private final LiveData<List<Ingredient>> baseIngredients; // số lượng gốc trong CSDL
    private final LiveData<List<Step>> steps;

    // Số người đang chọn; null = chưa có, sẽ lấy theo số người gốc của món khi món vừa đọc xong
    private final MutableLiveData<Integer> servings = new MutableLiveData<>();

    // Nguyên liệu đã nhân theo số người — kết hợp 3 nguồn: món, nguyên liệu gốc, số người
    private final MediatorLiveData<List<Ingredient>> scaledIngredients = new MediatorLiveData<>();

    public RecipeDetailViewModel(@NonNull Application application) {
        super(application);
        repository = new RecipeRepository(application);
        communityRepository = new CommunityRepository(application);

        recipe = Transformations.switchMap(recipeId, repository::getRecipe);
        baseIngredients = Transformations.switchMap(recipeId, repository::getIngredients);
        steps = Transformations.switchMap(recipeId, repository::getSteps);

        scaledIngredients.addSource(recipe, r -> {
            // Lần đầu đọc được món: đặt số người = số người gốc của công thức
            if (r != null && servings.getValue() == null) {
                servings.setValue(clampServings(r.servings));
            }
            recalculateIngredients();
        });
        scaledIngredients.addSource(baseIngredients, list -> recalculateIngredients());
        scaledIngredients.addSource(servings, s -> recalculateIngredients());
    }

    /** Gọi một lần trong onCreate của Activity. Gọi lại với cùng mã (khi xoay màn hình) thì bỏ qua. */
    public void setRecipeId(long id) {
        Long current = recipeId.getValue();
        if (current == null || current != id) {
            recipeId.setValue(id);
        }
    }

    public LiveData<Recipe> getRecipe() {
        return recipe;
    }

    /** Nguyên liệu đã quy đổi theo số người đang chọn. */
    public LiveData<List<Ingredient>> getIngredients() {
        return scaledIngredients;
    }

    public LiveData<List<Step>> getSteps() {
        return steps;
    }

    public LiveData<Integer> getServings() {
        return servings;
    }

    // ---------- Quy đổi khẩu phần ----------

    public void increaseServings() {
        Integer s = servings.getValue();
        if (s != null && s < MAX_SERVINGS) {
            servings.setValue(s + 1);
        }
    }

    public void decreaseServings() {
        Integer s = servings.getValue();
        if (s != null && s > MIN_SERVINGS) {
            servings.setValue(s - 1);
        }
    }

    /**
     * Số lượng mới = số lượng gốc × (số người chọn / số người gốc).
     * Vd món cho 2 người cần 300 g thịt → chọn 3 người: 300 × 3/2 = 450 g.
     * Tạo bản sao nguyên liệu để KHÔNG sửa vào đối tượng gốc Room trả về.
     */
    private void recalculateIngredients() {
        Recipe r = recipe.getValue();
        List<Ingredient> base = baseIngredients.getValue();
        Integer s = servings.getValue();
        if (r == null || base == null || s == null) {
            return;
        }
        double factor = r.servings > 0 ? (double) s / r.servings : 1.0;

        List<Ingredient> result = new ArrayList<>(base.size());
        for (Ingredient original : base) {
            Ingredient copy = new Ingredient();
            copy.id = original.id;
            copy.recipeId = original.recipeId;
            copy.name = original.name;
            copy.unit = original.unit;
            copy.position = original.position;
            // 0 = "vừa ăn", không ghi số lượng → giữ nguyên 0
            copy.quantity = original.quantity * factor;
            result.add(copy);
        }
        scaledIngredients.setValue(result);
    }

    private int clampServings(int value) {
        return Math.max(MIN_SERVINGS, Math.min(MAX_SERVINGS, value));
    }

    // ---------- Thao tác ----------

    /** CN09: bật/tắt yêu thích món đang xem. */
    public void toggleFavorite() {
        Recipe r = recipe.getValue();
        if (r != null) {
            repository.setFavorite(r.id, !r.favorite);
        }
    }

    /** CN05: xoá món đang xem (Activity hỏi xác nhận trước, xoá xong thì finish()). */
    public void deleteRecipe() {
        Recipe r = recipe.getValue();
        if (r != null) {
            repository.deleteRecipe(r);
        }
    }

    // ---------- Đăng lên cộng đồng (CN15) ----------

    /** Đã đăng nhập chưa: chưa đăng nhập thì Activity ẩn nút "Đăng lên cộng đồng". */
    public boolean isSignedIn() {
        return communityRepository.getCurrentUid() != null;
    }

    /** Tên sẽ hiện dưới bài đăng, để hộp xác nhận cho người dùng xem trước. */
    public String getAuthorName() {
        return communityRepository.getAuthorName();
    }

    public LiveData<Boolean> isPublishing() {
        return publishing;
    }

    public LiveData<Integer> getPublishMessage() {
        return publishMessage;
    }

    public void consumePublishMessage() {
        publishMessage.setValue(null);
    }

    /**
     * Gửi món đang xem lên community_recipes. Dùng nguyên liệu GỐC trong CSDL (không phải số đã nhân theo
     * số người đang chọn) để bài đăng đúng công thức.
     */
    public void publishToCommunity() {
        Recipe r = recipe.getValue();
        List<Ingredient> ingredients = baseIngredients.getValue();
        List<Step> stepList = steps.getValue();
        if (r == null || ingredients == null || stepList == null || Boolean.TRUE.equals(publishing.getValue())) {
            return;
        }
        publishing.setValue(true);
        communityRepository.publish(r, ingredients, stepList, new CommunityRepository.Callback<Void>() {
            // Firebase gọi trên main thread → setValue được
            @Override
            public void onSuccess(@Nullable Void result) {
                publishing.setValue(false);
                publishMessage.setValue(R.string.msg_community_published);
            }

            @Override
            public void onError(@NonNull Exception error) {
                publishing.setValue(false);
                // Hết giờ chờ = mạng chậm: Firestore giữ lệnh ghi trong máy và tự gửi khi có mạng
                publishMessage.setValue(error instanceof TimeoutException
                        ? R.string.msg_community_queued
                        : FirestoreErrors.messageFor(error, R.string.error_community_unknown));
            }
        });
    }
}
