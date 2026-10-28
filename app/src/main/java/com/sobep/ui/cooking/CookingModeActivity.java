package com.sobep.ui.cooking;

import android.content.res.ColorStateList;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;

import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.lifecycle.ViewModelProvider;

import com.sobep.R;
import com.sobep.data.local.entity.Step;
import com.sobep.databinding.ActivityCookingModeBinding;
import com.sobep.ui.BaseActivity;
import com.sobep.util.IntentKeys;

import java.util.ArrayList;
import java.util.List;

/**
 * Màn 4 · Chế độ nấu: hiện từng bước chữ to, bấm Bước trước / Bước tiếp để chuyển.
 * Bước có số phút thì hiện vòng đếm ngược (Bắt đầu / Tạm dừng / Đặt lại), hết giờ thì phát chuông.
 * Màn hình luôn sáng nhờ keepScreenOn="true" trong layout.
 * Dữ liệu và bộ đếm nằm ở CookingModeViewModel nên xoay màn hình không mất bước đang nấu.
 */
public class CookingModeActivity extends BaseActivity {

    private ActivityCookingModeBinding binding;
    private CookingModeViewModel viewModel;

    // Bản sao danh sách bước để vẽ thanh tiến độ và biết bước cuối
    private List<Step> steps = new ArrayList<>();
    private int stepIndex;
    // Chuông báo hết giờ (âm thanh thông báo mặc định của máy)
    private Ringtone alarm;

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

        viewModel = new ViewModelProvider(this).get(CookingModeViewModel.class);
        viewModel.setRecipeId(recipeId);

        setupActions();
        observeViewModel();
    }

    private void setupActions() {
        binding.buttonExit.setOnClickListener(v -> finish());
        binding.buttonPrevStep.setOnClickListener(v -> {
            stopAlarm();
            viewModel.previousStep();
        });
        binding.buttonNextStep.setOnClickListener(v -> {
            stopAlarm();
            if (stepIndex >= steps.size() - 1) {
                // Đang ở bước cuối (hoặc món chưa có bước): nút là "Hoàn thành" → đóng chế độ nấu
                finish();
            } else {
                viewModel.nextStep();
            }
        });
        binding.buttonTimerToggle.setOnClickListener(v -> {
            stopAlarm();
            if (viewModel.isTimerFinished()) {
                // Hết giờ, nút là "Xong" → sang bước tiếp; bước cuối thì đóng chế độ nấu
                if (!viewModel.completeTimedStep()) {
                    finish();
                }
            } else {
                viewModel.toggleTimer();
            }
        });
        binding.buttonTimerReset.setOnClickListener(v -> {
            stopAlarm();
            viewModel.resetTimer();
        });
    }

    private void observeViewModel() {
        viewModel.getRecipe().observe(this, recipe -> {
            if (recipe == null) {
                showToast(R.string.msg_recipe_not_found);
                finish();
                return;
            }
            binding.textRecipeTitle.setText(recipe.name);
        });
        viewModel.getSteps().observe(this, list -> {
            steps = list != null ? list : new ArrayList<>();
            buildStepSegments();
            showStep();
        });
        viewModel.getStepIndex().observe(this, index -> {
            stepIndex = index != null ? index : 0;
            showStep();
        });
        // Bước đang nấu đổi → có / không có hẹn giờ
        viewModel.getCurrentStep().observe(this, this::showTimerSection);

        viewModel.getTimerState().observe(this, state -> showTimer());
        viewModel.getRemainingMillis().observe(this, millis -> showTimer());
        viewModel.getTimerFinishedEvent().observe(this, finished -> {
            if (Boolean.TRUE.equals(finished)) {
                playAlarm();
                // Đã xử lý → xoá sự kiện, xoay màn hình không phát chuông lại
                viewModel.consumeTimerFinished();
            }
        });
    }

    // ---------- Bước nấu ----------

    private void showStep() {
        boolean hasSteps = !steps.isEmpty();
        int index = Math.min(stepIndex, Math.max(steps.size() - 1, 0));
        boolean isLastStep = index >= steps.size() - 1;

        binding.buttonPrevStep.setEnabled(index > 0);
        binding.buttonNextStep.setText(isLastStep ? R.string.step_finish : R.string.step_next);
        binding.textStepNumber.setVisibility(hasSteps ? View.VISIBLE : View.GONE);

        if (!hasSteps) {
            binding.textStepProgress.setText("");
            binding.textStepContent.setText(R.string.cooking_no_steps);
            return;
        }
        int stepNumber = index + 1;
        binding.textStepProgress.setText(getString(R.string.format_step_progress, stepNumber, steps.size()));
        binding.textStepNumber.setText(getString(R.string.format_step_number, stepNumber));
        binding.textStepContent.setText(steps.get(index).content);
        updateStepSegments(index);
    }

    /** Thanh tiến độ: mỗi bước một đoạn, thêm bằng code vì số bước mỗi món khác nhau. */
    private void buildStepSegments() {
        LinearLayout container = binding.layoutStepSegments;
        container.removeAllViews();
        int gap = getResources().getDimensionPixelSize(R.dimen.step_segment_gap);
        for (int i = 0; i < steps.size(); i++) {
            View segment = new View(this);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.MATCH_PARENT, 1f);
            if (i > 0) {
                params.setMarginStart(gap);
            }
            segment.setLayoutParams(params);
            segment.setBackgroundResource(R.drawable.bg_step_segment);
            container.addView(segment);
        }
    }

    /** Đoạn của bước đã làm và bước đang làm tô xanh, bước chưa làm giữ màu xám của drawable. */
    private void updateStepSegments(int currentIndex) {
        ColorStateList done = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.green_primary));
        LinearLayout container = binding.layoutStepSegments;
        for (int i = 0; i < container.getChildCount(); i++) {
            ViewCompat.setBackgroundTintList(container.getChildAt(i), i <= currentIndex ? done : null);
        }
    }

    // ---------- Hẹn giờ ----------

    private void showTimerSection(Step step) {
        boolean hasTimer = step != null && step.timerMinutes > 0;
        binding.layoutTimer.setVisibility(hasTimer ? View.VISIBLE : View.GONE);
        // Món chưa có bước nào thì không hiện dòng "Bước này không cần hẹn giờ"
        binding.textNoTimer.setVisibility(step != null && !hasTimer ? View.VISIBLE : View.GONE);
        showTimer();
    }

    private void showTimer() {
        CookingModeViewModel.TimerState state = viewModel.getTimerState().getValue();
        Long remaining = viewModel.getRemainingMillis().getValue();
        if (state == null || remaining == null) {
            return;
        }
        long total = viewModel.getTotalMillis();

        // Làm tròn lên: còn 179,4 giây vẫn hiện 03:00 cho đến khi trôi hết giây đầu
        long remainingSeconds = (remaining + 999) / 1000;
        binding.textTimerValue.setText(getString(R.string.format_timer,
                remainingSeconds / 60, remainingSeconds % 60));

        // Vòng tròn chạy dần theo số giây đã trôi qua
        int totalSeconds = (int) (total / 1000);
        binding.progressTimer.setMax(Math.max(totalSeconds, 1));
        binding.progressTimer.setProgress((int) (totalSeconds - remainingSeconds));

        if (state == CookingModeViewModel.TimerState.RUNNING) {
            binding.textTimerHint.setText(R.string.timer_running);
            binding.buttonTimerToggle.setText(R.string.timer_pause);
        } else if (state == CookingModeViewModel.TimerState.PAUSED) {
            binding.textTimerHint.setText(getString(R.string.format_step_timer, totalSeconds / 60));
            binding.buttonTimerToggle.setText(R.string.timer_resume);
        } else if (state == CookingModeViewModel.TimerState.FINISHED) {
            binding.textTimerHint.setText(R.string.timer_finished);
            binding.buttonTimerToggle.setText(R.string.timer_done);
        } else {
            binding.textTimerHint.setText(getString(R.string.format_step_timer, totalSeconds / 60));
            binding.buttonTimerToggle.setText(R.string.timer_start);
        }
        // Chưa chạy thì không có gì để đặt lại
        binding.buttonTimerReset.setEnabled(state != CookingModeViewModel.TimerState.IDLE);
    }

    private void playAlarm() {
        stopAlarm();
        alarm = RingtoneManager.getRingtone(this,
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION));
        if (alarm != null) {
            alarm.play();
        }
    }

    private void stopAlarm() {
        if (alarm != null && alarm.isPlaying()) {
            alarm.stop();
        }
        alarm = null;
    }

    @Override
    protected void onStop() {
        // Rời màn hình thì tắt chuông
        stopAlarm();
        super.onStop();
    }
}
