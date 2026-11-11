package com.sobep.util;

import androidx.annotation.StringRes;

import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.sobep.R;

import java.util.concurrent.TimeoutException;

/**
 * Đổi lỗi Firestore thành câu báo tiếng Việt. Dùng chung cho Đồng bộ (CN14) và Cộng đồng (CN15).
 */
public final class FirestoreErrors {

    private FirestoreErrors() {
    }

    /** fallbackRes: câu báo khi không nhận ra lỗi (mỗi chức năng một câu riêng). */
    @StringRes
    public static int messageFor(Exception error, @StringRes int fallbackRes) {
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
        return fallbackRes;
    }
}
