package com.sobep.ui.auth;

import android.util.Patterns;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.credentials.exceptions.GetCredentialException;
import androidx.credentials.exceptions.GetCredentialInterruptedException;
import androidx.credentials.exceptions.NoCredentialException;

import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.FirebaseTooManyRequestsException;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseAuthWeakPasswordException;
import com.sobep.R;

/**
 * Dùng chung cho màn Đăng nhập và Đăng ký:
 * - Kiểm tra ô nhập TRƯỚC khi gửi lên Firebase (đỡ tốn lượt gọi mạng, báo lỗi ngay dưới ô).
 * - Đổi lỗi Firebase (tiếng Anh, mã lỗi) thành câu tiếng Việt dễ hiểu.
 * Trả về mã chuỗi trong strings.xml; 0 = không có lỗi.
 */
public final class AuthFormHelper {

    /** Firebase bắt buộc mật khẩu tối thiểu 6 ký tự. */
    public static final int MIN_PASSWORD_LENGTH = 6;

    private AuthFormHelper() {
        // Chỉ chứa hàm tĩnh
    }

    @StringRes
    public static int validateEmail(@Nullable String email) {
        if (email == null || email.isEmpty()) {
            return R.string.error_email_required;
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            return R.string.error_email_invalid;
        }
        return 0;
    }

    @StringRes
    public static int validatePassword(@Nullable String password) {
        if (password == null || password.isEmpty()) {
            return R.string.error_password_required;
        }
        if (password.length() < MIN_PASSWORD_LENGTH) {
            return R.string.error_password_short;
        }
        return 0;
    }

    /**
     * Lỗi Firebase → câu báo cho người dùng.
     * Thứ tự kiểm tra quan trọng: WeakPassword là lớp con của InvalidCredentials nên phải xét trước.
     */
    @StringRes
    public static int messageFor(Exception error) {
        if (error instanceof FirebaseNetworkException) {
            return R.string.error_auth_network;
        }
        if (error instanceof FirebaseTooManyRequestsException) {
            return R.string.error_auth_too_many;
        }
        if (error instanceof FirebaseAuthWeakPasswordException) {
            return R.string.error_password_short;
        }
        if (error instanceof FirebaseAuthUserCollisionException) {
            return R.string.error_auth_email_in_use;
        }
        if (error instanceof FirebaseAuthInvalidUserException) {
            // Tài khoản bị khoá trên Firebase Console, hoặc email chưa đăng ký
            String code = ((FirebaseAuthInvalidUserException) error).getErrorCode();
            return "ERROR_USER_DISABLED".equals(code)
                    ? R.string.error_auth_user_disabled : R.string.error_auth_wrong_credentials;
        }
        if (error instanceof FirebaseAuthInvalidCredentialsException) {
            String code = ((FirebaseAuthInvalidCredentialsException) error).getErrorCode();
            return "ERROR_INVALID_EMAIL".equals(code)
                    ? R.string.error_email_invalid : R.string.error_auth_wrong_credentials;
        }
        if (isProviderDisabled(error)) {
            return R.string.error_auth_not_enabled;
        }
        return R.string.error_auth_unknown;
    }

    /**
     * Lỗi khi gửi thư quên mật khẩu → câu báo. Khác đăng nhập ở chỗ không có mật khẩu,
     * nên không được báo "Email hoặc mật khẩu không đúng".
     * (Chỉ gặp "chưa có tài khoản" khi tắt chống dò email trên Console; mặc định Firebase báo thành công.)
     */
    @StringRes
    public static int messageForPasswordReset(Exception error) {
        if (error instanceof FirebaseAuthInvalidUserException) {
            String code = ((FirebaseAuthInvalidUserException) error).getErrorCode();
            return "ERROR_USER_DISABLED".equals(code)
                    ? R.string.error_auth_user_disabled : R.string.error_reset_no_account;
        }
        int res = messageFor(error);
        return res == R.string.error_auth_wrong_credentials ? R.string.error_auth_unknown : res;
    }

    /**
     * Lỗi khi lấy tài khoản Google từ Credential Manager (TRƯỚC khi gửi lên Firebase) → câu báo.
     * Trường hợp người dùng tự đóng bảng chọn (GetCredentialCancellationException) không coi là lỗi,
     * LoginActivity xử lý riêng nên không có ở đây.
     */
    @StringRes
    public static int messageForGoogle(GetCredentialException error) {
        if (error instanceof NoCredentialException) {
            // Máy chưa đăng nhập tài khoản Google nào (hoặc không có Google Play)
            return R.string.error_google_no_account;
        }
        if (error instanceof GetCredentialInterruptedException) {
            return R.string.error_auth_network;
        }
        // Còn lại: thường do cấu hình (thiếu SHA-1 trên Firebase Console, sai Web client ID)
        return R.string.error_google_failed;
    }

    /** Chưa bật "Email/Password" trong Firebase Console → Authentication → Sign-in method. */
    private static boolean isProviderDisabled(Exception error) {
        if (error instanceof FirebaseAuthException
                && "ERROR_OPERATION_NOT_ALLOWED".equals(((FirebaseAuthException) error).getErrorCode())) {
            return true;
        }
        String message = error.getMessage();
        return message != null && message.contains("CONFIGURATION_NOT_FOUND");
    }
}
