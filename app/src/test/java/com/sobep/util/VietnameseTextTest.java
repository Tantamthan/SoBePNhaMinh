package com.sobep.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Unit test cho VietnameseText (chạy trên máy tính: ./gradlew testDebugUnitTest).
 * Trọng tâm: tìm kiếm không phân biệt dấu ở màn chính và màn Khám phá.
 */
public class VietnameseTextTest {

    @Test
    public void normalize_removesAccentsAndLowercases() {
        assertEquals("thit kho trung", VietnameseText.normalize("Thịt kho trứng"));
        assertEquals("dau phu sot ca", VietnameseText.normalize("  Đậu phụ sốt cà "));
        assertEquals("", VietnameseText.normalize(null));
    }

    @Test
    public void contains_keywordWithoutAccents_matchesAccentedName() {
        // Lỗi đã gặp khi test trên máy ảo: gõ "trung" không ra "Thịt kho trứng"
        assertTrue(VietnameseText.containsIgnoreAccents("Thịt kho trứng", "trung"));
        assertTrue(VietnameseText.containsIgnoreAccents("Canh chua cá lóc", "ca loc"));
        assertTrue(VietnameseText.containsIgnoreAccents("Đậu hũ chiên", "dau"));
    }

    @Test
    public void contains_ignoresCaseAndAccentsOnBothSides() {
        assertTrue(VietnameseText.containsIgnoreAccents("Thịt kho trứng", "TRỨNG"));
        assertTrue(VietnameseText.containsIgnoreAccents("thit kho trung", "Trứng"));
        assertTrue(VietnameseText.containsIgnoreAccents("Thịt kho trứng", "  kho  ".trim()));
    }

    @Test
    public void contains_differentWord_doesNotMatch() {
        assertFalse(VietnameseText.containsIgnoreAccents("Thịt kho trứng", "trang"));
        assertFalse(VietnameseText.containsIgnoreAccents("Canh chua cá lóc", "ga"));
    }

    @Test
    public void contains_emptyKeywordMatchesEverything_nullTextMatchesNothing() {
        assertTrue(VietnameseText.containsIgnoreAccents("Canh chua", ""));
        assertTrue(VietnameseText.containsIgnoreAccents("Canh chua", "   "));
        assertTrue(VietnameseText.containsIgnoreAccents(null, ""));
        assertFalse(VietnameseText.containsIgnoreAccents(null, "canh"));
    }

    @Test
    public void fold_keepsSameLength() {
        String original = "Nguyên liệu: Đường";
        assertEquals(original.length(), VietnameseText.fold(VietnameseText.toNfc(original)).length());
    }
}
