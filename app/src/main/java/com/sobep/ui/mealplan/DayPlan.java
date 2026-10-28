package com.sobep.ui.mealplan;

import com.sobep.data.local.model.MealPlanWithRecipe;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * Một ngày trong Thực đơn tuần để hiển thị: ngày nào, có phải hôm nay không, các bữa đã xếp.
 * Không phải bảng CSDL — ViewModel ghép từ 7 ngày của tuần + kết quả truy vấn meal_plans.
 */
public class DayPlan {

    // 0 = Thứ Hai … 6 = Chủ nhật (vị trí trong string-array day_names)
    public final int dayIndex;
    public final Calendar date;
    // "yyyy-MM-dd", dùng khi thêm món vào ngày này
    public final String dateKey;
    public final boolean today;
    public final List<MealPlanWithRecipe> meals = new ArrayList<>();

    public DayPlan(int dayIndex, Calendar date, String dateKey, boolean today) {
        this.dayIndex = dayIndex;
        this.date = date;
        this.dateKey = dateKey;
        this.today = today;
    }
}
