package com.sobep.ui.main;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import com.sobep.data.local.entity.Recipe;
import com.sobep.data.local.model.RecipeIngredientNames;
import com.sobep.data.repository.RecipeRepository;
import com.sobep.util.VietnameseText;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ViewModel của màn chính (Sổ công thức): giữ danh sách món và bộ lọc hiện tại.
 * Xoay màn hình thì Activity bị tạo lại nhưng ViewModel vẫn còn → không mất từ khoá đang tìm.
 *
 * Cách hoạt động (2 tầng lọc):
 * 1. Bộ lọc là một LiveData. Mỗi khi bộ lọc đổi, switchMap chạy lại câu truy vấn filter(...) trong Room
 *    → lọc theo chip (danh mục, yêu thích, thời gian nấu).
 * 2. MediatorLiveData ghép danh sách đó với tên nguyên liệu của từng món, rồi lọc theo TỪ KHOÁ bằng Java,
 *    KHÔNG phân biệt dấu (VietnameseText): gõ "trung" vẫn ra "Thịt kho trứng".
 *    Không lọc từ khoá bằng SQL vì LIKE của SQLite coi "u" và "ứ" là 2 chữ khác nhau.
 * Activity nhận danh sách cuối cùng qua getRecipes().
 */
public class MainViewModel extends AndroidViewModel {

    // Chip "Dưới 30 phút" (CN08: lọc theo thời gian nấu)
    public static final int QUICK_MAX_MINUTES = 30;

    /** Bộ lọc danh sách món. Không sửa trực tiếp: mỗi lần đổi tạo một bản mới (bất biến). */
    public static final class RecipeFilter {
        public final String keyword;      // "" = không tìm
        public final String category;     // "" = mọi danh mục
        public final boolean favoriteOnly;
        public final int maxMinutes;      // 0 = không giới hạn thời gian nấu

        public RecipeFilter(String keyword, String category, boolean favoriteOnly, int maxMinutes) {
            this.keyword = keyword;
            this.category = category;
            this.favoriteOnly = favoriteOnly;
            this.maxMinutes = maxMinutes;
        }

        /** Bộ lọc rỗng = hiện tất cả món. */
        public static RecipeFilter none() {
            return new RecipeFilter("", "", false, 0);
        }
    }

    private final RecipeRepository repository;
    private final MutableLiveData<RecipeFilter> filter = new MutableLiveData<>(RecipeFilter.none());
    // Tầng 1: món đã lọc theo chip (từ Room)
    private final LiveData<List<Recipe>> chipFilteredRecipes;
    // Tầng 2: món đã lọc thêm theo từ khoá → Activity hiển thị
    private final MediatorLiveData<List<Recipe>> recipes = new MediatorLiveData<>();
    // Mã món → tên các nguyên liệu đã bỏ dấu, vd 3 → "trung ga thit ba chi". Tính lại khi nguyên liệu đổi
    private Map<Long, String> normalizedIngredients = new HashMap<>();

    public MainViewModel(@NonNull Application application) {
        super(application);
        repository = new RecipeRepository(application);
        // Bộ lọc đổi → chạy lại truy vấn với bộ lọc mới
        chipFilteredRecipes = Transformations.switchMap(filter, f ->
                repository.filterRecipes(f.category, f.favoriteOnly, f.maxMinutes));

        // Danh sách món đổi (hoặc từ khoá đổi, vì switchMap chạy lại) → lọc lại theo từ khoá
        recipes.addSource(chipFilteredRecipes, list -> applyKeyword());
        // Nguyên liệu đổi (thêm / sửa món) → bỏ dấu sẵn tên nguyên liệu một lần, rồi lọc lại
        recipes.addSource(repository.getIngredientNames(), rows -> {
            Map<Long, String> map = new HashMap<>();
            if (rows != null) {
                for (RecipeIngredientNames row : rows) {
                    map.put(row.recipeId, VietnameseText.normalize(row.names));
                }
            }
            normalizedIngredients = map;
            applyKeyword();
        });
    }

    /**
     * CN07: giữ lại món có TÊN MÓN hoặc TÊN NGUYÊN LIỆU chứa từ khoá, so sánh sau khi bỏ dấu + chữ thường.
     * Sổ cá nhân chỉ vài chục món nên lọc trong bộ nhớ trên main thread vẫn rất nhanh.
     */
    private void applyKeyword() {
        List<Recipe> list = chipFilteredRecipes.getValue();
        if (list == null) {
            return;
        }
        String keyword = VietnameseText.normalize(currentFilter().keyword);
        if (keyword.isEmpty()) {
            recipes.setValue(list);
            return;
        }
        List<Recipe> result = new ArrayList<>();
        for (Recipe recipe : list) {
            String ingredients = normalizedIngredients.get(recipe.id);
            if (VietnameseText.containsNormalized(recipe.name, keyword)
                    || (ingredients != null && ingredients.contains(keyword))) {
                result.add(recipe);
            }
        }
        recipes.setValue(result);
    }

    /** Danh sách món đã áp bộ lọc (CN01, CN07–CN09). */
    public LiveData<List<Recipe>> getRecipes() {
        return recipes;
    }

    public LiveData<RecipeFilter> getFilter() {
        return filter;
    }

    // ---------- Đổi bộ lọc ----------

    /** CN07: tìm theo tên món hoặc tên nguyên liệu, không phân biệt dấu. */
    public void setKeyword(String keyword) {
        RecipeFilter f = currentFilter();
        filter.setValue(new RecipeFilter(keyword == null ? "" : keyword.trim(),
                f.category, f.favoriteOnly, f.maxMinutes));
    }

    /** CN08: lọc theo danh mục (Recipe.CATEGORY_*), "" = tất cả. */
    public void setCategory(String category) {
        RecipeFilter f = currentFilter();
        filter.setValue(new RecipeFilter(f.keyword, category == null ? "" : category,
                f.favoriteOnly, f.maxMinutes));
    }

    /** CN08: lọc theo thời gian nấu tối đa (phút), 0 = không giới hạn. */
    public void setMaxMinutes(int maxMinutes) {
        RecipeFilter f = currentFilter();
        filter.setValue(new RecipeFilter(f.keyword, f.category, f.favoriteOnly, maxMinutes));
    }

    /** CN09: chỉ hiện món yêu thích. */
    public void setFavoriteOnly(boolean favoriteOnly) {
        RecipeFilter f = currentFilter();
        filter.setValue(new RecipeFilter(f.keyword, f.category, favoriteOnly, f.maxMinutes));
    }

    /**
     * Bấm một chip lọc: đổi danh mục + yêu thích + thời gian cùng lúc (chỉ chạy truy vấn 1 lần),
     * giữ nguyên từ khoá đang tìm.
     */
    public void setChipFilter(String category, boolean favoriteOnly, int maxMinutes) {
        RecipeFilter f = currentFilter();
        filter.setValue(new RecipeFilter(f.keyword, category == null ? "" : category,
                favoriteOnly, maxMinutes));
    }

    public void clearFilter() {
        filter.setValue(RecipeFilter.none());
    }

    // ---------- Thao tác trên một món ----------

    /** CN09: bấm tim trên thẻ món. */
    public void toggleFavorite(Recipe recipe) {
        repository.setFavorite(recipe.id, !recipe.favorite);
    }

    /** CN05: xoá món (Activity phải hỏi xác nhận trước khi gọi). */
    public void deleteRecipe(Recipe recipe) {
        repository.deleteRecipe(recipe);
    }

    private RecipeFilter currentFilter() {
        RecipeFilter f = filter.getValue();
        return f != null ? f : RecipeFilter.none();
    }
}
