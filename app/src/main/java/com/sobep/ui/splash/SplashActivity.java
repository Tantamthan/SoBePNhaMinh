package com.sobep.ui.splash;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import com.sobep.databinding.ActivitySplashBinding;
import com.sobep.ui.BaseActivity;
import com.sobep.ui.main.MainActivity;

/**
 * Màn 1 · Splash: hiện logo khoảng 1,2 giây rồi mở màn Sổ công thức.
 * Gọi finish() để bấm Back ở màn chính sẽ thoát app, không quay lại Splash.
 */
public class SplashActivity extends BaseActivity {

    private static final long SPLASH_DELAY_MS = 1200L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable openMainScreen = () -> {
        // Intent tường minh: chỉ rõ Activity cần mở là MainActivity
        startActivity(new Intent(this, MainActivity.class));
        finish();
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActivitySplashBinding binding = ActivitySplashBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        applySystemBarInsets(binding.getRoot());

        handler.postDelayed(openMainScreen, SPLASH_DELAY_MS);
    }

    @Override
    protected boolean hasDarkBackground() {
        return true;
    }

    @Override
    protected void onDestroy() {
        // Người dùng thoát giữa chừng thì huỷ lệnh mở màn chính, tránh app tự bật lại
        handler.removeCallbacks(openMainScreen);
        super.onDestroy();
    }
}
