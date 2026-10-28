package com.sobep.data.local.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Kết quả gộp nguyên liệu trùng của các món trong thực đơn, THEO TỪNG NGÀY:
 * cùng ngày + cùng tên + cùng đơn vị thì cộng dồn số lượng. Dùng để sinh danh sách đi chợ.
 */
public class IngredientTotal {

    // Ngày nấu ("yyyy-MM-dd")
    @NonNull
    public String date = "";

    @NonNull
    public String name = "";

    @NonNull
    public String unit = "";

    public double quantity;

    // Tên các món dùng nguyên liệu này trong ngày, ngăn cách bằng dấu phẩy (GROUP_CONCAT)
    @Nullable
    public String forRecipes;
}
