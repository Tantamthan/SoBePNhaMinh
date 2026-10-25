package com.sobep.ui.scan;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import com.sobep.R;
import com.sobep.databinding.ActivityScanRecipeBinding;
import com.sobep.ui.BaseActivity;
import com.sobep.util.IntentKeys;

/**
 * Màn 6 · Quét công thức. Được AddEditRecipe mở để lấy kết quả.
 * Bấm "Dùng kết quả này" thì gói đoạn chữ vào Intent, setResult(RESULT_OK) rồi đóng màn.
 * Chưa làm: chụp ảnh / nhận dạng chữ thật, hiện người dùng tự gõ chữ vào ô.
 */
public class ScanRecipeActivity extends BaseActivity {

    private ActivityScanRecipeBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityScanRecipeBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        applySystemBarInsets(binding.getRoot());

        binding.buttonBack.setOnClickListener(v -> finish());
        binding.buttonRetake.setOnClickListener(v -> showComingSoon());
        binding.buttonPickOther.setOnClickListener(v -> showComingSoon());
        binding.buttonUseResult.setOnClickListener(v -> returnScannedText());
    }

    private void returnScannedText() {
        String text = binding.editScannedText.getText().toString().trim();
        if (text.isEmpty()) {
            showToast(R.string.msg_scan_empty);
            return;
        }
        Intent data = new Intent();
        data.putExtra(IntentKeys.EXTRA_SCANNED_TEXT, text);
        setResult(Activity.RESULT_OK, data);
        finish();
    }
}
