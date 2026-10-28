package com.sobep.util;

import android.content.Context;

import com.sobep.R;

import java.text.DecimalFormat;

/**
 * Định dạng số lượng + đơn vị để hiển thị: "250 g", "0,5 bó", "2 quả".
 * Dùng chung cho Chi tiết món, Khám phá và Đi chợ.
 */
public final class QuantityFormat {

    // Tối đa 2 chữ số thập phân, bỏ số 0 thừa: 250.0 → "250", 0.125 → "0,13" (dấu phẩy theo ngôn ngữ máy)
    private static final DecimalFormat NUMBER_FORMAT = new DecimalFormat("#.##");

    private QuantityFormat() {
        // Lớp tiện ích, không tạo đối tượng
    }

    /** Số lượng 0 → "vừa ăn"; có đơn vị → "250 g"; không đơn vị → "250". */
    public static String format(Context context, double quantity, String unit) {
        if (quantity <= 0) {
            return context.getString(R.string.qty_to_taste);
        }
        String number = NUMBER_FORMAT.format(quantity);
        String trimmedUnit = unit == null ? "" : unit.trim();
        if (trimmedUnit.isEmpty()) {
            return number;
        }
        return context.getString(R.string.format_quantity, number, trimmedUnit);
    }
}
