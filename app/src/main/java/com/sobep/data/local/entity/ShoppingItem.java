package com.sobep.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Bảng shopping_items: một dòng trong danh sách đi chợ.
 * Không có khoá ngoại tới recipes, vì một dòng có thể là tổng của nguyên liệu từ nhiều món.
 */
@Entity(tableName = "shopping_items")
public class ShoppingItem {

    @PrimaryKey(autoGenerate = true)
    public long id;

    @NonNull
    public String name = "";

    // 0 = không ghi số lượng
    public double quantity;

    @NonNull
    public String unit = "";

    // Đã mua chưa (đánh dấu bằng ô tick)
    public boolean bought;

    // true = người dùng tự gõ thêm; false = sinh ra từ thực đơn tuần.
    // Khi tạo lại danh sách từ thực đơn chỉ xoá các dòng tự sinh, giữ lại dòng tự gõ
    public boolean manual;
}
