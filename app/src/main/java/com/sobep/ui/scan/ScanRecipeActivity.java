package com.sobep.ui.scan;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.ImageView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.sobep.R;
import com.sobep.databinding.ActivityScanRecipeBinding;
import com.sobep.ui.BaseActivity;
import com.sobep.util.ImageStorage;
import com.sobep.util.IntentKeys;

import java.io.IOException;

/**
 * Màn 6 · Quét công thức. Được AddEditRecipe mở để lấy kết quả.
 * - Lần đầu mở: xin quyền CAMERA lúc chạy → mở app Camera (Intent ngầm định) chụp trang công thức.
 * - "Chọn ảnh khác": bộ chọn ảnh của hệ thống, không cần xin quyền.
 * - Có ảnh → ScanRecipeViewModel đọc chữ bằng ML Kit → chữ hiện trong ô để người dùng sửa.
 * - "Dùng kết quả này": gói đoạn chữ vào Intent, setResult(RESULT_OK) rồi đóng màn.
 */
public class ScanRecipeActivity extends BaseActivity {

    // Lưu Uri ảnh đang chờ app Camera chụp, phòng khi hệ thống tạo lại màn này lúc đang ở app Camera
    private static final String STATE_PENDING_PHOTO_URI = "pending_photo_uri";

    private ActivityScanRecipeBinding binding;
    private ScanRecipeViewModel viewModel;

    // File app Camera sẽ ghi ảnh vào (chỉ dùng khi chụp thành công)
    private Uri pendingPhotoUri;

    // 1. Xin quyền CAMERA lúc chạy → hệ thống hiện hộp thoại "Cho phép / Không cho phép"
    private final ActivityResultLauncher<String> cameraPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            granted -> {
                if (granted) {
                    launchCamera();
                } else if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
                    // Mới từ chối 1 lần: lần sau vẫn hỏi lại được
                    showToast(R.string.permission_camera_denied);
                } else {
                    // Từ chối hẳn ("Không hỏi lại"): hệ thống không hiện hộp thoại nữa → chỉ đường vào Cài đặt
                    showPermissionBlockedDialog();
                }
            });

    // 2. Mở app Camera chụp ảnh, ảnh được ghi vào pendingPhotoUri. Kết quả: true = đã chụp
    private final ActivityResultLauncher<Uri> takePictureLauncher = registerForActivityResult(
            new ActivityResultContracts.TakePicture(),
            saved -> {
                if (Boolean.TRUE.equals(saved) && pendingPhotoUri != null) {
                    viewModel.setPhoto(pendingPhotoUri);
                }
                pendingPhotoUri = null;
            });

    // 3. Bộ chọn ảnh của hệ thống (Photo Picker). Không cần quyền đọc bộ nhớ. Kết quả: Uri hoặc null nếu huỷ
    private final ActivityResultLauncher<PickVisualMediaRequest> pickImageLauncher = registerForActivityResult(
            new ActivityResultContracts.PickVisualMedia(),
            uri -> {
                if (uri != null) {
                    viewModel.setPhoto(uri);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityScanRecipeBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        applySystemBarInsets(binding.getRoot());

        viewModel = new ViewModelProvider(this).get(ScanRecipeViewModel.class);

        binding.buttonBack.setOnClickListener(v -> finish());
        binding.buttonRetake.setOnClickListener(v -> takePhotoWithPermission());
        binding.buttonPickOther.setOnClickListener(v -> pickImageLauncher.launch(
                new PickVisualMediaRequest.Builder()
                        .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                        .build()));
        binding.buttonUseResult.setOnClickListener(v -> returnScannedText());

        observeViewModel();

        if (savedInstanceState == null) {
            // Mở màn lần đầu: vào thẳng bước chụp ảnh cho nhanh
            takePhotoWithPermission();
        } else {
            String pending = savedInstanceState.getString(STATE_PENDING_PHOTO_URI);
            pendingPhotoUri = pending != null ? Uri.parse(pending) : null;
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (pendingPhotoUri != null) {
            outState.putString(STATE_PENDING_PHOTO_URI, pendingPhotoUri.toString());
        }
    }

    private void observeViewModel() {
        viewModel.getPreview().observe(this, this::showPreview);
        viewModel.getStatus().observe(this, this::showStatus);
        viewModel.getRecognizedText().observe(this, text -> {
            // Chỉ điền một lần cho mỗi ảnh. Xoay màn hình: ô nhập tự giữ chữ người dùng đã sửa
            if (text != null && !viewModel.isTextShown()) {
                binding.editScannedText.setText(text);
                viewModel.markTextShown();
            }
        });
    }

    // ---------- Quyền CAMERA + chụp ảnh ----------

    /** Đã có quyền → chụp luôn; chưa có → giải thích (nếu cần) rồi xin quyền. */
    private void takePhotoWithPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            launchCamera();
        } else if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
            // Người dùng từng từ chối → nói rõ vì sao cần quyền trước khi hỏi lại
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.permission_camera_title)
                    .setMessage(R.string.permission_camera_rationale)
                    .setPositiveButton(R.string.action_allow,
                            (dialog, which) -> cameraPermissionLauncher.launch(Manifest.permission.CAMERA))
                    .setNegativeButton(R.string.action_not_now, null)
                    .show();
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    /** Tạo file ảnh tạm rồi mở app Camera (Intent ngầm định ACTION_IMAGE_CAPTURE bên trong TakePicture). */
    private void launchCamera() {
        try {
            pendingPhotoUri = ImageStorage.createCameraPhotoUri(this);
            takePictureLauncher.launch(pendingPhotoUri);
        } catch (IOException e) {
            pendingPhotoUri = null;
            showToast(R.string.scan_error);
        } catch (ActivityNotFoundException e) {
            pendingPhotoUri = null;
            showToast(R.string.msg_no_camera_app);
        }
    }

    /** Quyền đã bị tắt hẳn: mở trang thông tin app trong Cài đặt (Intent ngầm định). */
    private void showPermissionBlockedDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.permission_camera_title)
                .setMessage(R.string.permission_camera_blocked)
                .setPositiveButton(R.string.action_open_settings, (dialog, which) -> {
                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", getPackageName(), null));
                    startActivity(intent);
                })
                .setNegativeButton(R.string.action_not_now, null)
                .show();
    }

    // ---------- Hiển thị ----------

    private void showPreview(Bitmap bitmap) {
        if (bitmap == null) {
            return;
        }
        // Bỏ kiểu icon giữ chỗ (căn giữa, tô xám) → hiện trọn ảnh trong khung để thấy cả trang công thức
        binding.imagePhoto.setImageTintList(null);
        binding.imagePhoto.setScaleType(ImageView.ScaleType.FIT_CENTER);
        binding.imagePhoto.setImageBitmap(bitmap);
        binding.textPhotoBadge.setVisibility(View.VISIBLE);
    }

    private void showStatus(ScanRecipeViewModel.Status status) {
        boolean reading = status == ScanRecipeViewModel.Status.READING;
        boolean warning = status == ScanRecipeViewModel.Status.NO_TEXT
                || status == ScanRecipeViewModel.Status.ERROR;

        binding.progressReading.setVisibility(reading ? View.VISIBLE : View.GONE);
        binding.imageStatusDone.setVisibility(
                status == ScanRecipeViewModel.Status.DONE ? View.VISIBLE : View.GONE);
        binding.layoutStatus.setBackgroundResource(
                warning ? R.drawable.bg_status_warning : R.drawable.bg_status_success);
        binding.textStatus.setTextColor(ContextCompat.getColor(this,
                warning ? R.color.timer_text : R.color.green_dark));
        binding.textStatus.setText(statusText(status));

        // Đang đọc thì chưa cho dùng kết quả
        binding.buttonUseResult.setEnabled(!reading);
        // Đã có ảnh → nút đổi thành "Chụp lại" / "Chọn ảnh khác"
        boolean hasPhoto = viewModel.hasPhoto();
        binding.buttonRetake.setText(hasPhoto ? R.string.action_retake : R.string.action_take_photo);
        binding.buttonPickOther.setText(hasPhoto ? R.string.action_pick_other_photo : R.string.action_pick_photo);
    }

    private static int statusText(ScanRecipeViewModel.Status status) {
        if (status == ScanRecipeViewModel.Status.READING) {
            return R.string.scan_reading;
        } else if (status == ScanRecipeViewModel.Status.DONE) {
            return R.string.scan_done;
        } else if (status == ScanRecipeViewModel.Status.NO_TEXT) {
            return R.string.scan_no_text;
        } else if (status == ScanRecipeViewModel.Status.ERROR) {
            return R.string.scan_error;
        }
        return R.string.scan_idle;
    }

    // ---------- Trả kết quả ----------

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
