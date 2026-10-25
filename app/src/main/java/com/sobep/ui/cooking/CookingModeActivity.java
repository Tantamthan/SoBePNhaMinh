package com.sobep.ui.cooking;

import android.os.Bundle;
import android.view.View;

import com.sobep.R;
import com.sobep.databinding.ActivityCookingModeBinding;
import com.sobep.ui.BaseActivity;
import com.sobep.util.IntentKeys;

/**
 * Màn 4 · Chế độ nấu: hiện từng bước chữ to, bấm Bước trước / Bước tiếp để chuyển.
 * Màn hình luôn sáng nhờ keepScreenOn="true" trong layout.
 * Chưa làm: đọc tên món và các bước từ CSDL (Room), hẹn giờ cho từng bước.
 */
public class CookingModeActivity extends BaseActivity {

    private static final String STATE_STEP_INDEX = "step_index";

    private ActivityCookingModeBinding binding;
    // Chưa có CSDL nên danh sách bước đang trống
    private String[] steps = new String[0];
    private int stepIndex = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCookingModeBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        applySystemBarInsets(binding.getRoot());

        long recipeId = getIntent().getLongExtra(IntentKeys.EXTRA_RECIPE_ID, IntentKeys.NO_RECIPE_ID);
        if (recipeId == IntentKeys.NO_RECIPE_ID) {
            showToast(R.string.msg_recipe_not_found);
            finish();
            return;
        }

        if (savedInstanceState != null) {
            // Giữ đúng bước đang nấu khi xoay màn hình
            stepIndex = savedInstanceState.getInt(STATE_STEP_INDEX, 0);
        }

        // Chưa có hẹn giờ cho từng bước
        binding.layoutTimer.setVisibility(View.GONE);
        binding.textNoTimer.setVisibility(View.VISIBLE);

        binding.buttonExit.setOnClickListener(v -> finish());
        binding.buttonPrevStep.setOnClickListener(v -> {
            if (stepIndex > 0) {
                stepIndex--;
                showStep();
            }
        });
        binding.buttonNextStep.setOnClickListener(v -> {
            if (stepIndex < steps.length - 1) {
                stepIndex++;
                showStep();
            } else {
                // Đang ở bước cuối (hoặc chưa có bước nào): nút là "Hoàn thành" → đóng chế độ nấu
                finish();
            }
        });

        showStep();
    }

    private void showStep() {
        boolean isLastStep = stepIndex >= steps.length - 1;
        binding.buttonPrevStep.setEnabled(stepIndex > 0);
        binding.buttonNextStep.setText(isLastStep ? R.string.step_finish : R.string.step_next);

        if (steps.length == 0) {
            // Chưa có bước nào thì không hiện số bước, tránh truy cập mảng rỗng
            return;
        }
        int stepNumber = stepIndex + 1;
        binding.textStepProgress.setText(getString(R.string.format_step_progress, stepNumber, steps.length));
        binding.textStepNumber.setText(getString(R.string.format_step_number, stepNumber));
        binding.textStepContent.setText(steps[stepIndex]);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_STEP_INDEX, stepIndex);
    }
}
