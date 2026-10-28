package com.sobep.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Transaction;
import androidx.room.Update;

import com.sobep.data.local.entity.Ingredient;
import com.sobep.data.local.entity.Recipe;
import com.sobep.data.local.entity.Step;

import java.util.List;

/**
 * Truy cập bảng recipes và hai bảng con ingredients, steps.
 * Hàm trả LiveData: Room tự chạy ở luồng nền và báo lại mỗi khi dữ liệu đổi.
 * Hàm còn lại (thêm/sửa/xoá): phải gọi qua AppDatabase.databaseWriteExecutor, không gọi trên main thread.
 * Dùng abstract class (thay vì interface) để viết được các hàm @Transaction có thân hàm.
 */
@Dao
public abstract class RecipeDao {

    // ---------- Đọc ----------

    @Query("SELECT * FROM recipes ORDER BY updatedAt DESC")
    public abstract LiveData<List<Recipe>> getAll();

    @Query("SELECT * FROM recipes WHERE id = :id")
    public abstract LiveData<Recipe> getById(long id);

    @Query("SELECT * FROM ingredients WHERE recipeId = :recipeId ORDER BY position")
    public abstract LiveData<List<Ingredient>> getIngredients(long recipeId);

    @Query("SELECT * FROM steps WHERE recipeId = :recipeId ORDER BY position")
    public abstract LiveData<List<Step>> getSteps(long recipeId);

    /**
     * Tìm kiếm + lọc trong một câu truy vấn (CN07, CN08, CN09). Tham số để trống / 0 / false = bỏ qua điều kiện đó.
     * @param keyword      khớp với tên món HOẶC tên một nguyên liệu của món
     * @param category     mã danh mục (Recipe.CATEGORY_*), "" = mọi danh mục
     * @param favoriteOnly true = chỉ món yêu thích
     * @param maxMinutes   thời gian nấu tối đa, 0 = không giới hạn
     */
    @Query("SELECT * FROM recipes"
            + " WHERE (:keyword = '' OR name LIKE '%' || :keyword || '%'"
            + "        OR id IN (SELECT recipeId FROM ingredients WHERE name LIKE '%' || :keyword || '%'))"
            + " AND (:category = '' OR category = :category)"
            + " AND (:favoriteOnly = 0 OR favorite = 1)"
            + " AND (:maxMinutes <= 0 OR cookTimeMinutes <= :maxMinutes)"
            + " ORDER BY updatedAt DESC")
    public abstract LiveData<List<Recipe>> search(String keyword, String category,
                                                  boolean favoriteOnly, int maxMinutes);

    // ---------- Đọc một lần (không LiveData), gọi qua databaseWriteExecutor ----------
    // Dùng khi mở màn Sửa món: chỉ cần lấy dữ liệu một lần để điền sẵn vào form

    @Query("SELECT * FROM recipes WHERE id = :id")
    public abstract Recipe getByIdNow(long id);

    @Query("SELECT * FROM ingredients WHERE recipeId = :recipeId ORDER BY position")
    public abstract List<Ingredient> getIngredientsNow(long recipeId);

    @Query("SELECT * FROM steps WHERE recipeId = :recipeId ORDER BY position")
    public abstract List<Step> getStepsNow(long recipeId);

    // ---------- Ghi (gọi qua databaseWriteExecutor) ----------

    @Insert
    public abstract long insertRecipe(Recipe recipe);

    @Update
    public abstract void updateRecipe(Recipe recipe);

    // Xoá món; nguyên liệu, bước nấu và các dòng thực đơn của món tự xoá theo nhờ CASCADE
    @Delete
    public abstract void deleteRecipe(Recipe recipe);

    @Insert
    public abstract void insertIngredients(List<Ingredient> ingredients);

    @Insert
    public abstract void insertSteps(List<Step> steps);

    @Query("DELETE FROM ingredients WHERE recipeId = :recipeId")
    public abstract void deleteIngredientsOf(long recipeId);

    @Query("DELETE FROM steps WHERE recipeId = :recipeId")
    public abstract void deleteStepsOf(long recipeId);

    @Query("UPDATE recipes SET favorite = :favorite, updatedAt = :updatedAt WHERE id = :id")
    public abstract void setFavorite(long id, boolean favorite, long updatedAt);

    /**
     * Thêm món mới cùng nguyên liệu và các bước trong MỘT giao dịch:
     * lỗi giữa chừng thì không lưu gì, tránh có món mà thiếu nguyên liệu.
     * @return id của món vừa thêm
     */
    @Transaction
    public long insertFullRecipe(Recipe recipe, List<Ingredient> ingredients, List<Step> steps) {
        long now = System.currentTimeMillis();
        recipe.createdAt = now;
        recipe.updatedAt = now;
        long recipeId = insertRecipe(recipe);
        saveChildren(recipeId, ingredients, steps);
        return recipeId;
    }

    /**
     * Sửa món: cập nhật bảng recipes, xoá hết nguyên liệu/bước cũ rồi chèn danh sách mới.
     * Làm vậy đơn giản hơn so từng dòng xem dòng nào thêm/sửa/xoá.
     */
    @Transaction
    public void updateFullRecipe(Recipe recipe, List<Ingredient> ingredients, List<Step> steps) {
        recipe.updatedAt = System.currentTimeMillis();
        updateRecipe(recipe);
        deleteIngredientsOf(recipe.id);
        deleteStepsOf(recipe.id);
        saveChildren(recipe.id, ingredients, steps);
    }

    // Gắn mã món và số thứ tự cho từng nguyên liệu / bước rồi chèn vào CSDL
    private void saveChildren(long recipeId, List<Ingredient> ingredients, List<Step> steps) {
        for (int i = 0; i < ingredients.size(); i++) {
            Ingredient ingredient = ingredients.get(i);
            ingredient.id = 0; // 0 = để Room tự cấp id mới
            ingredient.recipeId = recipeId;
            ingredient.position = i;
        }
        for (int i = 0; i < steps.size(); i++) {
            Step step = steps.get(i);
            step.id = 0;
            step.recipeId = recipeId;
            step.position = i;
        }
        insertIngredients(ingredients);
        insertSteps(steps);
    }
}
