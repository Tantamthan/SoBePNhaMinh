package com.sobep.util;

import android.content.Context;

import com.sobep.R;
import com.sobep.data.local.entity.Recipe;

/**
 * Đổi mã danh mục lưu trong CSDL (vd "soup") thành tên hiển thị (vd "Canh").
 * Tên lấy từ string-array recipe_categories, cùng thứ tự với Recipe.CATEGORIES.
 */
public final class CategoryNames {

    private CategoryNames() {
        // Lớp tiện ích, không tạo đối tượng
    }

    public static String of(Context context, String category) {
        String[] names = context.getResources().getStringArray(R.array.recipe_categories);
        for (int i = 0; i < Recipe.CATEGORIES.length; i++) {
            if (Recipe.CATEGORIES[i].equals(category)) {
                return names[i];
            }
        }
        // Mã lạ → coi như Món chính (danh mục đầu tiên)
        return names[0];
    }
}
