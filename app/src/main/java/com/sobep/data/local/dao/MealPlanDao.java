package com.sobep.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.sobep.data.local.entity.MealPlan;
import com.sobep.data.local.model.MealPlanWithRecipe;

import java.util.List;

/**
 * Truy cập bảng meal_plans (thực đơn tuần, CN10).
 */
@Dao
public interface MealPlanDao {

    /**
     * Các món trong khoảng ngày (thường là Thứ Hai → Chủ nhật), kèm tên món nhờ JOIN với recipes.
     * startDate, endDate dạng "yyyy-MM-dd".
     */
    @Query("SELECT m.*, r.name AS recipeName FROM meal_plans m"
            + " INNER JOIN recipes r ON r.id = m.recipeId"
            + " WHERE m.date BETWEEN :startDate AND :endDate"
            + " ORDER BY m.date, m.mealType, m.id")
    LiveData<List<MealPlanWithRecipe>> getMealsBetween(String startDate, String endDate);

    // Món đã có trong bữa đó thì bỏ qua (do chỉ mục unique), trả về -1
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    long insert(MealPlan mealPlan);

    @Query("DELETE FROM meal_plans WHERE id = :id")
    void deleteById(long id);
}
