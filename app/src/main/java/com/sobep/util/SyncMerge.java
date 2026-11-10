package com.sobep.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * CN14: quyết định làm gì với MỘT món khi đồng bộ máy ↔ Firestore.
 * Tách riêng, không dùng Android / Firebase → viết unit test được (SyncMergeTest).
 *
 * Quy tắc "bản sửa sau cùng thắng" (last write wins) theo updatedAt:
 * - Chỉ có trong máy                  → gửi lên mây.
 * - Chỉ có trên mây                   → tải về máy (trừ khi trên mây là "bia mộ" = món đã bị xoá).
 * - Có cả hai                         → bản nào updatedAt lớn hơn thắng; bằng nhau = đã khớp.
 * - Trên mây là bia mộ, có trong máy → bia mộ mới hơn (hoặc bằng) thì xoá món trong máy;
 *                                       món trong máy được sửa SAU khi xoá thì gửi lên lại (món "sống lại").
 */
public final class SyncMerge {

    public enum Action {
        /** Không làm gì (đã khớp, hoặc món đã xoá ở cả hai nơi). */
        NONE,
        /** Gửi bản trong máy lên Firestore. */
        UPLOAD,
        /** Tải bản trên Firestore về máy (thêm mới hoặc ghi đè). */
        DOWNLOAD,
        /** Món đã bị xoá ở máy khác → xoá trong máy này. */
        DELETE_LOCAL
    }

    private SyncMerge() {
        // Chỉ chứa hàm tĩnh
    }

    /**
     * @param localUpdatedAt  updatedAt của món trong máy; null = máy không có món này
     * @param remoteUpdatedAt updatedAt của document trên mây; null = mây không có món này
     * @param remoteDeleted   true = document trên mây là bia mộ (deleted = true)
     */
    @NonNull
    public static Action decide(@Nullable Long localUpdatedAt, @Nullable Long remoteUpdatedAt,
                                boolean remoteDeleted) {
        if (localUpdatedAt == null && remoteUpdatedAt == null) {
            return Action.NONE;
        }
        if (remoteUpdatedAt == null) {
            return Action.UPLOAD;
        }
        if (localUpdatedAt == null) {
            return remoteDeleted ? Action.NONE : Action.DOWNLOAD;
        }
        if (remoteDeleted) {
            return remoteUpdatedAt >= localUpdatedAt ? Action.DELETE_LOCAL : Action.UPLOAD;
        }
        if (localUpdatedAt > remoteUpdatedAt) {
            return Action.UPLOAD;
        }
        if (remoteUpdatedAt > localUpdatedAt) {
            return Action.DOWNLOAD;
        }
        return Action.NONE;
    }
}
