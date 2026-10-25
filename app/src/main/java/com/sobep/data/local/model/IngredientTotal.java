package com.sobep.data.local.model;

import androidx.annotation.NonNull;

/**
 * Kết quả gộp nguyên liệu trùng của các món trong thực đơn tuần:
 * cùng tên + cùng đơn vị thì cộng dồn số lượng. Dùng để sinh danh sách đi chợ.
 */
public class IngredientTotal {

    @NonNull
    public String name = "";

    @NonNull
    public String unit = "";

    public double quantity;
}
