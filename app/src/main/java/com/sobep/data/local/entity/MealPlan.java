package com.sobep.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Bảng meal_plans: một món được xếp vào một bữa của một ngày trong thực đơn tuần.
 * Một bữa có thể có nhiều món, nhưng cùng một món không được thêm hai lần vào cùng một bữa
 * (chỉ mục unique date + mealType + recipeId).
 */
@Entity(tableName = "meal_plans",
        foreignKeys = @ForeignKey(
                entity = Recipe.class,
                parentColumns = "id",
                childColumns = "recipeId",
                onDelete = ForeignKey.CASCADE),
        indices = {
                @Index("recipeId"),
                @Index(value = {"date", "mealType", "recipeId"}, unique = true)
        })
public class MealPlan {

    public static final int MEAL_BREAKFAST = 0;
    public static final int MEAL_LUNCH = 1;
    public static final int MEAL_DINNER = 2;

    @PrimaryKey(autoGenerate = true)
    public long id;

    // Ngày dạng "yyyy-MM-dd" (vd "2026-10-21"), tạo bằng DateUtils.toDateKey.
    // Chuỗi dạng này so sánh theo thứ tự chữ cũng đúng thứ tự ngày, nên lọc tuần bằng BETWEEN được
    @NonNull
    public String date = "";

    // Một trong các hằng MEAL_* ở trên
    public int mealType;

    public long recipeId;
}
