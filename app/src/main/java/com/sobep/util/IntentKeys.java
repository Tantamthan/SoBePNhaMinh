package com.sobep.util;

/**
 * Tên các key dùng để gửi dữ liệu giữa các Activity qua Intent (putExtra / getXxxExtra).
 * Đặt chung một chỗ để Activity gửi và Activity nhận luôn dùng đúng một tên, tránh gõ sai.
 */
public final class IntentKeys {

    // Mã món ăn (long). Gửi sang RecipeDetail, CookingMode; gửi sang AddEditRecipe nghĩa là chế độ Sửa
    public static final String EXTRA_RECIPE_ID = "com.sobep.extra.RECIPE_ID";

    // Đoạn chữ quét được (String). ScanRecipe trả về cho AddEditRecipe
    public static final String EXTRA_SCANNED_TEXT = "com.sobep.extra.SCANNED_TEXT";

    // Khoảng ngày của tuần (String, vd "6/10 – 12/10/2026"). MealPlan gửi sang ShoppingList
    public static final String EXTRA_WEEK_RANGE = "com.sobep.extra.WEEK_RANGE";

    // Giá trị mặc định khi Intent không mang mã món
    public static final long NO_RECIPE_ID = -1L;

    private IntentKeys() {
        // Lớp chỉ chứa hằng số, không cho tạo đối tượng
    }
}
