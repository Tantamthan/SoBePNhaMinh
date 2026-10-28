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

    // Ngày Thứ Hai của tuần (String "yyyy-MM-dd", vd "2026-10-26"). MealPlan gửi sang ShoppingList
    // để đi chợ theo đúng tuần đang xem; không gửi = tuần này
    public static final String EXTRA_WEEK_START = "com.sobep.extra.WEEK_START";

    // Giá trị mặc định khi Intent không mang mã món
    public static final long NO_RECIPE_ID = -1L;

    private IntentKeys() {
        // Lớp chỉ chứa hằng số, không cho tạo đối tượng
    }
}
