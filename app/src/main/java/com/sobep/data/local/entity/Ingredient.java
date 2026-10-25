package com.sobep.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Bảng ingredients: một nguyên liệu của một công thức.
 * Khoá ngoại recipeId trỏ tới recipes.id; xoá món thì nguyên liệu tự bị xoá theo (CASCADE).
 */
@Entity(tableName = "ingredients",
        foreignKeys = @ForeignKey(
                entity = Recipe.class,
                parentColumns = "id",
                childColumns = "recipeId",
                onDelete = ForeignKey.CASCADE),
        indices = @Index("recipeId"))
public class Ingredient {

    @PrimaryKey(autoGenerate = true)
    public long id;

    public long recipeId;

    @NonNull
    public String name = "";

    // Số lượng cho đúng số người gốc của món; 0 = không ghi số lượng (vd "muối vừa ăn")
    public double quantity;

    @NonNull
    public String unit = "";

    // Thứ tự hiển thị trong danh sách nguyên liệu
    public int position;
}
