package com.sobep.data.local.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Bảng recipes: một công thức (món ăn) trong sổ bếp.
 * Nguyên liệu và các bước nấu nằm ở bảng riêng (ingredients, steps), nối bằng recipeId.
 */
@Entity(tableName = "recipes")
public class Recipe {

    // Mã danh mục lưu trong CSDL. Thứ tự trùng với string-array recipe_categories trong strings.xml
    public static final String CATEGORY_MAIN = "main";
    public static final String CATEGORY_SOUP = "soup";
    public static final String CATEGORY_STIR_FRY = "stir_fry";
    public static final String CATEGORY_DESSERT = "dessert";
    public static final String CATEGORY_DRINK = "drink";
    public static final String[] CATEGORIES = {
            CATEGORY_MAIN, CATEGORY_SOUP, CATEGORY_STIR_FRY, CATEGORY_DESSERT, CATEGORY_DRINK
    };

    @PrimaryKey(autoGenerate = true)
    public long id;

    @NonNull
    public String name = "";

    // Một trong các hằng CATEGORY_* ở trên
    @NonNull
    public String category = CATEGORY_MAIN;

    public int cookTimeMinutes;

    // Số người ăn gốc của công thức, dùng để quy đổi khẩu phần
    public int servings = 2;

    // Đường dẫn ảnh đã chép vào bộ nhớ riêng của app (null = chưa có ảnh)
    @Nullable
    public String imagePath;

    @Nullable
    public String videoUrl;

    @Nullable
    public String note;

    public boolean favorite;

    // Mã món mẫu (trong assets/mon_mau.json) nếu món được chép từ màn Khám phá; null = tự nhập.
    // Dùng để biết món mẫu nào đã lưu, tránh chép trùng
    @Nullable
    public String sourceSampleId;

    // Thời điểm tạo / sửa lần cuối (mili giây). updatedAt dùng để sắp xếp và để đồng bộ Firestore sau này
    public long createdAt;
    public long updatedAt;
}
