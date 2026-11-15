package com.sobep.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.sobep.util.SyncMerge.Action;
import com.sobep.util.SyncMerge.PhotoAction;

import org.junit.Test;

/**
 * Unit test cho SyncMerge (chạy trên máy tính: ./gradlew testDebugUnitTest).
 * Mỗi test là một tình huống đồng bộ 1 món giữa máy và Firestore.
 */
public class SyncMergeTest {

    @Test
    public void onlyOnPhone_uploads() {
        // Món mới nhập, chưa từng đồng bộ
        assertEquals(Action.UPLOAD, SyncMerge.decide(100L, null, false));
    }

    @Test
    public void onlyOnCloud_downloads() {
        // Đổi máy mới, đăng nhập lại → lấy món từ mây về
        assertEquals(Action.DOWNLOAD, SyncMerge.decide(null, 100L, false));
    }

    @Test
    public void onlyOnCloudButDeleted_doesNothing() {
        // Bia mộ của món đã xoá, máy này cũng không có → không tải về
        assertEquals(Action.NONE, SyncMerge.decide(null, 100L, true));
    }

    @Test
    public void phoneNewer_uploads() {
        assertEquals(Action.UPLOAD, SyncMerge.decide(200L, 100L, false));
    }

    @Test
    public void cloudNewer_downloads() {
        // Sửa món trên máy khác rồi đồng bộ → máy này lấy bản mới
        assertEquals(Action.DOWNLOAD, SyncMerge.decide(100L, 200L, false));
    }

    @Test
    public void sameTime_alreadyInSync() {
        assertEquals(Action.NONE, SyncMerge.decide(100L, 100L, false));
    }

    @Test
    public void deletedOnOtherPhone_deletesHere() {
        // Máy khác xoá món sau (hoặc cùng lúc) lần sửa cuối của máy này → xoá theo
        assertEquals(Action.DELETE_LOCAL, SyncMerge.decide(100L, 200L, true));
        assertEquals(Action.DELETE_LOCAL, SyncMerge.decide(100L, 100L, true));
    }

    @Test
    public void editedAfterDeletion_uploadsAgain() {
        // Máy này sửa món SAU khi máy khác xoá → giữ món, gửi lên lại
        assertEquals(Action.UPLOAD, SyncMerge.decide(300L, 200L, true));
    }

    @Test
    public void nothingAnywhere_doesNothing() {
        assertEquals(Action.NONE, SyncMerge.decide(null, null, false));
    }

    // ---------- Ảnh món (14/11/2026) ----------

    @Test
    public void photo_oldCloudDocWithoutField_keepsLocal() {
        // Bản gửi lên trước khi có đồng bộ ảnh: không biết gì về ảnh → giữ ảnh trong máy
        assertEquals(PhotoAction.KEEP, SyncMerge.decidePhoto("recipe_1.jpg", false, null));
    }

    @Test
    public void photo_samePhotoId_keeps() {
        // Chỉ sửa chữ ở máy khác → không tải lại ảnh
        assertEquals(PhotoAction.KEEP, SyncMerge.decidePhoto("recipe_1.jpg", true, "recipe_1.jpg"));
    }

    @Test
    public void photo_changedOnOtherPhone_downloads() {
        assertEquals(PhotoAction.DOWNLOAD, SyncMerge.decidePhoto("recipe_1.jpg", true, "recipe_2.jpg"));
        assertEquals(PhotoAction.DOWNLOAD, SyncMerge.decidePhoto(null, true, "recipe_2.jpg"));
    }

    @Test
    public void photo_removedOnOtherPhone_removes() {
        assertEquals(PhotoAction.REMOVE, SyncMerge.decidePhoto("recipe_1.jpg", true, null));
        assertEquals(PhotoAction.KEEP, SyncMerge.decidePhoto(null, true, null));
    }

    @Test
    public void photo_unsafeId_ignored() {
        // Mã ảnh lạ (có thể ghi file ra ngoài thư mục ảnh) → không tải
        assertEquals(PhotoAction.KEEP, SyncMerge.decidePhoto(null, true, "../databases/sobep.db"));
    }

    @Test
    public void photoUpload_onlyWhenCloudLacksThisPhoto() {
        assertTrue(SyncMerge.needsPhotoUpload("recipe_2.jpg", "recipe_1.jpg"));
        assertTrue(SyncMerge.needsPhotoUpload("recipe_2.jpg", null));
        assertFalse(SyncMerge.needsPhotoUpload("recipe_2.jpg", "recipe_2.jpg"));
        assertFalse(SyncMerge.needsPhotoUpload(null, "recipe_1.jpg"));
    }
}
