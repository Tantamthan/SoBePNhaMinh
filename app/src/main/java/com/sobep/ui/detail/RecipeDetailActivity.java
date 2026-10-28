package com.sobep.ui.detail;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.lifecycle.ViewModelProvider;

import com.sobep.R;
import com.sobep.data.local.entity.Recipe;
import com.sobep.databinding.ActivityRecipeDetailBinding;
import com.sobep.ui.BaseActivity;
import com.sobep.ui.addedit.AddEditRecipeActivity;
import com.sobep.ui.cooking.CookingModeActivity;
import com.sobep.util.IntentKeys;

import java.io.File;

/**
 * Màn 3 · Chi tiết món (CN02). Nhận mã món qua Intent, đọc món + nguyên liệu + các bước
 * từ Room qua RecipeDetailViewModel, quy đổi định lượng theo số người đang chọn.
 * Minh hoạ đủ các loại Intent:
 * - Tường minh: mở Chế độ nấu, mở màn Sửa món (chờ kết quả trả về).
 * - Ngầm định: Chia sẻ (ACTION_SEND), mở Video (ACTION_VIEW) bằng app khác trên máy.
 */
public class RecipeDetailActivity extends BaseActivity {

    // Món chưa lưu link video riêng thì tìm tên món trên YouTube
    private static final String VIDEO_SEARCH_URL = "https://www.youtube.com/results?search_query=";

    private ActivityRecipeDetailBinding binding;
    private RecipeDetailViewModel viewModel;
    private IngredientAdapter ingredientAdapter;
    private StepAdapter stepAdapter;
    private long recipeId;

    // Món đang hiển thị (null khi Room chưa đọc xong) và số người đang chọn
    private Recipe currentRecipe;
    private int currentServings;

    // Mở màn Sửa món và chờ kết quả. Không cần tự đọc lại: Room báo thay đổi qua LiveData
    private final ActivityResultLauncher<Intent> editRecipeLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK) {
                    showToast(R.string.msg_recipe_updated);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityRecipeDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        applySystemBarInsets(binding.getRoot());

        // Đọc mã món mà màn trước gửi sang
        recipeId = getIntent().getLongExtra(IntentKeys.EXTRA_RECIPE_ID, IntentKeys.NO_RECIPE_ID);
        if (recipeId == IntentKeys.NO_RECIPE_ID) {
            showToast(R.string.msg_recipe_not_found);
            finish();
            return;
        }

        viewModel = new ViewModelProvider(this).get(RecipeDetailViewModel.class);
        viewModel.setRecipeId(recipeId);

        setupLists();
        setupActions();
        observeViewModel();
    }

    private void setupLists() {
        // LinearLayoutManager đã khai báo trong activity_recipe_detail.xml
        ingredientAdapter = new IngredientAdapter();
        binding.recyclerIngredients.setAdapter(ingredientAdapter);
        stepAdapter = new StepAdapter();
        binding.recyclerSteps.setAdapter(stepAdapter);
    }

    private void observeViewModel() {
        viewModel.getRecipe().observe(this, recipe -> {
            if (recipe == null) {
                // Mã món không có trong CSDL
                showToast(R.string.msg_recipe_not_found);
                finish();
                return;
            }
            currentRecipe = recipe;
            showRecipe(recipe);
        });
        viewModel.getServings().observe(this, servings -> {
            if (servings != null) {
                currentServings = servings;
                showServings();
            }
        });
        // Nguyên liệu đã nhân theo số người; các bước lấy nguyên từ CSDL
        viewModel.getIngredients().observe(this, ingredientAdapter::submitList);
        viewModel.getSteps().observe(this, stepAdapter::submitList);
    }

    /** Điền thông tin chung của món lên màn hình. */
    private void showRecipe(Recipe recipe) {
        binding.textTitle.setText(recipe.name);
        binding.textCategory.setText(getCategoryName(recipe.category));
        binding.textTime.setText(getString(R.string.format_minutes, recipe.cookTimeMinutes));
        binding.textBaseServings.setText(getString(R.string.format_recipe_for_people, recipe.servings));

        binding.buttonFavorite.setImageResource(recipe.favorite
                ? R.drawable.ic_heart_filled : R.drawable.ic_heart_outline);
        binding.buttonFavorite.setContentDescription(getString(recipe.favorite
                ? R.string.cd_remove_favorite : R.string.cd_add_favorite));

        // Có ảnh thì hiện ảnh, chưa có thì giữ icon bát
        if (recipe.imagePath != null && new File(recipe.imagePath).exists()) {
            binding.imagePhoto.setImageURI(Uri.fromFile(new File(recipe.imagePath)));
            binding.imagePhoto.setVisibility(View.VISIBLE);
            binding.imagePlaceholder.setVisibility(View.GONE);
        } else {
            binding.imagePhoto.setVisibility(View.GONE);
            binding.imagePlaceholder.setVisibility(View.VISIBLE);
        }

        // Ghi chú trống thì ẩn cả khung ghi chú
        boolean hasNote = recipe.note != null && !recipe.note.trim().isEmpty();
        binding.layoutNote.setVisibility(hasNote ? View.VISIBLE : View.GONE);
        binding.textNote.setText(hasNote ? recipe.note.trim() : "");
    }

    private void showServings() {
        binding.textServings.setText(getString(R.string.format_people, currentServings));
        binding.buttonCookNow.setText(getString(R.string.format_cook_now, currentServings));
        binding.buttonDecrease.setEnabled(currentServings > RecipeDetailViewModel.MIN_SERVINGS);
        binding.buttonIncrease.setEnabled(currentServings < RecipeDetailViewModel.MAX_SERVINGS);
    }

    /** Mã danh mục trong CSDL (vd "soup") → tên hiển thị (vd "Canh"), theo string-array recipe_categories. */
    private String getCategoryName(String category) {
        String[] names = getResources().getStringArray(R.array.recipe_categories);
        for (int i = 0; i < Recipe.CATEGORIES.length; i++) {
            if (Recipe.CATEGORIES[i].equals(category)) {
                return names[i];
            }
        }
        return names[0];
    }

    private void setupActions() {
        binding.buttonBack.setOnClickListener(v -> finish());
        binding.buttonFavorite.setOnClickListener(v -> viewModel.toggleFavorite());

        // Bấm +/- chỉ báo cho ViewModel; ViewModel tính lại nguyên liệu rồi LiveData cập nhật màn hình
        binding.buttonDecrease.setOnClickListener(v -> viewModel.decreaseServings());
        binding.buttonIncrease.setOnClickListener(v -> viewModel.increaseServings());

        binding.buttonShare.setOnClickListener(v -> shareRecipe());
        binding.buttonVideo.setOnClickListener(v -> openVideo());

        // Gửi kèm mã món → AddEditRecipe hiểu là chế độ Sửa
        binding.buttonEdit.setOnClickListener(v -> {
            Intent intent = new Intent(this, AddEditRecipeActivity.class);
            intent.putExtra(IntentKeys.EXTRA_RECIPE_ID, recipeId);
            editRecipeLauncher.launch(intent);
        });

        binding.buttonCookNow.setOnClickListener(v -> {
            Intent intent = new Intent(this, CookingModeActivity.class);
            intent.putExtra(IntentKeys.EXTRA_RECIPE_ID, recipeId);
            startActivity(intent);
        });
    }

    /** Intent ngầm định: không chỉ rõ app nào, hệ thống hiện danh sách app có thể nhận chữ. */
    private void shareRecipe() {
        if (currentRecipe == null) {
            return;
        }
        String text = getString(R.string.format_share_recipe,
                currentRecipe.name, currentRecipe.cookTimeMinutes, currentServings);
        Intent sendIntent = new Intent(Intent.ACTION_SEND);
        sendIntent.setType("text/plain");
        sendIntent.putExtra(Intent.EXTRA_TEXT, text);
        startActivity(Intent.createChooser(sendIntent, getString(R.string.share_chooser_title)));
    }

    /** Intent ngầm định: mở link video của món, chưa có link thì tìm tên món trên YouTube. */
    private void openVideo() {
        if (currentRecipe == null) {
            return;
        }
        String url = currentRecipe.videoUrl;
        if (url == null || url.trim().isEmpty()) {
            url = VIDEO_SEARCH_URL + Uri.encode(currentRecipe.name);
        }
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url.trim())));
        } catch (ActivityNotFoundException e) {
            // Máy không có app nào mở được link
            showToast(R.string.msg_no_app_to_open);
        }
    }
}
