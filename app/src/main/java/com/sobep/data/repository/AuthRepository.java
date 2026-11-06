package com.sobep.data.repository;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

/**
 * CN13: gói Firebase Auth (đăng ký, đăng nhập, đăng xuất bằng email + mật khẩu).
 *
 * Độc lập với Room: đăng nhập / đăng xuất KHÔNG đọc hay ghi CSDL trong máy, món ăn giữ nguyên.
 * Firebase Auth chỉ cho biết "ai đang dùng app" (uid + email); bước đồng bộ (CN14) sẽ dùng uid này.
 *
 * Firebase tự chạy việc gọi mạng ở luồng nền và gọi Callback trên main thread,
 * nên ViewModel nhận kết quả xong có thể setValue ngay.
 */
public class AuthRepository {

    /** Kết quả đăng ký / đăng nhập trả về cho ViewModel. */
    public interface Callback {
        void onSuccess(@NonNull FirebaseUser user);

        /** error: lỗi Firebase trả về (sai mật khẩu, mất mạng…), ViewModel đổi thành câu tiếng Việt. */
        void onError(@NonNull Exception error);
    }

    private final FirebaseAuth auth;

    public AuthRepository() {
        // FirebaseAuth là singleton: Firebase tự khởi tạo khi app mở (nhờ google-services.json)
        auth = FirebaseAuth.getInstance();
    }

    /** Người đang đăng nhập; null = chưa đăng nhập. Firebase tự nhớ phiên đăng nhập kể cả khi tắt app. */
    @Nullable
    public FirebaseUser getCurrentUser() {
        return auth.getCurrentUser();
    }

    public boolean isSignedIn() {
        return auth.getCurrentUser() != null;
    }

    /** Tạo tài khoản mới. Tạo xong Firebase tự đăng nhập luôn bằng tài khoản đó. */
    public void register(String email, String password, Callback callback) {
        handle(auth.createUserWithEmailAndPassword(email, password), callback);
    }

    public void signIn(String email, String password, Callback callback) {
        handle(auth.signInWithEmailAndPassword(email, password), callback);
    }

    /** Đăng xuất: chỉ xoá phiên đăng nhập trên máy, không xoá món ăn trong Room. */
    public void signOut() {
        auth.signOut();
    }

    /** Task của Firebase chạy bất đồng bộ; khi xong thì báo thành công hoặc lỗi qua Callback. */
    private void handle(Task<AuthResult> task, Callback callback) {
        task.addOnCompleteListener(result -> {
            FirebaseUser user = result.isSuccessful() ? auth.getCurrentUser() : null;
            if (user != null) {
                callback.onSuccess(user);
            } else {
                Exception error = result.getException();
                callback.onError(error != null ? error : new IllegalStateException("Không có người dùng"));
            }
        });
    }
}
