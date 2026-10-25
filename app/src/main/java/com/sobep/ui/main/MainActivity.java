package com.sobep.ui.main;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import com.sobep.R;
import com.sobep.databinding.ActivityMainBinding;
import com.sobep.ui.BaseActivity;
import com.sobep.ui.addedit.AddEditRecipeActivity;
import com.sobep.ui.discover.DiscoverActivity;
import com.sobep.ui.mealplan.MealPlanActivity;
import com.sobep.ui.shopping.ShoppingListActivity;

/**
 * Màn 2 · Sổ công thức (màn chính): lưới các món + lối vào các màn khác.
 * Chưa làm: đọc danh sách món từ CSDL (Room), tìm kiếm, lọc theo loại.
 */
public class MainActivity extends BaseActivity {

    private ActivityMainBinding binding;

    // Mở màn Thêm món và chờ kết quả: RESULT_OK nghĩa là người dùng đã bấm Lưu
    private final ActivityResultLauncher<Intent> addRecipeLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK) {
                    showToast(R.string.msg_recipe_saved);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        applySystemBarInsets(binding.getRoot());

        showEmptyList();
        setupNavigation();
    }

    /** Chưa có CSDL nên danh sách luôn trống. */
    private void showEmptyList() {
        binding.textCount.setText(getString(R.string.format_recipe_count, 0));
        binding.textEmpty.setVisibility(View.VISIBLE);
    }

    private void setupNavigation() {
        // Intent tường minh tới từng màn hình
        binding.buttonDiscover.setOnClickListener(v ->
                startActivity(new Intent(this, DiscoverActivity.class)));
        binding.buttonMealPlan.setOnClickListener(v ->
                startActivity(new Intent(this, MealPlanActivity.class)));
        binding.buttonShoppingList.setOnClickListener(v ->
                startActivity(new Intent(this, ShoppingListActivity.class)));

        // Không gửi mã món → AddEditRecipe hiểu là chế độ Thêm mới
        binding.fabAddRecipe.setOnClickListener(v ->
                addRecipeLauncher.launch(new Intent(this, AddEditRecipeActivity.class)));
    }
}
