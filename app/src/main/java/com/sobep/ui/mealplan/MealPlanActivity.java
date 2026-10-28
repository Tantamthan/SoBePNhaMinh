package com.sobep.ui.mealplan;

import android.content.Intent;
import android.os.Bundle;

import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.sobep.R;
import com.sobep.data.local.entity.Recipe;
import com.sobep.data.local.model.MealPlanWithRecipe;
import com.sobep.databinding.ActivityMealPlanBinding;
import com.sobep.ui.BaseActivity;
import com.sobep.ui.detail.RecipeDetailActivity;
import com.sobep.ui.shopping.ShoppingListActivity;
import com.sobep.util.DateUtils;
import com.sobep.util.IntentKeys;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * Màn 7 · Thực đơn tuần (CN10): 7 thẻ ngày, mỗi ngày có các bữa Sáng / Trưa / Tối.
 * - Bấm + trên thẻ ngày → chọn món trong sổ → chọn bữa → thêm vào thực đơn.
 * - Bấm một bữa → mở Chi tiết món (Intent tường minh); nhấn giữ → bỏ món khỏi thực đơn.
 * - Nút giỏ hàng → mở ShoppingList kèm ngày Thứ Hai của tuần đang xem; màn Đi chợ tự lấy nguyên liệu
 *   theo thực đơn tuần đó, chia theo từng ngày (CN11).
 */
public class MealPlanActivity extends BaseActivity implements DayPlanAdapter.OnDayActionListener {

    private ActivityMealPlanBinding binding;
    private MealPlanViewModel viewModel;
    private DayPlanAdapter dayAdapter;
    // Món trong sổ (để chọn khi thêm vào thực đơn)
    private List<Recipe> recipes = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMealPlanBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        applySystemBarInsets(binding.getRoot());

        // Tuần đang xem nằm trong ViewModel → xoay màn hình không mất, không cần onSaveInstanceState
        viewModel = new ViewModelProvider(this).get(MealPlanViewModel.class);

        // LinearLayoutManager đã khai báo trong activity_meal_plan.xml
        dayAdapter = new DayPlanAdapter(this);
        binding.recyclerDays.setAdapter(dayAdapter);

        binding.buttonBack.setOnClickListener(v -> finish());
        binding.buttonPrevWeek.setOnClickListener(v -> viewModel.previousWeek());
        binding.buttonNextWeek.setOnClickListener(v -> viewModel.nextWeek());
        binding.fabCreateShoppingList.setOnClickListener(v -> openShoppingList());

        observeViewModel();
    }

    private void observeViewModel() {
        viewModel.getWeekOffset().observe(this, offset -> showWeekTitle());
        viewModel.getDays().observe(this, dayAdapter::setItems);
        viewModel.getRecipes().observe(this, list -> recipes = list != null ? list : new ArrayList<>());
    }

    private void showWeekTitle() {
        int offset = viewModel.currentOffset();
        Calendar monday = viewModel.getMonday();
        binding.textWeekRange.setText(DateUtils.formatWeekRange(monday));

        if (offset == 0) {
            binding.textWeekTitle.setText(R.string.this_week);
        } else if (offset == -1) {
            binding.textWeekTitle.setText(R.string.week_previous);
        } else if (offset == 1) {
            binding.textWeekTitle.setText(R.string.week_next);
        } else {
            binding.textWeekTitle.setText(getString(R.string.format_week_number, DateUtils.getWeekNumber(monday)));
        }
    }

    // ---------- Thêm món: chọn món → chọn bữa ----------

    @Override
    public void onAddMealClick(DayPlan day) {
        if (recipes.isEmpty()) {
            showToast(R.string.msg_no_recipes_for_plan);
            return;
        }
        String[] names = new String[recipes.size()];
        for (int i = 0; i < recipes.size(); i++) {
            names[i] = recipes.get(i).name;
        }
        String dayName = getResources().getStringArray(R.array.day_names)[day.dayIndex];
        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.format_pick_recipe_title, dayName))
                .setItems(names, (dialog, which) -> pickMealType(day, recipes.get(which)))
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    /** Hộp thoại thứ hai: chọn Sáng / Trưa / Tối. Vị trí trong meal_names trùng với MealPlan.MEAL_*. */
    private void pickMealType(DayPlan day, Recipe recipe) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.format_pick_meal_title, recipe.name))
                .setItems(R.array.meal_names, (dialog, mealType) -> viewModel.addMeal(day, mealType, recipe))
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    // ---------- Bấm vào một bữa ----------

    /** Intent tường minh: mở Chi tiết món của bữa đó. */
    @Override
    public void onMealClick(MealPlanWithRecipe meal) {
        Intent intent = new Intent(this, RecipeDetailActivity.class);
        intent.putExtra(IntentKeys.EXTRA_RECIPE_ID, meal.mealPlan.recipeId);
        startActivity(intent);
    }

    /** Nhấn giữ → hỏi bỏ món khỏi thực đơn (món vẫn còn trong sổ). */
    @Override
    public void onMealLongClick(MealPlanWithRecipe meal) {
        String mealName = getString(MealEntryAdapter.mealNameRes(meal.mealPlan.mealType));
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.dialog_remove_meal_title)
                .setMessage(getString(R.string.format_remove_meal_message, meal.recipeName, mealName))
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_remove, (dialog, which) -> viewModel.removeMeal(meal))
                .show();
    }

    // ---------- Đi chợ ----------

    /**
     * Intent tường minh + putExtra: gửi ngày Thứ Hai ("yyyy-MM-dd") của tuần đang xem sang màn Đi chợ.
     * Màn Đi chợ dựa vào đó lấy nguyên liệu theo đúng tuần này (xem tuần sau thì đi chợ cho tuần sau).
     */
    private void openShoppingList() {
        Intent intent = new Intent(this, ShoppingListActivity.class);
        intent.putExtra(IntentKeys.EXTRA_WEEK_START, DateUtils.toDateKey(viewModel.getMonday()));
        startActivity(intent);
    }
}
