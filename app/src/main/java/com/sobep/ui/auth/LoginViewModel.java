package com.sobep.ui.auth;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.sobep.R;
import com.sobep.data.repository.AuthRepository;

/**
 * ViewModel màn Đăng nhập (CN13): đăng nhập bằng Firebase Auth theo 2 cách
 * - Email + mật khẩu: kiểm tra ô nhập rồi gửi.
 * - Google: LoginActivity mở bảng chọn tài khoản (Credential Manager) rồi đưa ID token vào đây.
 * Thêm "Quên mật khẩu?": gửi thư đặt lại mật khẩu tới email đang gõ.
 */
public class LoginViewModel extends AuthFormViewModel {

    // Email vừa gửi thư quên mật khẩu (để hiện khung xanh báo đã gửi); null = ẩn khung
    private final MutableLiveData<String> resetSentEmail = new MutableLiveData<>(null);

    public LoginViewModel(@NonNull Application application) {
        super(application);
    }

    public LiveData<String> getResetSentEmail() {
        return resetSentEmail;
    }

    public void signIn(String email, String password) {
        if (isBusy()) {
            return;
        }
        resetSentEmail.setValue(null);
        String cleanEmail = cleanEmail(email);
        // Kiểm tra cả 2 ô (không dừng ở ô đầu) để báo hết lỗi một lượt.
        // Đăng nhập chỉ cần mật khẩu không trống; độ dài do Firebase kiểm tra.
        boolean emailOk = check(emailError, AuthFormHelper.validateEmail(cleanEmail));
        boolean passwordOk = check(passwordError, password == null || password.isEmpty()
                ? R.string.error_password_required : 0);
        if (!emailOk || !passwordOk) {
            return;
        }
        startLoading();
        repository.signIn(cleanEmail, password, resultCallback());
    }

    /**
     * Bấm "Tiếp tục với Google": bật vòng xoay chờ từ lúc mở bảng chọn tài khoản để không bấm 2 lần.
     * Trả về false nếu đang bận (Activity không mở bảng chọn nữa).
     */
    public boolean beginGoogleSignIn() {
        if (isBusy()) {
            return false;
        }
        // Đăng nhập Google không dùng 2 ô nhập → xoá lỗi cũ dưới ô cho đỡ rối
        emailError.setValue(0);
        passwordError.setValue(0);
        resetSentEmail.setValue(null);
        startLoading();
        return true;
    }

    /**
     * Bấm "Quên mật khẩu?": dùng email đang gõ ở ô Email, chỉ kiểm tra ô này (không cần mật khẩu)
     * rồi nhờ Firebase gửi thư đặt lại mật khẩu.
     */
    public void sendPasswordReset(String email) {
        if (isBusy()) {
            return;
        }
        resetSentEmail.setValue(null);
        passwordError.setValue(0);
        String cleanEmail = cleanEmail(email);
        int error = AuthFormHelper.validateEmail(cleanEmail);
        // Ô trống: nói rõ cần email để làm gì
        if (error == R.string.error_email_required) {
            error = R.string.error_email_required_for_reset;
        }
        if (!check(emailError, error)) {
            return;
        }
        startLoading();
        repository.sendPasswordReset(cleanEmail, new AuthRepository.ResetCallback() {
            @Override
            public void onSent() {
                stopLoading();
                resetSentEmail.setValue(cleanEmail);
            }

            @Override
            public void onError(@NonNull Exception error) {
                showError(AuthFormHelper.messageForPasswordReset(error));
            }
        });
    }

    /** Đã chọn tài khoản Google: gửi ID token cho Firebase. Kết quả báo về như đăng nhập email. */
    public void signInWithGoogle(String idToken) {
        repository.signInWithGoogle(idToken, resultCallback());
    }

    /** Người dùng tự đóng bảng chọn tài khoản: không phải lỗi, chỉ tắt vòng xoay. */
    public void onGoogleCancelled() {
        stopLoading();
    }

    /** Không lấy được tài khoản Google (máy chưa có tài khoản, mất mạng…). */
    public void onGoogleError(@StringRes int messageRes) {
        showError(messageRes);
    }
}
