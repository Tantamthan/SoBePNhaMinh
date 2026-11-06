package com.sobep.ui.auth;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import com.sobep.databinding.ActivityLoginBinding;
import com.sobep.ui.BaseActivity;
import com.sobep.util.IntentKeys;

/**
 * Màn 10 · Đăng nhập (CN13). Mở từ màn chính khi bấm nút Tài khoản mà chưa đăng nhập.
 *
 * Intent:
 * - Bấm "Chưa có tài khoản? Đăng ký" → mở RegisterActivity, chờ kết quả, gửi kèm email đang gõ (EXTRA_EMAIL).
 * - Đăng ký xong (Firebase tự đăng nhập) hoặc đăng nhập xong → trả RESULT_OK + EXTRA_EMAIL về màn chính.
 */
public class LoginActivity extends BaseActivity {

    private ActivityLoginBinding binding;
    private LoginViewModel viewModel;

    // Đăng ký thành công thì tài khoản mới đã được đăng nhập → đóng luôn màn Đăng nhập
    private final ActivityResultLauncher<Intent> registerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK) {
                    Intent data = result.getData();
                    finishSignedIn(data == null ? null : data.getStringExtra(IntentKeys.EXTRA_EMAIL));
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        applySystemBarInsets(binding.getRoot());

        viewModel = new ViewModelProvider(this).get(LoginViewModel.class);

        setupActions();
        observeViewModel();
    }

    private void setupActions() {
        binding.buttonBack.setOnClickListener(v -> finish());
        binding.buttonSignIn.setOnClickListener(v -> submit());
        // Bấm "Xong" trên bàn phím ở ô mật khẩu = bấm Đăng nhập
        binding.editPassword.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                submit();
                return true;
            }
            return false;
        });
        binding.buttonGoRegister.setOnClickListener(v -> {
            Intent intent = new Intent(this, RegisterActivity.class);
            intent.putExtra(IntentKeys.EXTRA_EMAIL, binding.editEmail.getText().toString().trim());
            registerLauncher.launch(intent);
        });
    }

    private void submit() {
        viewModel.signIn(binding.editEmail.getText().toString(), binding.editPassword.getText().toString());
    }

    private void observeViewModel() {
        viewModel.isLoading().observe(this, loading -> {
            boolean busy = Boolean.TRUE.equals(loading);
            binding.progress.setVisibility(busy ? View.VISIBLE : View.GONE);
            // Đang gửi thì khoá ô nhập và nút để không bấm 2 lần
            binding.buttonSignIn.setEnabled(!busy);
            binding.buttonGoRegister.setEnabled(!busy);
            binding.editEmail.setEnabled(!busy);
            binding.editPassword.setEnabled(!busy);
        });
        viewModel.getEmailError().observe(this, res -> showFieldError(binding.textEmailError, res));
        viewModel.getPasswordError().observe(this, res -> showFieldError(binding.textPasswordError, res));
        viewModel.getMessage().observe(this, res -> {
            binding.textError.setVisibility(res == null ? View.GONE : View.VISIBLE);
            if (res != null) {
                binding.textError.setText(res);
            }
        });
        viewModel.getSignedInEmail().observe(this, email -> {
            if (email != null) {
                finishSignedIn(email);
            }
        });
    }

    /**
     * Lỗi hiện luôn bằng dòng chữ dưới ô (không dùng EditText.setError: kiểu đó chỉ hiện dấu "!",
     * phải chạm vào ô mới thấy chữ). 0 = ô hợp lệ → ẩn dòng lỗi.
     */
    private void showFieldError(TextView errorView, Integer res) {
        boolean hasError = res != null && res != 0;
        errorView.setVisibility(hasError ? View.VISIBLE : View.GONE);
        if (hasError) {
            errorView.setText(res);
        }
    }

    /** Trả kết quả về màn chính: đã đăng nhập, kèm email để hiện thông báo. */
    private void finishSignedIn(@Nullable String email) {
        Intent data = new Intent();
        data.putExtra(IntentKeys.EXTRA_EMAIL, email);
        setResult(Activity.RESULT_OK, data);
        finish();
    }
}
