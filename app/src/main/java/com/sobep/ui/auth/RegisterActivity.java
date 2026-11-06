package com.sobep.ui.auth;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.TextView;

import androidx.lifecycle.ViewModelProvider;

import com.sobep.databinding.ActivityRegisterBinding;
import com.sobep.ui.BaseActivity;
import com.sobep.util.IntentKeys;

/**
 * Màn 11 · Đăng ký (CN13). Mở từ màn Đăng nhập.
 *
 * Intent:
 * - Nhận EXTRA_EMAIL (email đã gõ ở màn Đăng nhập) để điền sẵn, đỡ phải gõ lại.
 * - Tạo tài khoản xong → setResult(RESULT_OK) + EXTRA_EMAIL → màn Đăng nhập đóng theo, về màn chính.
 */
public class RegisterActivity extends BaseActivity {

    private ActivityRegisterBinding binding;
    private RegisterViewModel viewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityRegisterBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        applySystemBarInsets(binding.getRoot());

        viewModel = new ViewModelProvider(this).get(RegisterViewModel.class);

        // Chỉ điền sẵn lần đầu mở màn; xoay màn hình thì EditText tự giữ chữ người dùng đã sửa
        if (savedInstanceState == null) {
            String email = getIntent().getStringExtra(IntentKeys.EXTRA_EMAIL);
            if (email != null && !email.isEmpty()) {
                binding.editEmail.setText(email);
                binding.editPassword.requestFocus();
            }
        }

        setupActions();
        observeViewModel();
    }

    private void setupActions() {
        binding.buttonBack.setOnClickListener(v -> finish());
        binding.buttonRegister.setOnClickListener(v -> submit());
        binding.editConfirmPassword.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                submit();
                return true;
            }
            return false;
        });
    }

    private void submit() {
        viewModel.register(binding.editEmail.getText().toString(),
                binding.editPassword.getText().toString(),
                binding.editConfirmPassword.getText().toString());
    }

    private void observeViewModel() {
        viewModel.isLoading().observe(this, loading -> {
            boolean busy = Boolean.TRUE.equals(loading);
            binding.progress.setVisibility(busy ? View.VISIBLE : View.GONE);
            binding.buttonRegister.setEnabled(!busy);
            binding.editEmail.setEnabled(!busy);
            binding.editPassword.setEnabled(!busy);
            binding.editConfirmPassword.setEnabled(!busy);
        });
        viewModel.getEmailError().observe(this, res -> showFieldError(binding.textEmailError, res));
        viewModel.getPasswordError().observe(this, res -> showFieldError(binding.textPasswordError, res));
        viewModel.getConfirmError().observe(this, res -> showFieldError(binding.textConfirmError, res));
        viewModel.getMessage().observe(this, res -> {
            binding.textError.setVisibility(res == null ? View.GONE : View.VISIBLE);
            if (res != null) {
                binding.textError.setText(res);
            }
        });
        viewModel.getSignedInEmail().observe(this, email -> {
            if (email != null) {
                Intent data = new Intent();
                data.putExtra(IntentKeys.EXTRA_EMAIL, email);
                setResult(Activity.RESULT_OK, data);
                finish();
            }
        });
    }

    /** Dòng chữ lỗi dưới ô (giống màn Đăng nhập); 0 = ô hợp lệ → ẩn. */
    private void showFieldError(TextView errorView, Integer res) {
        boolean hasError = res != null && res != 0;
        errorView.setVisibility(hasError ? View.VISIBLE : View.GONE);
        if (hasError) {
            errorView.setText(res);
        }
    }
}
