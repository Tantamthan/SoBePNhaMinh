package com.sobep.util;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

/** Kiểm thử quy định thu nhỏ / nén ảnh trước khi đưa lên Firestore (bước 8d). Chạy: ./gradlew testDebugUnitTest */
public class CloudPhotoTest {

    @Test
    public void fitInside_landscapePhoto_longSideBecomesMax() {
        // Ảnh camera 4000×3000 → 800×600
        assertArrayEquals(new int[]{800, 600}, CloudPhoto.fitInside(4000, 3000, 800));
    }

    @Test
    public void fitInside_portraitPhoto_heightBecomesMax() {
        // Ảnh dọc 1280×960 đã xoay → 960×1280 → 600×800
        assertArrayEquals(new int[]{600, 800}, CloudPhoto.fitInside(960, 1280, 800));
    }

    @Test
    public void fitInside_smallPhoto_notEnlarged() {
        assertArrayEquals(new int[]{500, 300}, CloudPhoto.fitInside(500, 300, 800));
    }

    @Test
    public void fitInside_veryThinPhoto_keepsAtLeastOnePixel() {
        int[] size = CloudPhoto.fitInside(10000, 2, 240);
        assertEquals(240, size[0]);
        assertTrue(size[1] >= 1);
    }

    @Test
    public void qualitySteps_fromPhotoQuality_goesDownToMinimum() {
        assertEquals(Arrays.asList(70, 60, 50, 40), CloudPhoto.qualitySteps(CloudPhoto.PHOTO_QUALITY));
    }

    @Test
    public void qualitySteps_belowMinimum_onlyThatQuality() {
        assertEquals(Collections.singletonList(30), CloudPhoto.qualitySteps(30));
    }

    @Test
    public void limits_fitInsideOneFirestoreDocument() {
        // Một document Firestore tối đa 1 MiB; ảnh lớn ở document riêng, ảnh nhỏ nằm chung với chữ của bài
        assertTrue(CloudPhoto.PHOTO_MAX_BYTES < 1024 * 1024);
        // Ảnh nhỏ nhẹ hơn ảnh lớn ít nhất 10 lần → tải danh sách 50 bài vẫn nhẹ
        assertTrue(CloudPhoto.THUMB_MAX_BYTES * 10 <= CloudPhoto.PHOTO_MAX_BYTES);
    }
}
