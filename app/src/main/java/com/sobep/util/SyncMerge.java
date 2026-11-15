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

    /** Làm gì với ẢNH của món khi tải bản trên mây về máy. */
    public enum PhotoAction {
        /** Giữ ảnh đang có trong máy (đã cùng ảnh, hoặc bản trên mây chưa có thông tin ảnh). */
        KEEP,
        /** Tải ảnh trên mây về (món chưa có ảnh trong máy, hoặc ảnh đã được đổi ở máy khác). */
        DOWNLOAD,
        /** Ảnh đã bị xoá ở máy khác → bỏ ảnh trong máy. */
        REMOVE
    }

    private SyncMerge() {
        // Chỉ chứa hàm tĩnh
    }

    /**
     * Ảnh của món đang được tải về (đã quyết định DOWNLOAD phần chữ).
     *
     * @param localPhotoId        mã ảnh trong máy (tên file, xem CloudPhoto.photoIdOf); null = món không có ảnh
     * @param remoteHasPhotoField document trên mây có field photoId không (bản gửi lên trước khi có đồng bộ ảnh
     *                            thì không có → không biết gì về ảnh → giữ nguyên)
     * @param remotePhotoId       giá trị field photoId; null = món trên mây không có ảnh
     */
    @NonNull
    public static PhotoAction decidePhoto(@Nullable String localPhotoId, boolean remoteHasPhotoField,
                                          @Nullable String remotePhotoId) {
        if (!remoteHasPhotoField) {
            return PhotoAction.KEEP;
        }
        if (remotePhotoId == null) {
            return localPhotoId == null ? PhotoAction.KEEP : PhotoAction.REMOVE;
        }
        if (remotePhotoId.equals(localPhotoId) || !CloudPhoto.isSafePhotoId(remotePhotoId)) {
            return PhotoAction.KEEP;
        }
        return PhotoAction.DOWNLOAD;
    }

    /**
     * Gửi món lên mây: có phải gửi kèm ảnh không. Trên mây đã có đúng ảnh này (cùng mã) thì thôi,
     * nhờ vậy sửa chữ / bấm yêu thích không phải gửi lại ảnh vài trăm KB.
     */
    public static boolean needsPhotoUpload(@Nullable String localPhotoId, @Nullable String remotePhotoId) {
        return localPhotoId != null && !localPhotoId.equals(remotePhotoId);
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
