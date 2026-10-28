package com.sobep.data.repository;

import android.app.Application;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;

import com.sobep.data.local.AppDatabase;
import com.sobep.data.local.dao.RecipeDao;
import com.sobep.data.local.entity.Ingredient;
import com.sobep.data.local.entity.Recipe;
import com.sobep.data.local.entity.Step;

import java.util.List;
import java.util.concurrent.ExecutorService;

/**
 * Lớp trung gian giữa ViewModel và CSDL cho nhóm chức năng Công thức (CN01–CN09).
 * ViewModel chỉ gọi Repository, không biết tới DAO hay Room → sau này thêm Firestore (CN14)
 * thì chỉ sửa ở đây, không phải sửa ViewModel / Activity.
 * - Hàm đọc trả LiveData: Room tự chạy ở luồng nền.
 * - Hàm ghi chạy trên databaseWriteExecutor, không bao giờ chạy trên main thread.
 */
public class RecipeRepository {

    /** Báo lại khi lưu món xong. Lưu ý: được gọi ở LUỒNG NỀN, ViewModel phải dùng postValue. */
    public interface OnSavedListener {
        void onSaved(long recipeId);
    }

    /** Trả lại món đầy đủ (đọc một lần). Được gọi ở LUỒNG NỀN. recipe = null nếu không tìm thấy. */
    public interface OnRecipeLoadedListener {
        void onLoaded(@Nullable Recipe recipe, List<Ingredient> ingredients, List<Step> steps);
    }

    private final RecipeDao recipeDao;
    private final ExecutorService executor;

    public RecipeRepository(Application application) {
        AppDatabase db = AppDatabase.getInstance(application);
        recipeDao = db.recipeDao();
        executor = AppDatabase.databaseWriteExecutor;
    }

    // ---------- Đọc ----------

    /** CN01: toàn bộ món, món sửa gần nhất lên đầu. */
    public LiveData<List<Recipe>> getAllRecipes() {
        return recipeDao.getAll();
    }

    /** CN07–CN09: tìm theo tên/nguyên liệu, lọc danh mục, thời gian nấu, yêu thích. */
    public LiveData<List<Recipe>> searchRecipes(String keyword, String category,
                                                boolean favoriteOnly, int maxMinutes) {
        return recipeDao.search(keyword, category, favoriteOnly, maxMinutes);
    }

    /** CN02: thông tin một món, kèm nguyên liệu và các bước. */
    public LiveData<Recipe> getRecipe(long recipeId) {
        return recipeDao.getById(recipeId);
    }

    public LiveData<List<Ingredient>> getIngredients(long recipeId) {
        return recipeDao.getIngredients(recipeId);
    }

    public LiveData<List<Step>> getSteps(long recipeId) {
        return recipeDao.getSteps(recipeId);
    }

    /** CN04: đọc món một lần để điền sẵn vào form Sửa. */
    public void loadFullRecipe(long recipeId, OnRecipeLoadedListener listener) {
        executor.execute(() -> {
            Recipe recipe = recipeDao.getByIdNow(recipeId);
            List<Ingredient> ingredients = recipeDao.getIngredientsNow(recipeId);
            List<Step> steps = recipeDao.getStepsNow(recipeId);
            listener.onLoaded(recipe, ingredients, steps);
        });
    }

    // ---------- Ghi ----------

    /**
     * CN03 + CN04: lưu món cùng nguyên liệu và các bước.
     * recipe.id == 0 → món mới (thêm); khác 0 → món đã có (sửa).
     */
    public void saveRecipe(Recipe recipe, List<Ingredient> ingredients, List<Step> steps,
                           @Nullable OnSavedListener listener) {
        executor.execute(() -> {
            long recipeId;
            if (recipe.id == 0) {
                recipeId = recipeDao.insertFullRecipe(recipe, ingredients, steps);
            } else {
                recipeDao.updateFullRecipe(recipe, ingredients, steps);
                recipeId = recipe.id;
            }
            if (listener != null) {
                listener.onSaved(recipeId);
            }
        });
    }

    /** CN05: xoá món (nguyên liệu, bước, thực đơn của món tự xoá theo nhờ CASCADE). */
    public void deleteRecipe(Recipe recipe) {
        executor.execute(() -> recipeDao.deleteRecipe(recipe));
    }

    /** CN09: bật/tắt yêu thích. */
    public void setFavorite(long recipeId, boolean favorite) {
        executor.execute(() ->
                recipeDao.setFavorite(recipeId, favorite, System.currentTimeMillis()));
    }
}
