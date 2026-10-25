package com.sobep.ui.addedit;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import com.sobep.R;
import com.sobep.databinding.ActivityAddEditRecipeBinding;
import com.sobep.ui.BaseActivity;
import com.sobep.ui.scan.ScanRecipeActivity;
import com.sobep.util.IntentKeys;

/**
 * Màn 5 · Thêm / Sửa món. Một màn dùng cho 2 chế độ:
 * - Intent KHÔNG có mã món → Thêm mới.
 * - Intent CÓ mã món → Sửa, điền sẵn thông tin món.
 * Bấm Lưu thì trả RESULT_OK về màn đã mở nó.
 * Chưa làm: điền sẵn món khi Sửa và ghi món vào CSDL (Room).
 */
public class AddEditRecipeActivity extends BaseActivity {

    private ActivityAddEditRecipeBinding binding;

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
        if (recipeId == IntentKeys.NO_RECIPE_ID) {
            binding.textScreenTitle.setText(R.string.add_recipe_title);
        } else {
            binding.textScreenTitle.setText(R.string.edit_recipe_title);
        }

        setupActions();
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
        binding.buttonAddIngredient.setOnClickListener(v -> showComingSoon());
        binding.buttonAddStep.setOnClickListener(v -> showComingSoon());
    }

    private void saveRecipe() {
        String name = binding.editName.getText().toString().trim();
        if (name.isEmpty()) {
            binding.editName.setError(getString(R.string.error_recipe_name_required));
            binding.editName.requestFocus();
            return;
        }
        // Báo cho màn trước biết đã lưu xong rồi đóng màn này
        setResult(Activity.RESULT_OK);
        finish();
    }
}
