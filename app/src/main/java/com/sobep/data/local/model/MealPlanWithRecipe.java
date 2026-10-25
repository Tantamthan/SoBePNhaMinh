package com.sobep.data.local.model;

import androidx.annotation.NonNull;
import androidx.room.Embedded;

import com.sobep.data.local.entity.MealPlan;

/**
 * Kết quả câu JOIN meal_plans với recipes: một món trong thực đơn kèm tên món để hiển thị.
 * Không phải bảng, chỉ là lớp chứa kết quả truy vấn.
 */
public class MealPlanWithRecipe {

    // Các cột của meal_plans (id, date, mealType, recipeId) được gắn vào đối tượng này
    @Embedded
    @NonNull
    public MealPlan mealPlan = new MealPlan();

    @NonNull
    public String recipeName = "";
}
