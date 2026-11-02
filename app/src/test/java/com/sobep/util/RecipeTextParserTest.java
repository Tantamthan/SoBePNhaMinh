package com.sobep.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.sobep.data.local.entity.Ingredient;
import com.sobep.data.local.entity.Step;

import org.junit.Test;

/**
 * Unit test cho RecipeTextParser – chạy trên máy tính (JVM), không cần máy ảo.
 * Chạy: Android Studio bấm nút ▶ cạnh tên lớp, hoặc lệnh `./gradlew testDebugUnitTest`.
 */
public class RecipeTextParserTest {

    private static final double DELTA = 0.0001;

    /**
     * Chữ ML Kit đọc thật từ ảnh mẫu "Canh chua cá lóc" (02/11/2026), giữ nguyên 4 lỗi đọc sai:
     * "Ióc" (l → I), "Khấu" (sai dấu), "sach" (mất dấu), "Đồ" (sai dấu). Bước 3 bị ngắt thành 2 dòng.
     */
    private static final String SCANNED_CANH_CHUA =
            "Canh chua cá Ióc\n"
                    + "Thời gian: 35 phút - Khấu phần: 4 người\n"
                    + "NGUYÊN LIỆU\n"
                    + "- 500 g cá lóc\n"
                    + "- Cà chua: 2 quả\n"
                    + "- 1/2 trái thơm\n"
                    + "- Rau om\n"
                    + "CÁCH LÀM\n"
                    + "1. Làm sach cá, cắt khúc.\n"
                    + "2. Phi thơm hành, cho cà chua vào xào.\n"
                    + "3. Đồ nước, nấu sôi rồi cho cá vào nấu\n"
                    + "khoảng 10 phút.";

    @Test
    public void parse_scannedSample_splitsAllParts() {
        RecipeTextParser.Result result = RecipeTextParser.parse(SCANNED_CANH_CHUA);

        // "Ióc" được sửa thành "lóc"
        assertEquals("Canh chua cá lóc", result.name);
        assertEquals(35, result.cookTimeMinutes);
        // "Khấu phần" sai dấu vẫn nhận ra
        assertEquals(4, result.servings);

        assertEquals(4, result.ingredients.size());
        assertIngredient(result.ingredients.get(0), "Cá lóc", 500, "g");
        assertIngredient(result.ingredients.get(1), "Cà chua", 2, "quả");
        assertIngredient(result.ingredients.get(2), "Thơm", 0.5, "trái");
        assertIngredient(result.ingredients.get(3), "Rau om", 0, "");

        assertEquals(3, result.steps.size());
        assertStep(result.steps.get(0), "Làm sach cá, cắt khúc.", 0);
        assertStep(result.steps.get(1), "Phi thơm hành, cho cà chua vào xào.", 0);
        // Dòng "khoảng 10 phút." được nối vào bước 3, và bước 3 có hẹn giờ 10 phút
        assertStep(result.steps.get(2), "Đồ nước, nấu sôi rồi cho cá vào nấu khoảng 10 phút.", 10);

        assertTrue(result.otherLines.isEmpty());
    }

    @Test
    public void parse_withoutHeaders_guessesSectionsFromMarkers() {
        String text = "Trứng chiên\n"
                + "Món nhanh cho bữa sáng\n"
                + "- 3 quả trứng\n"
                + "- Nước mắm 1 muỗng cà phê\n"
                + "1. Đánh tan trứng với nước mắm.\n"
                + "2. Chiên vàng 2 mặt khoảng 5 phút.";
        RecipeTextParser.Result result = RecipeTextParser.parse(text);

        assertEquals("Trứng chiên", result.name);
        // Dòng giới thiệu không thuộc phần nào → đưa vào ghi chú
        assertEquals(1, result.otherLines.size());
        assertEquals("Món nhanh cho bữa sáng", result.otherLines.get(0));

        assertEquals(2, result.ingredients.size());
        assertIngredient(result.ingredients.get(0), "Trứng", 3, "quả");
        assertIngredient(result.ingredients.get(1), "Nước mắm", 1, "muỗng cà phê");

        assertEquals(2, result.steps.size());
        assertEquals(5, result.steps.get(1).timerMinutes);
    }

    @Test
    public void parse_headersWithColonAndStepWords() {
        String text = "Tên món: THỊT KHO TRỨNG\n"
                + "Cho 6 người\n"
                + "I. Nguyên liệu:\n"
                + "Thịt ba chỉ 500g\n"
                + "Muối, tiêu vừa ăn\n"
                + "II. Cách nấu:\n"
                + "Bước 1: Thịt rửa sạch, cắt miếng vừa ăn.\n"
                + "Bước 2: Kho lửa nhỏ 1 giờ 30 phút.";
        RecipeTextParser.Result result = RecipeTextParser.parse(text);

        // Tên viết HOA → chỉ viết hoa chữ đầu; bỏ chữ "Tên món:"
        assertEquals("Thịt kho trứng", result.name);
        assertEquals(6, result.servings);

        assertEquals(2, result.ingredients.size());
        assertIngredient(result.ingredients.get(0), "Thịt ba chỉ", 500, "g");
        assertIngredient(result.ingredients.get(1), "Muối, tiêu", 0, "");

        assertEquals(2, result.steps.size());
        assertStep(result.steps.get(0), "Thịt rửa sạch, cắt miếng vừa ăn.", 0);
        assertStep(result.steps.get(1), "Kho lửa nhỏ 1 giờ 30 phút.", 90);
    }

    @Test
    public void parse_sentenceStartingWithKeyword_isNotHeader() {
        String text = "Canh rau\n"
                + "Cách làm\n"
                + "Chuẩn bị nồi nước sôi.\n"
                + "Cho rau vào nấu 3 phút.";
        RecipeTextParser.Result result = RecipeTextParser.parse(text);

        assertTrue(result.ingredients.isEmpty());
        assertEquals(2, result.steps.size());
        assertEquals("Chuẩn bị nồi nước sôi.", result.steps.get(0).content);
    }

    @Test
    public void parse_emptyOrNull_returnsEmptyResult() {
        assertEquals("", RecipeTextParser.parse(null).name);
        RecipeTextParser.Result result = RecipeTextParser.parse("\n  \n---\n");
        assertEquals("", result.name);
        assertTrue(result.ingredients.isEmpty());
        assertTrue(result.steps.isEmpty());
    }

    @Test
    public void parseIngredient_variousFormats() {
        assertIngredient(RecipeTextParser.parseIngredient("1 1/2 muỗng canh nước mắm"), "Nước mắm", 1.5, "muỗng canh");
        assertIngredient(RecipeTextParser.parseIngredient("Nước dừa 1,5 lít"), "Nước dừa", 1.5, "lít");
        assertIngredient(RecipeTextParser.parseIngredient("Hành lá: 2 nhánh"), "Hành lá", 2, "nhánh");
        assertIngredient(RecipeTextParser.parseIngredient("2 con gà"), "Gà", 2, "con");
        // "g" phải là nguyên từ: "2 gà" không có đơn vị
        assertIngredient(RecipeTextParser.parseIngredient("2 gà ta"), "Gà ta", 2, "");
        // Khoảng "2-3" → lấy số đầu
        assertIngredient(RecipeTextParser.parseIngredient("2-3 trái ớt"), "Ớt", 2, "trái");
        assertIngredient(RecipeTextParser.parseIngredient("Đường tùy thích"), "Đường", 0, "");
        // Đơn vị đọc thiếu dấu vẫn nhận ra, giữ nguyên chữ như trong ảnh
        assertIngredient(RecipeTextParser.parseIngredient("2 qua trứng"), "Trứng", 2, "qua");
        assertNull(RecipeTextParser.parseIngredient("  - "));
    }

    @Test
    public void parseMinutes_variousFormats() {
        assertEquals(35, RecipeTextParser.parseMinutes(VietnameseText.normalize("Thời gian: 35 phút")));
        assertEquals(90, RecipeTextParser.parseMinutes(VietnameseText.normalize("1 giờ 30 phút")));
        assertEquals(90, RecipeTextParser.parseMinutes(VietnameseText.normalize("1,5 tiếng")));
        assertEquals(90, RecipeTextParser.parseMinutes(VietnameseText.normalize("1h30")));
        assertEquals(45, RecipeTextParser.parseMinutes(VietnameseText.normalize("45p")));
        assertEquals(10, RecipeTextParser.parseMinutes(VietnameseText.normalize("hầm 10-15 phút")));
        // "phần" không phải "p" (phút), "hành" không phải "h" (giờ)
        assertEquals(0, RecipeTextParser.parseMinutes(VietnameseText.normalize("chia 2 phần, thêm 3 hành")));
    }

    @Test
    public void vietnameseText_foldKeepsLength() {
        String text = "Khẩu phần: 4 NGƯỜI, đĩa";
        String folded = VietnameseText.fold(text);
        assertEquals(text.length(), folded.length());
        assertEquals("khau phan: 4 nguoi, dia", folded);
        assertEquals("trung ga", VietnameseText.normalize("  Trứng gà "));
    }

    private static void assertIngredient(Ingredient ingredient, String name, double quantity, String unit) {
        assertEquals(name, ingredient.name);
        assertEquals(quantity, ingredient.quantity, DELTA);
        assertEquals(unit, ingredient.unit);
    }

    private static void assertStep(Step step, String content, int timerMinutes) {
        assertEquals(content, step.content);
        assertEquals(timerMinutes, step.timerMinutes);
    }
}
