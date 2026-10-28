package com.sobep.ui.cooking;

import android.app.Application;
import android.os.CountDownTimer;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import com.sobep.data.local.entity.Recipe;
import com.sobep.data.local.entity.Step;
import com.sobep.data.repository.RecipeRepository;

import java.util.List;

/**
 * ViewModel màn Chế độ nấu: đọc món + các bước, giữ bước đang nấu và bộ đếm ngược của bước đó.
 * Bộ đếm (CountDownTimer) nằm ở ViewModel nên xoay màn hình không bị đếm lại từ đầu.
 */
public class CookingModeViewModel extends AndroidViewModel {

    /** Trạng thái hẹn giờ: chưa chạy, đang đếm, tạm dừng, hết giờ. */
    public enum TimerState { IDLE, RUNNING, PAUSED, FINISHED }

    private static final long ONE_SECOND_MS = 1000L;
    private static final long ONE_MINUTE_MS = 60_000L;

    private final RecipeRepository repository;
    private final MutableLiveData<Long> recipeId = new MutableLiveData<>();
    private final LiveData<Recipe> recipe;
    private final LiveData<List<Step>> steps;

    // Vị trí bước đang nấu (0 = bước 1)
    private final MutableLiveData<Integer> stepIndex = new MutableLiveData<>(0);
    // Bước đang nấu — kết hợp danh sách bước và vị trí
    private final MediatorLiveData<Step> currentStep = new MediatorLiveData<>();

    private final MutableLiveData<TimerState> timerState = new MutableLiveData<>(TimerState.IDLE);
    private final MutableLiveData<Long> remainingMillis = new MutableLiveData<>(0L);
    // Sự kiện một lần: true khi vừa hết giờ, Activity phát chuông xong thì gọi consumeTimerFinished()
    private final MutableLiveData<Boolean> timerFinishedEvent = new MutableLiveData<>(false);

    private CountDownTimer countDownTimer;
    private long totalMillis;
    // Mã bước mà bộ đếm đang dùng; đổi bước thì đặt lại bộ đếm
    private long timerStepId = -1;

    public CookingModeViewModel(@NonNull Application application) {
        super(application);
        repository = new RecipeRepository(application);
        recipe = Transformations.switchMap(recipeId, repository::getRecipe);
        steps = Transformations.switchMap(recipeId, repository::getSteps);

        currentStep.addSource(steps, list -> updateCurrentStep());
        currentStep.addSource(stepIndex, index -> updateCurrentStep());
    }

    /** Gọi trong onCreate. Gọi lại với cùng mã (khi xoay màn hình) thì bỏ qua. */
    public void setRecipeId(long id) {
        Long current = recipeId.getValue();
        if (current == null || current != id) {
            recipeId.setValue(id);
        }
    }

    public LiveData<Recipe> getRecipe() {
        return recipe;
    }

    public LiveData<List<Step>> getSteps() {
        return steps;
    }

    public LiveData<Integer> getStepIndex() {
        return stepIndex;
    }

    public LiveData<Step> getCurrentStep() {
        return currentStep;
    }

    public LiveData<TimerState> getTimerState() {
        return timerState;
    }

    public LiveData<Long> getRemainingMillis() {
        return remainingMillis;
    }

    public LiveData<Boolean> getTimerFinishedEvent() {
        return timerFinishedEvent;
    }

    /** Tổng thời gian hẹn của bước hiện tại (mili giây), 0 = bước không hẹn giờ. */
    public long getTotalMillis() {
        return totalMillis;
    }

    // ---------- Chuyển bước ----------

    public void nextStep() {
        List<Step> list = steps.getValue();
        int index = currentIndex();
        if (list != null && index < list.size() - 1) {
            stepIndex.setValue(index + 1);
        }
    }

    public void previousStep() {
        int index = currentIndex();
        if (index > 0) {
            stepIndex.setValue(index - 1);
        }
    }

    private void updateCurrentStep() {
        List<Step> list = steps.getValue();
        if (list == null || list.isEmpty()) {
            currentStep.setValue(null);
            return;
        }
        int index = Math.min(currentIndex(), list.size() - 1);
        Step step = list.get(index);
        currentStep.setValue(step);
        // Sang bước khác → bộ đếm lấy số phút của bước mới
        if (step.id != timerStepId) {
            timerStepId = step.id;
            resetTimer();
        }
    }

    private int currentIndex() {
        Integer index = stepIndex.getValue();
        return index != null ? index : 0;
    }

    // ---------- Hẹn giờ ----------

    /** Nút chính của bộ đếm: Bắt đầu / Tạm dừng / Tiếp tục. Lúc hết giờ nút là "Xong" → gọi completeTimedStep(). */
    public void toggleTimer() {
        TimerState state = timerState.getValue();
        if (state == TimerState.RUNNING) {
            pauseTimer();
        } else if (state != TimerState.FINISHED) {
            startTimer();
        }
    }

    /**
     * Bấm "Xong" khi hết giờ: sang bước tiếp theo (bộ đếm tự đặt lại theo bước mới).
     * @return false nếu đang ở bước cuối → Activity đóng chế độ nấu (giống nút Hoàn thành)
     */
    public boolean completeTimedStep() {
        List<Step> list = steps.getValue();
        if (list == null || currentIndex() >= list.size() - 1) {
            return false;
        }
        nextStep();
        return true;
    }

    public boolean isTimerFinished() {
        return timerState.getValue() == TimerState.FINISHED;
    }

    /** Đặt lại bộ đếm về số phút của bước hiện tại, chưa chạy. */
    public void resetTimer() {
        cancelCountDown();
        Step step = currentStep.getValue();
        totalMillis = step != null ? step.timerMinutes * ONE_MINUTE_MS : 0;
        remainingMillis.setValue(totalMillis);
        timerState.setValue(TimerState.IDLE);
        timerFinishedEvent.setValue(false);
    }

    public void consumeTimerFinished() {
        timerFinishedEvent.setValue(false);
    }

    private void startTimer() {
        Long remaining = remainingMillis.getValue();
        if (remaining == null || remaining <= 0) {
            return;
        }
        // CountDownTimer gọi onTick mỗi giây trên main thread → dùng setValue được
        countDownTimer = new CountDownTimer(remaining, ONE_SECOND_MS) {
            @Override
            public void onTick(long millisUntilFinished) {
                remainingMillis.setValue(millisUntilFinished);
            }

            @Override
            public void onFinish() {
                remainingMillis.setValue(0L);
                timerState.setValue(TimerState.FINISHED);
                timerFinishedEvent.setValue(true);
            }
        }.start();
        timerState.setValue(TimerState.RUNNING);
    }

    private void pauseTimer() {
        // Giữ nguyên remainingMillis, bấm Tiếp tục sẽ đếm tiếp từ đó
        cancelCountDown();
        timerState.setValue(TimerState.PAUSED);
    }

    private void cancelCountDown() {
        if (countDownTimer != null) {
            countDownTimer.cancel();
            countDownTimer = null;
        }
    }

    /** Thoát hẳn màn Chế độ nấu → huỷ bộ đếm, tránh chạy ngầm. */
    @Override
    protected void onCleared() {
        cancelCountDown();
        super.onCleared();
    }
}
