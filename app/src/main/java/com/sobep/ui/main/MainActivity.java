package com.sobep.ui.main;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.lifecycle.ViewModelProvider;

import com.sobep.R;
import com.sobep.data.local.entity.Recipe;
import com.sobep.databinding.ActivityMainBinding;
import com.sobep.ui.BaseActivity;
import com.sobep.ui.addedit.AddEditRecipeActivity;
import com.sobep.ui.detail.RecipeDetailActivity;
import com.sobep.ui.discover.DiscoverActivity;
import com.sobep.ui.mealplan.MealPlanActivity;
import com.sobep.ui.shopping.ShoppingListActivity;
import com.sobep.util.IntentKeys;

import java.util.List;

/**
 * Màn 2 · Sổ công thức (màn chính): lưới các món + lối vào các màn khác.
 * Danh sách món đọc từ Room qua MainViewModel (LiveData): CSDL đổi thì lưới tự cập nhật.
 * Chưa làm: nối ô tìm kiếm và chip lọc theo loại (CN07, CN08).
 */
public class MainActivity extends BaseActivity implements RecipeAdapter.OnRecipeClickListener {

    private ActivityMainBinding binding;
    private MainViewModel viewModel;
    private RecipeAdapter adapter;

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

        // ViewModelProvider trả lại đúng ViewModel cũ khi xoay màn hình
        viewModel = new ViewModelProvider(this).get(MainViewModel.class);

        setupRecipeList();
        setupNavigation();
    }

    private void setupRecipeList() {
        // GridLayoutManager 2 cột đã khai báo trong activity_main.xml
        adapter = new RecipeAdapter(this);
        binding.recyclerRecipes.setAdapter(adapter);

        // Theo dõi danh sách món: mỗi lần CSDL đổi, Room gửi danh sách mới tới đây
        viewModel.getRecipes().observe(this, this::showRecipes);
    }

    private void showRecipes(List<Recipe> recipes) {
        adapter.submitList(recipes);

        int count = recipes == null ? 0 : recipes.size();
        binding.textCount.setText(getString(R.string.format_recipe_count, count));
        binding.textEmpty.setVisibility(count == 0 ? View.VISIBLE : View.GONE);
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

    // ---------- Bấm trên thẻ món (RecipeAdapter gọi về) ----------

    /** Bấm thẻ món → mở Chi tiết món, gửi kèm mã món qua Intent. */
    @Override
    public void onRecipeClick(Recipe recipe) {
        Intent intent = new Intent(this, RecipeDetailActivity.class);
        intent.putExtra(IntentKeys.EXTRA_RECIPE_ID, recipe.id);
        startActivity(intent);
    }

    /** CN09: bấm tim → đổi trạng thái yêu thích; Room báo lại nên icon tự cập nhật. */
    @Override
    public void onFavoriteClick(Recipe recipe) {
        viewModel.toggleFavorite(recipe);
    }
}
