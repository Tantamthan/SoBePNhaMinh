package com.sobep.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Bảng steps: một bước nấu của một công thức.
 * Khoá ngoại recipeId trỏ tới recipes.id; xoá món thì các bước tự bị xoá theo (CASCADE).
 */
@Entity(tableName = "steps",
        foreignKeys = @ForeignKey(
                entity = Recipe.class,
                parentColumns = "id",
                childColumns = "recipeId",
                onDelete = ForeignKey.CASCADE),
        indices = @Index("recipeId"))
public class Step {

    @PrimaryKey(autoGenerate = true)
    public long id;

    public long recipeId;

    // Thứ tự bước, bắt đầu từ 0
    public int position;

    @NonNull
    public String content = "";

    // Số phút hẹn giờ cho bước này; 0 = không cần hẹn giờ
    public int timerMinutes;
}
