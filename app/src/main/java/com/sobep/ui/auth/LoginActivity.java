package com.sobep.ui.auth;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.credentials.Credential;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.CustomCredential;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.GetCredentialCancellationException;
import androidx.credentials.exceptions.GetCredentialException;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;
import com.sobep.R;
import com.sobep.databinding.ActivityLoginBinding;
import com.sobep.ui.BaseActivity;
import com.sobep.util.IntentKeys;

/**
 * Màn 10 · Đăng nhập (CN13). Mở từ màn chính khi bấm nút Tài khoản mà chưa đăng nhập.
 * Hai cách đăng nhập: email + mật khẩu, hoặc "Tiếp tục với Google".
 *
 * Đăng nhập Google (Credential Manager):
 * 1. Activity mở bảng chọn tài khoản Google của hệ thống (cần Activity vì đây là giao diện).
 * 2. Người dùng chọn tài khoản → nhận về GoogleIdTokenCredential chứa ID token.
 * 3. ID token đưa cho LoginViewModel → AuthRepository → Firebase (signInWithCredential).
 *
 * Intent:
 * - Bấm "Chưa có tài khoản? Đăng ký" → mở RegisterActivity, chờ kết quả, gửi kèm email đang gõ (EXTRA_EMAIL).
 * - Đăng ký xong (Firebase tự đăng nhập) hoặc đăng nhập xong → trả RESULT_OK + EXTRA_EMAIL về màn chính.
 */
public class LoginActivity extends BaseActivity {

    private static final String TAG = "LoginActivity";

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
        binding.buttonGoogle.setOnClickListener(v -> startGoogleSignIn());
        binding.buttonGoRegister.setOnClickListener(v -> {
            Intent intent = new Intent(this, RegisterActivity.class);
            intent.putExtra(IntentKeys.EXTRA_EMAIL, binding.editEmail.getText().toString().trim());
            registerLauncher.launch(intent);
        });
    }

    private void submit() {
        viewModel.signIn(binding.editEmail.getText().toString(), binding.editPassword.getText().toString());
    }

    /**
     * Mở bảng chọn tài khoản Google. GetSignInWithGoogleOption = kiểu nút "Đăng nhập với Google":
     * luôn hiện danh sách tài khoản trong máy (kể cả tài khoản chưa từng dùng với app) và cho thêm tài khoản mới.
     * default_web_client_id do plugin google-services sinh ra từ google-services.json.
     */
    private void startGoogleSignIn() {
        if (!viewModel.beginGoogleSignIn()) {
            return;
        }
        GetSignInWithGoogleOption googleOption =
                new GetSignInWithGoogleOption.Builder(getString(R.string.default_web_client_id)).build();
        GetCredentialRequest request = new GetCredentialRequest.Builder()
                .addCredentialOption(googleOption)
                .build();

        CredentialManager.create(this).getCredentialAsync(
                this,
                request,
                null,
                ContextCompat.getMainExecutor(this),
                new CredentialManagerCallback<GetCredentialResponse, GetCredentialException>() {
                    @Override
                    public void onResult(GetCredentialResponse response) {
                        handleGoogleCredential(response.getCredential());
                    }

                    @Override
                    public void onError(@NonNull GetCredentialException e) {
                        if (e instanceof GetCredentialCancellationException) {
                            // Người dùng bấm ra ngoài / bấm Back: không báo lỗi
                            viewModel.onGoogleCancelled();
                        } else {
                            Log.w(TAG, "Không lấy được tài khoản Google", e);
                            viewModel.onGoogleError(AuthFormHelper.messageForGoogle(e));
                        }
                    }
                });
    }

    /** Lấy ID token từ kết quả Credential Manager rồi giao cho ViewModel. */
    private void handleGoogleCredential(Credential credential) {
        if (credential instanceof CustomCredential
                && GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL.equals(credential.getType())) {
            GoogleIdTokenCredential googleCredential = GoogleIdTokenCredential.createFrom(credential.getData());
            viewModel.signInWithGoogle(googleCredential.getIdToken());
        } else {
            Log.w(TAG, "Kiểu credential không mong đợi: " + credential.getType());
            viewModel.onGoogleError(R.string.error_google_failed);
        }
    }

    private void observeViewModel() {
        viewModel.isLoading().observe(this, loading -> {
            boolean busy = Boolean.TRUE.equals(loading);
            binding.progress.setVisibility(busy ? View.VISIBLE : View.GONE);
            // Đang gửi thì khoá ô nhập và nút để không bấm 2 lần
            binding.buttonSignIn.setEnabled(!busy);
            binding.buttonGoogle.setEnabled(!busy);
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
