package com.sobep.data.repository;

import android.app.Application;

import androidx.lifecycle.LiveData;

import com.sobep.data.local.AppDatabase;
import com.sobep.data.local.dao.MealPlanDao;
import com.sobep.data.local.dao.RecipeDao;
import com.sobep.data.local.entity.MealPlan;
import com.sobep.data.local.entity.Recipe;
import com.sobep.data.local.model.MealPlanWithRecipe;

import java.util.List;
import java.util.concurrent.ExecutorService;

/**
 * Lớp trung gian cho Thực đơn tuần (CN10).
 * Đọc trả LiveData; ghi chạy trên databaseWriteExecutor (không chạy trên main thread).
 * Danh sách đi chợ tự lấy theo thực đơn khi mở màn Đi chợ (xem ShoppingRepository.syncWithMealPlan).
 */
public class MealPlanRepository {

    private final MealPlanDao mealPlanDao;
    private final RecipeDao recipeDao;
    private final ExecutorService executor;

    public MealPlanRepository(Application application) {
        AppDatabase db = AppDatabase.getInstance(application);
        mealPlanDao = db.mealPlanDao();
        recipeDao = db.recipeDao();
        executor = AppDatabase.databaseWriteExecutor;
    }

    /** Các món trong thực đơn từ startDate đến endDate ("yyyy-MM-dd"), kèm tên món. */
    public LiveData<List<MealPlanWithRecipe>> getMealsBetween(String startDate, String endDate) {
        return mealPlanDao.getMealsBetween(startDate, endDate);
    }

    /** Các món trong sổ, để chọn khi thêm vào thực đơn. */
    public LiveData<List<Recipe>> getAllRecipes() {
        return recipeDao.getAll();
    }

    /** Thêm món vào một bữa của một ngày. Món đã có trong bữa đó thì Room bỏ qua (unique index). */
    public void addMeal(String date, int mealType, long recipeId) {
        MealPlan mealPlan = new MealPlan();
        mealPlan.date = date;
        mealPlan.mealType = mealType;
        mealPlan.recipeId = recipeId;
        executor.execute(() -> mealPlanDao.insert(mealPlan));
    }

    public void removeMeal(long mealPlanId) {
        executor.execute(() -> mealPlanDao.deleteById(mealPlanId));
    }
}
