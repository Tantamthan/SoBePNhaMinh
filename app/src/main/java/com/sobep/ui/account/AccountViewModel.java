package com.sobep.ui.account;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;

import com.google.firebase.auth.FirebaseUser;
import com.sobep.data.repository.AuthRepository;

/**
 * ViewModel màn Tài khoản: email người đang đăng nhập và đăng xuất.
 * Nút "Đồng bộ" (CN14) sẽ nối vào đây ở bước 8b.
 */
public class AccountViewModel extends AndroidViewModel {

    private final AuthRepository repository = new AuthRepository();

    public AccountViewModel(@NonNull Application application) {
        super(application);
    }

    public boolean isSignedIn() {
        return repository.isSignedIn();
    }

    /** Email đang đăng nhập; null nếu chưa đăng nhập. */
    @Nullable
    public String getEmail() {
        FirebaseUser user = repository.getCurrentUser();
        return user == null ? null : user.getEmail();
    }

    /** Đăng xuất (cả tài khoản email lẫn Google): món ăn trong máy (Room) giữ nguyên. */
    public void signOut() {
        repository.signOut(getApplication());
    }
}
