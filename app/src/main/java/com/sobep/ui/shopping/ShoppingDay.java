package com.sobep.ui.shopping;

import androidx.annotation.Nullable;

import com.sobep.data.local.entity.ShoppingItem;
import com.sobep.data.local.model.MealPlanWithRecipe;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Một nhóm trong danh sách đi chợ: một ngày nấu (các món của ngày đó + nguyên liệu cần mua),
 * hoặc nhóm "Tự thêm" (date = null) gồm các dòng người dùng tự gõ.
 * Không phải bảng CSDL — ViewModel ghép từ shopping_items + meal_plans.
 */
public class ShoppingDay {

    // "yyyy-MM-dd"; null = nhóm Tự thêm
    @Nullable
    public final String dateKey;
    @Nullable
    public final Calendar date;
    public final boolean today;
    // Các món nấu trong ngày (để ghi tiêu đề "Tối: Canh trứng cà chua")
    public final List<MealPlanWithRecipe> meals = new ArrayList<>();
    public final List<ShoppingItem> items = new ArrayList<>();

    public ShoppingDay(@Nullable String dateKey, @Nullable Calendar date, boolean today) {
        this.dateKey = dateKey;
        this.date = date;
        this.today = today;
    }

    public boolean isManualGroup() {
        return dateKey == null;
    }

    /** Ngày nấu từ 2 món trở lên → mỗi nguyên liệu ghi thêm "cho món nào". */
    public boolean hasSeveralRecipes() {
        Set<Long> recipeIds = new HashSet<>();
        for (MealPlanWithRecipe meal : meals) {
            recipeIds.add(meal.mealPlan.recipeId);
        }
        return recipeIds.size() > 1;
    }

    public int boughtCount() {
        int count = 0;
        for (ShoppingItem item : items) {
            if (item.bought) {
                count++;
            }
        }
        return count;
    }
}
