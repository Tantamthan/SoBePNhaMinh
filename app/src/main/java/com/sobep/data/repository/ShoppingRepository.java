package com.sobep.data.repository;

import android.app.Application;

import androidx.lifecycle.LiveData;

import com.sobep.data.local.AppDatabase;
import com.sobep.data.local.dao.MealPlanDao;
import com.sobep.data.local.dao.ShoppingItemDao;
import com.sobep.data.local.entity.ShoppingItem;
import com.sobep.data.local.model.MealPlanWithRecipe;

import java.util.List;
import java.util.concurrent.ExecutorService;

/**
 * Lớp trung gian cho Danh sách đi chợ (CN11, CN12).
 * Đọc trả LiveData; ghi chạy trên databaseWriteExecutor.
 */
public class ShoppingRepository {

    private final ShoppingItemDao shoppingItemDao;
    private final MealPlanDao mealPlanDao;
    private final ExecutorService executor;

    public ShoppingRepository(Application application) {
        AppDatabase db = AppDatabase.getInstance(application);
        shoppingItemDao = db.shoppingItemDao();
        mealPlanDao = db.mealPlanDao();
        executor = AppDatabase.databaseWriteExecutor;
    }

    /** Mọi dòng, xếp theo ngày; trong mỗi ngày chưa mua lên trước; dòng tự gõ ở cuối. */
    public LiveData<List<ShoppingItem>> getAll() {
        return shoppingItemDao.getAll();
    }

    /** Các món trong thực đơn của tuần, để ghi tiêu đề mỗi ngày ("Tối: Canh trứng cà chua"). */
    public LiveData<List<MealPlanWithRecipe>> getMealsBetween(String startDate, String endDate) {
        return mealPlanDao.getMealsBetween(startDate, endDate);
    }

    /** CN11: lấy nguyên liệu theo thực đơn của tuần (gộp theo từng ngày), giữ dấu đã mua và dòng tự gõ. */
    public void syncWithMealPlan(String startDate, String endDate) {
        executor.execute(() -> shoppingItemDao.syncWithMealPlan(startDate, endDate));
    }

    /** CN12: đánh dấu đã mua / bỏ đánh dấu. */
    public void setBought(long id, boolean bought) {
        executor.execute(() -> shoppingItemDao.setBought(id, bought));
    }

    /** Người dùng tự gõ thêm một thứ cần mua (không có số lượng, không thuộc ngày nào). */
    public void addManualItem(String name) {
        ShoppingItem item = new ShoppingItem();
        item.name = name;
        item.manual = true;
        executor.execute(() -> shoppingItemDao.insert(item));
    }

    /** Xoá các dòng tự gõ đã mua (dòng theo thực đơn chỉ gạch ngang, không xoá). */
    public void clearBoughtManual() {
        executor.execute(shoppingItemDao::deleteBoughtManual);
    }
}
