package com.sobep.util;

import java.util.Calendar;
import java.util.Locale;

/**
 * Các hàm xử lý ngày tháng dùng chung.
 */
public final class DateUtils {

    private DateUtils() {
        // Lớp chỉ chứa hàm tĩnh, không cho tạo đối tượng
    }

    /**
     * Trả về ngày Thứ Hai của tuần cách tuần hiện tại weekOffset tuần
     * (0 = tuần này, -1 = tuần trước, 1 = tuần sau). Giờ được đặt về 00:00.
     */
    public static Calendar getMondayOfWeek(int weekOffset) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);

        // Số ngày đã qua kể từ Thứ Hai: Thứ Hai = 0, Thứ Ba = 1, …, Chủ nhật = 6
        int daysSinceMonday = (calendar.get(Calendar.DAY_OF_WEEK) - Calendar.MONDAY + 7) % 7;
        calendar.add(Calendar.DAY_OF_MONTH, -daysSinceMonday + weekOffset * 7);
        return calendar;
    }

    /** Khoảng ngày của tuần, vd "6/10 – 12/10/2026" hoặc "29/12 – 4/1/2027" khi qua năm mới. */
    public static String formatWeekRange(Calendar monday) {
        Calendar sunday = (Calendar) monday.clone();
        sunday.add(Calendar.DAY_OF_MONTH, 6);
        return formatDayMonth(monday) + " – " + formatDayMonth(sunday) + "/" + sunday.get(Calendar.YEAR);
    }

    /** Số thứ tự tuần trong năm, tuần bắt đầu từ Thứ Hai. */
    public static int getWeekNumber(Calendar monday) {
        Calendar calendar = (Calendar) monday.clone();
        calendar.setFirstDayOfWeek(Calendar.MONDAY);
        calendar.setMinimalDaysInFirstWeek(4);
        return calendar.get(Calendar.WEEK_OF_YEAR);
    }

    /**
     * Ngày dạng "yyyy-MM-dd" (vd "2026-10-21") để lưu vào cột meal_plans.date.
     * Có số 0 đứng đầu nên so sánh chuỗi cũng đúng thứ tự ngày.
     */
    public static String toDateKey(Calendar calendar) {
        return String.format(Locale.US, "%04d-%02d-%02d",
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH) + 1,
                calendar.get(Calendar.DAY_OF_MONTH));
    }

    /** Ngược lại với toDateKey: "2026-10-21" → Calendar (00:00 ngày đó). */
    public static Calendar fromDateKey(String dateKey) {
        String[] parts = dateKey.split("-");
        Calendar calendar = Calendar.getInstance();
        calendar.clear();
        calendar.set(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]) - 1, Integer.parseInt(parts[2]));
        return calendar;
    }

    /** Cộng thêm số ngày vào ngày dạng "yyyy-MM-dd", vd ("2026-10-26", 6) → "2026-11-01". */
    public static String addDays(String dateKey, int days) {
        Calendar calendar = fromDateKey(dateKey);
        calendar.add(Calendar.DAY_OF_MONTH, days);
        return toDateKey(calendar);
    }

    /** Vị trí trong tuần: Thứ Hai = 0, …, Chủ nhật = 6 (dùng với string-array day_names). */
    public static int dayIndex(Calendar calendar) {
        return (calendar.get(Calendar.DAY_OF_WEEK) - Calendar.MONDAY + 7) % 7;
    }

    /** Ngày hôm nay dạng "yyyy-MM-dd", để biết thẻ ngày nào là "Hôm nay". */
    public static String todayKey() {
        return toDateKey(Calendar.getInstance());
    }

    /** Ngày/tháng ngắn, vd "28/9". */
    public static String formatDayMonth(Calendar calendar) {
        return calendar.get(Calendar.DAY_OF_MONTH) + "/" + (calendar.get(Calendar.MONTH) + 1);
    }
}
