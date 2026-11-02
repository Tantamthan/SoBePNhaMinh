package com.sobep.util;

import androidx.annotation.Nullable;

import java.text.Normalizer;

/**
 * Xử lý chữ tiếng Việt dùng chung:
 * - Khám phá: tìm không dấu ("trung" vẫn ra "trứng").
 * - Quét công thức: nhận ra tiêu đề "Nguyên liệu", "Cách làm"... kể cả khi ML Kit đọc sai dấu
 *   (vd "Khấu phần" thay vì "Khẩu phần").
 */
public final class VietnameseText {

    private VietnameseText() {
        // Lớp tiện ích, không tạo đối tượng
    }

    /**
     * Bỏ dấu + chữ thường + bỏ khoảng trắng 2 đầu: "Trứng gà " → "trung ga".
     * Dùng để so sánh / tìm kiếm.
     */
    public static String normalize(@Nullable String text) {
        if (text == null) {
            return "";
        }
        return fold(toNfc(text)).trim();
    }

    /**
     * Bỏ dấu + chữ thường, xử lý TỪNG ký tự nên chuỗi kết quả dài ĐÚNG BẰNG chuỗi vào:
     * ký tự thứ i của kết quả ứng với ký tự thứ i của chuỗi gốc.
     * Nhờ vậy RecipeTextParser tìm vị trí trên chuỗi không dấu rồi cắt đúng chỗ đó trên chuỗi gốc (giữ dấu).
     * Chuỗi vào nên ở dạng NFC (chữ và dấu gộp thành 1 ký tự, vd "ứ") – gọi toNfc trước.
     */
    public static String fold(String text) {
        StringBuilder builder = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            builder.append(foldChar(text.charAt(i)));
        }
        return builder.toString();
    }

    /** Gộp chữ và dấu thành 1 ký tự (dạng NFC). Chữ từ ML Kit / bàn phím thường đã ở dạng này. */
    public static String toNfc(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFC);
    }

    /**
     * Một ký tự → một ký tự không dấu, chữ thường.
     * NFD tách "ứ" thành "u" + các dấu; giữ lại đúng 1 ký tự gốc "u". Riêng "đ" không tách được nên đổi tay.
     */
    private static char foldChar(char c) {
        if (c == 'đ' || c == 'Đ') {
            return 'd';
        }
        String decomposed = Normalizer.normalize(String.valueOf(c), Normalizer.Form.NFD);
        char base = decomposed.isEmpty() ? c : decomposed.charAt(0);
        // Character.toLowerCase(char) luôn trả về đúng 1 ký tự (String.toLowerCase có thể đổi độ dài)
        return Character.toLowerCase(base);
    }
}
