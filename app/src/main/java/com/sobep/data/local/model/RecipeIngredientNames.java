package com.sobep.data.local.model;

/**
 * Kết quả truy vấn: tên các nguyên liệu của một món, gộp thành một chuỗi (GROUP_CONCAT).
 * Dùng ở màn chính để tìm món theo nguyên liệu mà không phân biệt dấu ("trung" vẫn ra "Trứng").
 */
public class RecipeIngredientNames {

    // Mã món trong bảng recipes
    public long recipeId;

    // Tên nguyên liệu của món, cách nhau bởi dấu cách, vd "Trứng gà Thịt ba chỉ Nước dừa"
    public String names;
}
