package com.sobep.ui.account;

import android.os.Bundle;

import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.sobep.R;
import com.sobep.databinding.ActivityAccountBinding;
import com.sobep.ui.BaseActivity;

/**
 * Màn 12 · Tài khoản. Mở từ màn chính khi bấm nút Tài khoản mà ĐÃ đăng nhập.
 * Hiện email đang đăng nhập, nút Đồng bộ (CN14 – bước 8b, tạm báo "sắp có") và Đăng xuất.
 */
public class AccountActivity extends BaseActivity {

    private ActivityAccountBinding binding;
    private AccountViewModel viewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewModel = new ViewModelProvider(this).get(AccountViewModel.class);

        // Phòng trường hợp phiên đăng nhập đã hết (vd tài khoản bị xoá trên Console) → không có gì để hiện
        if (!viewModel.isSignedIn()) {
            finish();
            return;
        }

        binding = ActivityAccountBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        applySystemBarInsets(binding.getRoot());

        binding.textEmail.setText(viewModel.getEmail());
        binding.buttonBack.setOnClickListener(v -> finish());
        // CN14 đồng bộ Firestore làm ở bước 8b
        binding.buttonSync.setOnClickListener(v -> showComingSoon());
        binding.buttonSignOut.setOnClickListener(v -> confirmSignOut());
    }

    /** Hỏi lại trước khi đăng xuất; nói rõ món trong máy không mất. */
    private void confirmSignOut() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.dialog_sign_out_title)
                .setMessage(R.string.dialog_sign_out_message)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_sign_out, (dialog, which) -> {
                    viewModel.signOut();
                    showToast(R.string.msg_signed_out);
                    finish();
                })
                .show();
    }
}
