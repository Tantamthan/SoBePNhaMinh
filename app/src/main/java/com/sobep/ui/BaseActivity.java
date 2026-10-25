package com.sobep.ui;

import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.SystemBarStyle;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.sobep.R;

/**
 * Lớp cha của mọi Activity trong app. Gom các việc lặp lại ở màn hình nào cũng cần:
 * - Bật chế độ tràn viền (edge-to-edge) và chừa chỗ cho thanh trạng thái / thanh điều hướng / bàn phím.
 * - Hàm hiện thông báo ngắn (Toast).
 */
public abstract class BaseActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (hasDarkBackground()) {
            // Nền tối (vd Splash màu xanh) → icon trên thanh hệ thống màu trắng
            EdgeToEdge.enable(this,
                    SystemBarStyle.dark(Color.TRANSPARENT),
                    SystemBarStyle.dark(Color.TRANSPARENT));
        } else {
            EdgeToEdge.enable(this);
        }
    }

    /** Màn hình có nền tối thì ghi đè trả về true. */
    protected boolean hasDarkBackground() {
        return false;
    }

    /**
     * Thêm khoảng đệm cho view gốc bằng đúng kích thước thanh hệ thống (và bàn phím khi đang mở),
     * để nội dung không bị che. Gọi ngay sau setContentView.
     */
    protected void applySystemBarInsets(View root) {
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, windowInsets) -> {
            Insets bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout()
                    | WindowInsetsCompat.Type.ime());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return WindowInsetsCompat.CONSUMED;
        });
    }

    protected void showToast(@StringRes int messageRes) {
        Toast.makeText(this, messageRes, Toast.LENGTH_SHORT).show();
    }

    /** Dùng cho các nút có trên giao diện nhưng tính năng chưa làm. */
    protected void showComingSoon() {
        showToast(R.string.msg_coming_soon);
    }
}
