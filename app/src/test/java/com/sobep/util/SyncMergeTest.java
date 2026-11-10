package com.sobep.util;

import static org.junit.Assert.assertEquals;

import com.sobep.util.SyncMerge.Action;

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
}
