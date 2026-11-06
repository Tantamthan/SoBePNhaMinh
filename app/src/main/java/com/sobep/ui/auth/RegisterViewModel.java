package com.sobep.ui.auth;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.sobep.R;

/**
 * ViewModel màn Đăng ký (CN13): kiểm tra email, mật khẩu ≥ 6 ký tự, nhập lại khớp,
 * rồi tạo tài khoản Firebase (tạo xong là đã đăng nhập).
 */
public class RegisterViewModel extends AuthFormViewModel {

    private final MutableLiveData<Integer> confirmError = new MutableLiveData<>(0);

    public RegisterViewModel(@NonNull Application application) {
        super(application);
    }

    public LiveData<Integer> getConfirmError() {
        return confirmError;
    }

    public void register(String email, String password, String confirmPassword) {
        if (isBusy()) {
            return;
        }
        String cleanEmail = cleanEmail(email);
        boolean emailOk = check(emailError, AuthFormHelper.validateEmail(cleanEmail));
        boolean passwordOk = check(passwordError, AuthFormHelper.validatePassword(password));
        boolean confirmOk = check(confirmError,
                password != null && password.equals(confirmPassword) ? 0 : R.string.error_password_mismatch);
        if (!emailOk || !passwordOk || !confirmOk) {
            return;
        }
        startLoading();
        repository.register(cleanEmail, password, resultCallback());
    }
}
