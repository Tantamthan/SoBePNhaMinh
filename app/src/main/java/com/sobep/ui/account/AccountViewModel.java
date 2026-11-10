package com.sobep.ui.account;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.sobep.R;
import com.sobep.data.repository.AuthRepository;
import com.sobep.data.repository.SyncRepository;

import java.util.concurrent.TimeoutException;

/**
 * ViewModel màn Tài khoản: email người đang đăng nhập, đồng bộ Firestore (CN14) và đăng xuất.
 * Trạng thái đồng bộ giữ trong ViewModel → xoay màn hình khi đang đồng bộ vẫn còn thanh chạy, không gửi lại 2 lần.
 */
public class AccountViewModel extends AndroidViewModel {

    private final AuthRepository authRepository = new AuthRepository();
    private final SyncRepository syncRepository;

    private final MutableLiveData<Boolean> syncing = new MutableLiveData<>(false);
    // Kết quả lần đồng bộ vừa xong; null = chưa đồng bộ trong lần mở màn này
    private final MutableLiveData<SyncRepository.SyncResult> syncResult = new MutableLiveData<>(null);
    // Lỗi lần đồng bộ vừa rồi (mã chuỗi); null = không lỗi
    private final MutableLiveData<Integer> syncError = new MutableLiveData<>(null);
    // Thời điểm đồng bộ thành công gần nhất (mili giây); 0 = chưa từng
    private final MutableLiveData<Long> lastSyncAt = new MutableLiveData<>(0L);

    public AccountViewModel(@NonNull Application application) {
        super(application);
        syncRepository = new SyncRepository(application);
        lastSyncAt.setValue(syncRepository.getLastSyncAt());
    }

    public boolean isSignedIn() {
        return authRepository.isSignedIn();
    }

    /** Email đang đăng nhập; null nếu chưa đăng nhập. */
    @Nullable
    public String getEmail() {
        FirebaseUser user = authRepository.getCurrentUser();
        return user == null ? null : user.getEmail();
    }

    public LiveData<Boolean> isSyncing() {
        return syncing;
    }

    public LiveData<SyncRepository.SyncResult> getSyncResult() {
        return syncResult;
    }

    public LiveData<Integer> getSyncError() {
        return syncError;
    }

    public LiveData<Long> getLastSyncAt() {
        return lastSyncAt;
    }

    /** CN14: bấm "Đồng bộ ngay". Đang đồng bộ thì bỏ qua lần bấm tiếp. */
    public void syncNow() {
        if (Boolean.TRUE.equals(syncing.getValue())) {
            return;
        }
        syncing.setValue(true);
        syncError.setValue(null);
        syncRepository.syncNow(new SyncRepository.SyncCallback() {
            // Hai hàm dưới chạy ở LUỒNG NỀN → dùng postValue
            @Override
            public void onSuccess(@NonNull SyncRepository.SyncResult result) {
                syncResult.postValue(result);
                lastSyncAt.postValue(syncRepository.getLastSyncAt());
                syncing.postValue(false);
            }

            @Override
            public void onError(@NonNull Exception error) {
                syncError.postValue(messageForSyncError(error));
                syncing.postValue(false);
            }
        });
    }

    /** Đăng xuất (cả tài khoản email lẫn Google): món ăn trong máy (Room) giữ nguyên. */
    public void signOut() {
        authRepository.signOut(getApplication());
    }

    /** Lỗi Firestore → câu báo tiếng Việt. */
    @StringRes
    static int messageForSyncError(Exception error) {
        if (error instanceof TimeoutException || error instanceof FirebaseNetworkException) {
            return R.string.error_sync_network;
        }
        if (error instanceof FirebaseFirestoreException) {
            FirebaseFirestoreException.Code code = ((FirebaseFirestoreException) error).getCode();
            if (code == FirebaseFirestoreException.Code.UNAVAILABLE
                    || code == FirebaseFirestoreException.Code.DEADLINE_EXCEEDED) {
                return R.string.error_sync_network;
            }
            if (code == FirebaseFirestoreException.Code.PERMISSION_DENIED
                    || code == FirebaseFirestoreException.Code.UNAUTHENTICATED) {
                return R.string.error_sync_permission;
            }
            if (code == FirebaseFirestoreException.Code.NOT_FOUND
                    || code == FirebaseFirestoreException.Code.FAILED_PRECONDITION) {
                // Thường gặp khi chưa bấm "Create database" trên Console
                return R.string.error_sync_not_ready;
            }
        }
        return R.string.error_sync_unknown;
    }
}
