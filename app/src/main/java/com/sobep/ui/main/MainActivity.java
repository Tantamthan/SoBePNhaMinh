package com.sobep.ui.main;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.sobep.R;
import com.sobep.data.local.entity.Recipe;
import com.sobep.databinding.ActivityMainBinding;
import com.sobep.ui.BaseActivity;
import com.sobep.ui.addedit.AddEditRecipeActivity;
import com.sobep.ui.detail.RecipeDetailActivity;
import com.sobep.ui.discover.DiscoverActivity;
import com.sobep.ui.mealplan.MealPlanActivity;
import com.sobep.ui.shopping.ShoppingListActivity;
import com.sobep.util.CategoryNames;
import com.sobep.util.IntentKeys;

import java.util.List;

/**
 * Màn 2 · Sổ công thức (màn chính): lưới các món + lối vào các màn khác.
 * Danh sách món đọc từ Room qua MainViewModel (LiveData): CSDL đổi thì lưới tự cập nhật.
 * Tìm theo tên món / nguyên liệu (CN07), lọc theo loại, thời gian nấu (CN08) và yêu thích (CN09).
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
        setupFilters();
        setupNavigation();
    }

    /** CN07: ô tìm kiếm, CN08 + CN09: chip lọc theo loại / thời gian nấu / yêu thích. */
    private void setupFilters() {
        // Gõ tới đâu tìm tới đó: mỗi lần chữ đổi, ViewModel đổi bộ lọc → Room chạy lại truy vấn
        binding.editSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                viewModel.setKeyword(s.toString());
            }
        });
        // Bấm nút Tìm trên bàn phím → ẩn bàn phím (kết quả đã hiện sẵn)
        binding.editSearch.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                hideKeyboard();
                return true;
            }
            return false;
        });

        // ChipGroup chọn 1 chip (singleSelection), luôn có 1 chip được chọn (selectionRequired)
        binding.chipGroupCategory.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) {
                return;
            }
            applyChip(checkedIds.get(0));
        });

        // Tiêu đề danh sách đổi theo bộ lọc
        viewModel.getFilter().observe(this, this::showHeading);
    }

    /** Chip → bộ lọc. R.id không phải hằng số (AGP 8+) nên dùng if/else, không dùng switch. */
    private void applyChip(int chipId) {
        if (chipId == R.id.chipFavorite) {
            viewModel.setChipFilter("", true, 0);
        } else if (chipId == R.id.chipQuick) {
            viewModel.setChipFilter("", false, MainViewModel.QUICK_MAX_MINUTES);
        } else if (chipId == R.id.chipMain) {
            viewModel.setChipFilter(Recipe.CATEGORY_MAIN, false, 0);
        } else if (chipId == R.id.chipSoup) {
            viewModel.setChipFilter(Recipe.CATEGORY_SOUP, false, 0);
        } else if (chipId == R.id.chipStirFry) {
            viewModel.setChipFilter(Recipe.CATEGORY_STIR_FRY, false, 0);
        } else if (chipId == R.id.chipDessert) {
            viewModel.setChipFilter(Recipe.CATEGORY_DESSERT, false, 0);
        } else if (chipId == R.id.chipDrink) {
            viewModel.setChipFilter(Recipe.CATEGORY_DRINK, false, 0);
        } else {
            // chipAll
            viewModel.setChipFilter("", false, 0);
        }
    }

    /** Tiêu đề: đang tìm → "Kết quả cho …"; không thì theo chip đang chọn. */
    private void showHeading(MainViewModel.RecipeFilter filter) {
        if (!filter.keyword.isEmpty()) {
            binding.textHeading.setText(getString(R.string.format_search_heading, filter.keyword));
        } else if (filter.favoriteOnly) {
            binding.textHeading.setText(R.string.main_heading_favorite);
        } else if (filter.maxMinutes > 0) {
            binding.textHeading.setText(R.string.main_heading_quick);
        } else if (!filter.category.isEmpty()) {
            binding.textHeading.setText(CategoryNames.of(this, filter.category));
        } else {
            binding.textHeading.setText(R.string.main_heading_all);
        }
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(binding.editSearch.getWindowToken(), 0);
        }
        binding.editSearch.clearFocus();
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

        // Danh sách trống vì đang tìm từ khoá → báo "không tìm thấy", còn lại → "chưa có món ở mục này"
        MainViewModel.RecipeFilter filter = viewModel.getFilter().getValue();
        boolean searching = filter != null && !filter.keyword.isEmpty();
        binding.textEmpty.setText(searching ? R.string.main_no_result : R.string.main_empty);
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

    /** CN05: nhấn giữ thẻ → hỏi xác nhận → xoá. Thẻ tự biến mất vì Room báo danh sách mới. */
    @Override
    public void onRecipeLongClick(Recipe recipe) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.dialog_delete_title)
                .setMessage(getString(R.string.format_delete_message, recipe.name))
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_delete, (dialog, which) -> {
                    viewModel.deleteRecipe(recipe);
                    showToast(R.string.msg_recipe_deleted);
                })
                .show();
    }
}
