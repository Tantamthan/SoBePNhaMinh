package com.sobep.ui.auth;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.firebase.auth.FirebaseUser;
import com.sobep.data.repository.AuthRepository;

/**
 * Phần chung của LoginViewModel và RegisterViewModel:
 * trạng thái đang gửi, lỗi dưới từng ô, thông báo lỗi từ Firebase và kết quả thành công.
 *
 * Giữ trong ViewModel nên xoay màn hình khi đang đăng nhập vẫn còn vòng xoay chờ và không gửi lại 2 lần.
 */
public abstract class AuthFormViewModel extends AndroidViewModel {

    protected final AuthRepository repository = new AuthRepository();

    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(false);
    // Lỗi dưới ô (mã chuỗi, 0 = không lỗi)
    protected final MutableLiveData<Integer> emailError = new MutableLiveData<>(0);
    protected final MutableLiveData<Integer> passwordError = new MutableLiveData<>(0);
    // Lỗi Firebase trả về; null = không có lỗi. Giữ nguyên khi xoay màn hình, xoá khi bấm gửi lại
    private final MutableLiveData<Integer> message = new MutableLiveData<>(null);
    // Email của tài khoản vừa đăng nhập / đăng ký thành công; null = chưa xong
    private final MutableLiveData<String> signedInEmail = new MutableLiveData<>(null);

    protected AuthFormViewModel(@NonNull Application application) {
        super(application);
    }

    public LiveData<Boolean> isLoading() {
        return loading;
    }

    public LiveData<Integer> getEmailError() {
        return emailError;
    }

    public LiveData<Integer> getPasswordError() {
        return passwordError;
    }

    public LiveData<Integer> getMessage() {
        return message;
    }

    public LiveData<String> getSignedInEmail() {
        return signedInEmail;
    }

    /** Đang gửi thì bỏ qua lần bấm tiếp theo (tránh tạo 2 yêu cầu cùng lúc). */
    protected boolean isBusy() {
        return Boolean.TRUE.equals(loading.getValue());
    }

    /** Gọi trước khi gửi lên Firebase: xoá lỗi lần trước, bật vòng xoay chờ. */
    protected void startLoading() {
        message.setValue(null);
        loading.setValue(true);
    }

    /** Callback chung: Firebase gọi về trên main thread nên dùng setValue được. */
    protected AuthRepository.Callback resultCallback() {
        return new AuthRepository.Callback() {
            @Override
            public void onSuccess(@NonNull FirebaseUser user) {
                loading.setValue(false);
                signedInEmail.setValue(user.getEmail());
            }

            @Override
            public void onError(@NonNull Exception error) {
                loading.setValue(false);
                message.setValue(AuthFormHelper.messageFor(error));
            }
        };
    }

    /** Email người dùng gõ: bỏ khoảng trắng đầu / cuối (hay dính khi dán từ chỗ khác). */
    protected static String cleanEmail(@Nullable String email) {
        return email == null ? "" : email.trim();
    }

    /** Đặt lỗi cho ô; trả về true nếu ô hợp lệ. */
    protected static boolean check(MutableLiveData<Integer> field, @StringRes int error) {
        field.setValue(error);
        return error == 0;
    }
}
