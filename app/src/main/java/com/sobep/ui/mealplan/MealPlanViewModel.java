package com.sobep.ui.mealplan;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import com.sobep.data.local.entity.Recipe;
import com.sobep.data.local.model.MealPlanWithRecipe;
import com.sobep.data.repository.MealPlanRepository;
import com.sobep.util.DateUtils;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * ViewModel màn Thực đơn tuần (CN10): giữ tuần đang xem, ghép 7 ngày với các món đã xếp, thêm / bỏ món.
 * Danh sách đi chợ tự lấy nguyên liệu theo thực đơn khi mở màn Đi chợ.
 */
public class MealPlanViewModel extends AndroidViewModel {

    private static final int DAYS_IN_WEEK = 7;

    private final MealPlanRepository repository;

    // 0 = tuần này, -1 = tuần trước, 1 = tuần sau… Nằm trong ViewModel nên xoay màn hình không mất
    private final MutableLiveData<Integer> weekOffset = new MutableLiveData<>(0);
    // 7 ngày của tuần đang xem, mỗi ngày kèm các bữa — đổi tuần thì switchMap đọc lại CSDL
    private final LiveData<List<DayPlan>> days;
    private final LiveData<List<Recipe>> recipes;

    public MealPlanViewModel(@NonNull Application application) {
        super(application);
        repository = new MealPlanRepository(application);
        recipes = repository.getAllRecipes();

        days = Transformations.switchMap(weekOffset, offset -> {
            Calendar monday = DateUtils.getMondayOfWeek(offset);
            Calendar sunday = (Calendar) monday.clone();
            sunday.add(Calendar.DAY_OF_MONTH, DAYS_IN_WEEK - 1);
            return Transformations.map(
                    repository.getMealsBetween(DateUtils.toDateKey(monday), DateUtils.toDateKey(sunday)),
                    meals -> buildDays(monday, meals));
        });
    }

    /** Ghép 7 ngày (Thứ Hai → Chủ nhật) với danh sách món đã xếp, theo cột date. */
    private static List<DayPlan> buildDays(Calendar monday, List<MealPlanWithRecipe> meals) {
        String today = DateUtils.todayKey();
        List<DayPlan> result = new ArrayList<>(DAYS_IN_WEEK);
        for (int i = 0; i < DAYS_IN_WEEK; i++) {
            Calendar date = (Calendar) monday.clone();
            date.add(Calendar.DAY_OF_MONTH, i);
            String key = DateUtils.toDateKey(date);
            DayPlan day = new DayPlan(i, date, key, key.equals(today));
            for (MealPlanWithRecipe meal : meals) {
                if (meal.mealPlan.date.equals(key)) {
                    day.meals.add(meal);
                }
            }
            result.add(day);
        }
        return result;
    }

    public LiveData<List<DayPlan>> getDays() {
        return days;
    }

    public LiveData<Integer> getWeekOffset() {
        return weekOffset;
    }

    public LiveData<List<Recipe>> getRecipes() {
        return recipes;
    }

    // ---------- Đổi tuần ----------

    public void previousWeek() {
        weekOffset.setValue(currentOffset() - 1);
    }

    public void nextWeek() {
        weekOffset.setValue(currentOffset() + 1);
    }

    public int currentOffset() {
        Integer offset = weekOffset.getValue();
        return offset != null ? offset : 0;
    }

    /** Thứ Hai của tuần đang xem. */
    public Calendar getMonday() {
        return DateUtils.getMondayOfWeek(currentOffset());
    }

    // ---------- Thêm / bỏ món ----------

    public void addMeal(DayPlan day, int mealType, Recipe recipe) {
        repository.addMeal(day.dateKey, mealType, recipe.id);
    }

    public void removeMeal(MealPlanWithRecipe meal) {
        repository.removeMeal(meal.mealPlan.id);
    }
}
