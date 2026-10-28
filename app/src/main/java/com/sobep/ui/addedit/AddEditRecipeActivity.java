package com.sobep.ui.addedit;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.EditText;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;

import com.sobep.R;
import com.sobep.data.local.entity.Ingredient;
import com.sobep.data.local.entity.Recipe;
import com.sobep.data.local.entity.Step;
import com.sobep.databinding.ActivityAddEditRecipeBinding;
import com.sobep.ui.BaseActivity;
import com.sobep.ui.scan.ScanRecipeActivity;
import com.sobep.util.IntentKeys;

/**
 * Màn 5 · Thêm / Sửa món (CN03, CN04). Một màn dùng cho 2 chế độ:
 * - Intent KHÔNG có mã món → Thêm mới.
 * - Intent CÓ mã món → Sửa, điền sẵn thông tin món đọc từ CSDL.
 * Bấm Lưu → AddEditRecipeViewModel ghi vào Room → trả RESULT_OK về màn đã mở nó.
 * Chưa làm: chọn / chụp ảnh món (bước 7).
 */
public class AddEditRecipeActivity extends BaseActivity {

    // Khẩu phần hợp lệ, khớp với giới hạn nút +/- ở màn Chi tiết món
    private static final int MIN_SERVINGS = 1;
    private static final int MAX_SERVINGS = 20;

    private ActivityAddEditRecipeBinding binding;
    private AddEditRecipeViewModel viewModel;
    private IngredientEditAdapter ingredientAdapter;
    private StepEditAdapter stepAdapter;

    // Mở màn Quét công thức và nhận lại đoạn chữ quét được
    private final ActivityResultLauncher<Intent> scanLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    String scannedText = result.getData().getStringExtra(IntentKeys.EXTRA_SCANNED_TEXT);
                    fillFromScannedText(scannedText);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAddEditRecipeBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        applySystemBarInsets(binding.getRoot());

        long recipeId = getIntent().getLongExtra(IntentKeys.EXTRA_RECIPE_ID, IntentKeys.NO_RECIPE_ID);
        binding.textScreenTitle.setText(recipeId == IntentKeys.NO_RECIPE_ID
                ? R.string.add_recipe_title : R.string.edit_recipe_title);

        viewModel = new ViewModelProvider(this).get(AddEditRecipeViewModel.class);
        // Thêm → món trống; Sửa → ViewModel đọc món từ CSDL (gọi lại khi xoay màn hình thì bỏ qua)
        viewModel.start(recipeId);

        setupLists();
        setupActions();
        observeViewModel();
    }

    private void setupLists() {
        // LinearLayoutManager đã khai báo trong activity_add_edit_recipe.xml
        ingredientAdapter = new IngredientEditAdapter(position -> viewModel.removeIngredient(position));
        binding.recyclerIngredients.setAdapter(ingredientAdapter);
        stepAdapter = new StepEditAdapter(position -> viewModel.removeStep(position));
        binding.recyclerSteps.setAdapter(stepAdapter);
    }

    private void observeViewModel() {
        viewModel.getRecipe().observe(this, recipe -> {
            // Chỉ điền form lần đầu. Xoay màn hình: các ô tự giữ chữ đang gõ, không điền đè
            if (recipe != null && !viewModel.isFormFilled()) {
                fillForm(recipe);
                viewModel.markFormFilled();
            }
        });
        viewModel.getIngredients().observe(this, ingredientAdapter::setItems);
        viewModel.getSteps().observe(this, stepAdapter::setItems);

        viewModel.getNotFound().observe(this, notFound -> {
            if (Boolean.TRUE.equals(notFound)) {
                showToast(R.string.msg_recipe_not_found);
                finish();
            }
        });
        // Lưu xong → báo màn trước (Main hoặc Chi tiết) rồi đóng
        viewModel.getSavedRecipeId().observe(this, savedId -> {
            if (savedId != null) {
                setResult(Activity.RESULT_OK);
                finish();
            }
        });
    }

    /** Điền thông tin món vào các ô nhập (chế độ Sửa) hoặc giá trị mặc định (chế độ Thêm). */
    private void fillForm(Recipe recipe) {
        binding.editName.setText(recipe.name);
        binding.spinnerCategory.setSelection(getCategoryIndex(recipe.category));
        binding.editCookTime.setText(recipe.cookTimeMinutes > 0 ? String.valueOf(recipe.cookTimeMinutes) : "");
        binding.editServings.setText(String.valueOf(recipe.servings));
        binding.editVideoUrl.setText(recipe.videoUrl);
        binding.editNote.setText(recipe.note);
    }

    /** Mã danh mục (vd "soup") → vị trí trong Spinner (Spinner dùng string-array recipe_categories cùng thứ tự). */
    private int getCategoryIndex(String category) {
        for (int i = 0; i < Recipe.CATEGORIES.length; i++) {
            if (Recipe.CATEGORIES[i].equals(category)) {
                return i;
            }
        }
        return 0;
    }

    /** Tạm thời lấy dòng đầu tiên của chữ quét được làm tên món (sau này thay bằng RecipeTextParser). */
    private void fillFromScannedText(String scannedText) {
        if (TextUtils.isEmpty(scannedText)) {
            return;
        }
        String firstLine = scannedText.trim().split("\n")[0].trim();
        binding.editName.setText(firstLine);
        showToast(R.string.msg_scan_filled);
    }

    private void setupActions() {
        binding.buttonBack.setOnClickListener(v -> finish());
        binding.buttonSaveTop.setOnClickListener(v -> saveRecipe());
        binding.buttonSave.setOnClickListener(v -> saveRecipe());

        binding.cardScan.setOnClickListener(v ->
                scanLauncher.launch(new Intent(this, ScanRecipeActivity.class)));

        binding.buttonPickPhoto.setOnClickListener(v -> showComingSoon());
        binding.buttonTakePhoto.setOnClickListener(v -> showComingSoon());

        // Thêm một dòng trống vào bản nháp rồi đặt con trỏ vào ô đầu của dòng mới
        binding.buttonAddIngredient.setOnClickListener(v -> {
            viewModel.addIngredient(new Ingredient());
            focusLastRow(binding.recyclerIngredients);
        });
        binding.buttonAddStep.setOnClickListener(v -> {
            viewModel.addStep(new Step());
            focusLastRow(binding.recyclerSteps);
        });
    }

    /** Đợi RecyclerView vẽ xong dòng mới (post) rồi đặt con trỏ vào ô nhập đầu tiên của dòng đó. */
    private void focusLastRow(RecyclerView recyclerView) {
        recyclerView.post(() -> {
            RecyclerView.Adapter<?> adapter = recyclerView.getAdapter();
            if (adapter == null || adapter.getItemCount() == 0) {
                return;
            }
            RecyclerView.ViewHolder holder =
                    recyclerView.findViewHolderForAdapterPosition(adapter.getItemCount() - 1);
            if (holder instanceof IngredientEditAdapter.IngredientEditViewHolder) {
                ((IngredientEditAdapter.IngredientEditViewHolder) holder).binding.editName.requestFocus();
            } else if (holder instanceof StepEditAdapter.StepEditViewHolder) {
                ((StepEditAdapter.StepEditViewHolder) holder).binding.editContent.requestFocus();
            }
        });
    }

    private void saveRecipe() {
        String name = textOf(binding.editName);
        if (name.isEmpty()) {
            binding.editName.setError(getString(R.string.error_recipe_name_required));
            binding.editName.requestFocus();
            return;
        }
        int servings = parseInt(textOf(binding.editServings));
        if (servings < MIN_SERVINGS || servings > MAX_SERVINGS) {
            binding.editServings.setError(getString(R.string.error_servings_range, MIN_SERVINGS, MAX_SERVINGS));
            binding.editServings.requestFocus();
            return;
        }

        // Món gốc trong ViewModel (giữ id, yêu thích, ngày tạo...); chưa đọc xong thì chưa cho lưu
        Recipe recipe = viewModel.getRecipe().getValue();
        if (recipe == null) {
            return;
        }
        // Chép dữ liệu form vào món
        recipe.name = name;
        recipe.category = Recipe.CATEGORIES[binding.spinnerCategory.getSelectedItemPosition()];
        recipe.cookTimeMinutes = parseInt(textOf(binding.editCookTime));
        recipe.servings = servings;
        String videoUrl = textOf(binding.editVideoUrl);
        recipe.videoUrl = videoUrl.isEmpty() ? null : videoUrl;
        String note = textOf(binding.editNote);
        recipe.note = note.isEmpty() ? null : note;

        // Nguyên liệu và các bước đã nằm sẵn trong bản nháp của ViewModel (TextWatcher ghi vào khi gõ)
        viewModel.save(recipe);
    }

    private static String textOf(EditText editText) {
        return editText.getText() == null ? "" : editText.getText().toString().trim();
    }

    /** Ô trống hoặc gõ sai → 0. */
    private static int parseInt(String text) {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
