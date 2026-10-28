package com.sobep.ui.shopping;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import com.sobep.data.local.entity.ShoppingItem;
import com.sobep.data.local.model.MealPlanWithRecipe;
import com.sobep.data.repository.ShoppingRepository;
import com.sobep.util.DateUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * ViewModel màn Đi chợ (CN11, CN12): danh sách nguyên liệu chia theo từng ngày nấu trong tuần.
 * - Mở màn → tự lấy nguyên liệu theo thực đơn tuần (syncWithMealPlan), giữ dấu đã mua và dòng tự gõ.
 * - Ghép shopping_items (nguyên liệu) với meal_plans (món của từng ngày) thành danh sách ShoppingDay.
 */
public class ShoppingListViewModel extends AndroidViewModel {

    private static final int DAYS_IN_WEEK = 7;

    private final ShoppingRepository repository;

    // Thứ Hai của tuần đi chợ ("yyyy-MM-dd")
    private final MutableLiveData<String> weekStart = new MutableLiveData<>();
    private final LiveData<List<ShoppingItem>> allItems;
    private final LiveData<List<MealPlanWithRecipe>> meals;
    // Kết hợp 2 nguồn trên → các nhóm theo ngày
    private final MediatorLiveData<List<ShoppingDay>> days = new MediatorLiveData<>();
    private boolean started;

    public ShoppingListViewModel(@NonNull Application application) {
        super(application);
        repository = new ShoppingRepository(application);
        allItems = repository.getAll();
        meals = Transformations.switchMap(weekStart,
                start -> repository.getMealsBetween(start, weekEnd(start)));

        days.addSource(allItems, list -> buildDays());
        days.addSource(meals, list -> buildDays());
    }

    /**
     * Gọi trong onCreate với Thứ Hai của tuần cần đi chợ.
     * Lần đầu: cập nhật nguyên liệu theo thực đơn. Xoay màn hình gọi lại thì bỏ qua.
     */
    public void start(String mondayKey) {
        if (started) {
            return;
        }
        started = true;
        weekStart.setValue(mondayKey);
        repository.syncWithMealPlan(mondayKey, weekEnd(mondayKey));
    }

    private static String weekEnd(String mondayKey) {
        return DateUtils.addDays(mondayKey, DAYS_IN_WEEK - 1);
    }

    public LiveData<List<ShoppingDay>> getDays() {
        return days;
    }

    public LiveData<String> getWeekStart() {
        return weekStart;
    }

    /**
     * Gom nguyên liệu theo ngày (TreeMap tự sắp ngày tăng dần), gắn các món của ngày đó;
     * dòng tự gõ (date = null) vào nhóm "Tự thêm" ở cuối.
     */
    private void buildDays() {
        List<ShoppingItem> items = allItems.getValue();
        String start = weekStart.getValue();
        if (items == null || start == null) {
            return;
        }
        String end = weekEnd(start);
        String today = DateUtils.todayKey();

        Map<String, ShoppingDay> byDate = new TreeMap<>();
        ShoppingDay manualGroup = new ShoppingDay(null, null, false);
        for (ShoppingItem item : items) {
            if (item.date == null) {
                manualGroup.items.add(item);
            } else if (item.date.compareTo(start) >= 0 && item.date.compareTo(end) <= 0) {
                ShoppingDay day = byDate.get(item.date);
                if (day == null) {
                    day = new ShoppingDay(item.date, DateUtils.fromDateKey(item.date), item.date.equals(today));
                    byDate.put(item.date, day);
                }
                day.items.add(item);
            }
        }

        List<MealPlanWithRecipe> mealList = meals.getValue();
        if (mealList != null) {
            for (MealPlanWithRecipe meal : mealList) {
                ShoppingDay day = byDate.get(meal.mealPlan.date);
                if (day != null) {
                    day.meals.add(meal);
                }
            }
        }

        List<ShoppingDay> result = new ArrayList<>(byDate.values());
        if (!manualGroup.items.isEmpty()) {
            result.add(manualGroup);
        }
        days.setValue(result);
    }

    // ---------- Thao tác ----------

    /** CN12: bấm ô tích → đổi trạng thái đã mua. */
    public void toggleBought(ShoppingItem item) {
        repository.setBought(item.id, !item.bought);
    }

    /** Thêm thứ cần mua do người dùng tự gõ. @return false nếu ô trống */
    public boolean addItem(String name) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) {
            return false;
        }
        repository.addManualItem(trimmed);
        return true;
    }

    public void clearBoughtManual() {
        repository.clearBoughtManual();
    }
}
