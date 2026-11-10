package com.sobep.ui.account;

import android.os.Bundle;
import android.view.View;

import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.sobep.R;
import com.sobep.data.repository.SyncRepository;
import com.sobep.databinding.ActivityAccountBinding;
import com.sobep.ui.BaseActivity;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Màn 12 · Tài khoản. Mở từ màn chính khi bấm nút Tài khoản mà ĐÃ đăng nhập.
 * Hiện email đang đăng nhập, nút "Đồng bộ ngay" (CN14 – Firestore) và Đăng xuất.
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
        binding.buttonSync.setOnClickListener(v -> viewModel.syncNow());
        binding.buttonSignOut.setOnClickListener(v -> confirmSignOut());

        observeSync();
    }

    private void observeSync() {
        viewModel.isSyncing().observe(this, syncing -> {
            boolean busy = Boolean.TRUE.equals(syncing);
            binding.progressSync.setVisibility(busy ? View.VISIBLE : View.GONE);
            // Đang đồng bộ thì khoá nút Đồng bộ và Đăng xuất (đăng xuất giữa chừng sẽ bị Firestore từ chối)
            binding.buttonSync.setEnabled(!busy);
            binding.buttonSync.setText(busy ? R.string.sync_in_progress : R.string.action_sync_now);
            binding.buttonSignOut.setEnabled(!busy);
        });
        viewModel.getSyncResult().observe(this, result -> showSyncStatus());
        viewModel.getSyncError().observe(this, error -> showSyncStatus());
        viewModel.getLastSyncAt().observe(this, time -> {
            if (time == null || time == 0) {
                binding.textLastSync.setText(R.string.sync_never);
            } else {
                DateFormat format = new SimpleDateFormat("HH:mm, dd/MM/yyyy", Locale.getDefault());
                binding.textLastSync.setText(getString(R.string.format_sync_last, format.format(new Date(time))));
            }
        });
    }

    /** Dòng trạng thái dưới nút: có lỗi thì hiện lỗi (màu cam), không thì hiện kết quả (màu xanh). */
    private void showSyncStatus() {
        Integer error = viewModel.getSyncError().getValue();
        SyncRepository.SyncResult result = viewModel.getSyncResult().getValue();
        if (error != null) {
            binding.textSyncStatus.setVisibility(View.VISIBLE);
            binding.textSyncStatus.setTextColor(ContextCompat.getColor(this, R.color.accent_orange_dark));
            binding.textSyncStatus.setText(error);
        } else if (result != null) {
            binding.textSyncStatus.setVisibility(View.VISIBLE);
            binding.textSyncStatus.setTextColor(ContextCompat.getColor(this, R.color.green_primary));
            binding.textSyncStatus.setText(result.nothingChanged()
                    ? getString(R.string.sync_result_none)
                    : getString(R.string.format_sync_result, result.uploaded, result.downloaded, result.deleted));
        } else {
            binding.textSyncStatus.setVisibility(View.GONE);
        }
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
