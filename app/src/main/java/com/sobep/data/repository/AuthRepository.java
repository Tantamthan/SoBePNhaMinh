package com.sobep.data.repository;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.credentials.ClearCredentialStateRequest;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.exceptions.ClearCredentialException;

import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;

/**
 * CN13: gói Firebase Auth – đăng ký, đăng nhập bằng email + mật khẩu HOẶC bằng tài khoản Google, đăng xuất.
 *
 * Độc lập với Room: đăng nhập / đăng xuất KHÔNG đọc hay ghi CSDL trong máy, món ăn giữ nguyên.
 * Firebase Auth chỉ cho biết "ai đang dùng app" (uid + email); bước đồng bộ (CN14) sẽ dùng uid này.
 *
 * Firebase tự chạy việc gọi mạng ở luồng nền và gọi Callback trên main thread,
 * nên ViewModel nhận kết quả xong có thể setValue ngay.
 */
public class AuthRepository {

    private static final String TAG = "AuthRepository";

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

    /**
     * Đăng nhập bằng Google. idToken do Credential Manager trả về sau khi người dùng chọn tài khoản Google
     * (bước đó cần giao diện nên LoginActivity làm). Firebase kiểm tra token với Google:
     * lần đầu thì tự tạo tài khoản Firebase mới (không cần màn Đăng ký), lần sau thì đăng nhập lại đúng uid cũ.
     */
    public void signInWithGoogle(String idToken, Callback callback) {
        // Tham số thứ 2 là accessToken: Firebase chỉ cần idToken nên để null
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        handle(auth.signInWithCredential(credential), callback);
    }

    /**
     * Đăng xuất: chỉ xoá phiên đăng nhập trên máy, không xoá món ăn trong Room.
     * Xoá thêm trạng thái của Credential Manager để lần sau bấm "Tiếp tục với Google"
     * vẫn được chọn tài khoản khác, không tự đăng nhập lại tài khoản cũ.
     */
    public void signOut(@NonNull Context context) {
        auth.signOut();
        CredentialManager.create(context).clearCredentialStateAsync(
                new ClearCredentialStateRequest(),
                null,
                ContextCompat.getMainExecutor(context),
                new CredentialManagerCallback<Void, ClearCredentialException>() {
                    @Override
                    public void onResult(Void result) {
                        // Không cần làm gì thêm
                    }

                    @Override
                    public void onError(@NonNull ClearCredentialException e) {
                        // Không ảnh hưởng tới việc đăng xuất Firebase (đã xong ở trên), chỉ ghi log
                        Log.w(TAG, "Không xoá được trạng thái Credential Manager", e);
                    }
                });
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
