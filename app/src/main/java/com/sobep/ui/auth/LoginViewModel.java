package com.sobep.ui.auth;

import android.app.Application;

import androidx.annotation.NonNull;

import com.sobep.R;

/** ViewModel màn Đăng nhập (CN13): kiểm tra ô nhập rồi đăng nhập bằng Firebase Auth. */
public class LoginViewModel extends AuthFormViewModel {

    public LoginViewModel(@NonNull Application application) {
        super(application);
    }

    public void signIn(String email, String password) {
        if (isBusy()) {
            return;
        }
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
}
