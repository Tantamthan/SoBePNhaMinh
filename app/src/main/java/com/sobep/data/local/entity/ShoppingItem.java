package com.sobep.data.local.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Bảng shopping_items: một dòng trong danh sách đi chợ.
 * Không có khoá ngoại tới recipes, vì một dòng có thể là tổng của nguyên liệu từ nhiều món.
 * Version 2 thêm 2 cột date, forRecipes (xem AppDatabase.MIGRATION_1_2).
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
    // Khi cập nhật lại từ thực đơn chỉ thay các dòng tự sinh, giữ lại dòng tự gõ
    public boolean manual;

    // Mua cho ngày nào ("yyyy-MM-dd", ngày nấu món trong thực đơn); null = dòng tự gõ, không thuộc ngày nào
    @Nullable
    public String date;

    // Nguyên liệu này dùng cho món nào, vd "Canh trứng cà chua, Trứng chiên hành lá"; null = dòng tự gõ
    @Nullable
    public String forRecipes;
}
