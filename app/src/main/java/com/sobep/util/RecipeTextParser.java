package com.sobep.util;

import androidx.annotation.Nullable;

import com.sobep.data.local.entity.Ingredient;
import com.sobep.data.local.entity.Step;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tách đoạn chữ quét được (ML Kit) thành các phần của công thức:
 * tên món, thời gian nấu, khẩu phần, danh sách nguyên liệu (số lượng + đơn vị + tên), các bước nấu (+ số phút hẹn giờ).
 *
 * Cách làm: đọc TỪNG DÒNG từ trên xuống, nhớ đang ở phần nào (chưa vào phần nào / Nguyên liệu / Cách làm).
 * - Dòng tiêu đề ("NGUYÊN LIỆU", "Cách làm:"...) → đổi phần.
 * - Dòng "Thời gian: 35 phút - Khẩu phần: 4 người" → lấy số phút, số người.
 * - Dòng có chữ đầu tiên (chưa vào phần nào) → tên món.
 * - Trong phần Nguyên liệu: "500 g cá lóc", "Cà chua: 2 quả", "Hành lá 2 nhánh", "Muối vừa ăn".
 * - Trong phần Cách làm: "1. ...", "Bước 2: ...", "- ..." mở bước mới; dòng bị ngắt giữa câu thì nối vào bước trước.
 *
 * So khớp luôn chạy trên chuỗi KHÔNG DẤU (VietnameseText.fold), nên ML Kit đọc sai dấu ("Khấu phần", "Cách lảm")
 * vẫn nhận ra. fold giữ nguyên độ dài chuỗi → tìm vị trí trên chuỗi không dấu rồi cắt trên chuỗi gốc để giữ dấu.
 *
 * Lớp Java thuần (không dùng Context / View) → kiểm thử được bằng unit test chạy trên máy tính (RecipeTextParserTest).
 */
public final class RecipeTextParser {

    /** Kết quả tách. Phần nào không tìm thấy thì để giá trị rỗng / 0. */
    public static final class Result {
        public String name = "";
        // 0 = không thấy thời gian nấu
        public int cookTimeMinutes;
        // 0 = không thấy khẩu phần
        public int servings;
        public final List<Ingredient> ingredients = new ArrayList<>();
        public final List<Step> steps = new ArrayList<>();
        // Các dòng không xếp được vào đâu (vd lời giới thiệu món) → đưa vào ô Ghi chú
        public final List<String> otherLines = new ArrayList<>();
    }

    /** Phần của công thức đang đọc tới. */
    private enum Section { NONE, INGREDIENTS, STEPS }

    /** Kiểu ký hiệu đầu dòng. */
    private enum Marker { NONE, BULLET, NUMBERED }

    // Từ khoá tiêu đề (viết không dấu, chữ thường)
    private static final String[] INGREDIENT_HEADERS = {"nguyen lieu", "thanh phan", "chuan bi"};
    private static final String[] STEP_HEADERS = {
            "cach lam", "cach nau", "cach che bien", "che bien", "cac buoc", "thuc hien", "huong dan", "so che"};
    // Dòng bắt đầu bằng các từ này là dòng thông tin (thời gian, khẩu phần), không phải tên món / nguyên liệu
    private static final String[] INFO_PREFIXES = {"thoi gian", "khau phan", "so nguoi", "so phan an", "phan an"};

    // Đơn vị (không dấu). Cụm dài đặt TRƯỚC cụm ngắn: "muong canh" phải được thử trước "muong"
    private static final String[] UNITS = {
            "muong canh", "muong ca phe", "muong cafe", "thia canh", "thia ca phe", "thia cafe",
            "muong", "thia", "kilogram", "kg", "gram", "gr", "mg", "g", "lit", "ml", "l",
            "chen", "bat", "coc", "ly", "qua", "trai", "cu", "tep", "nhanh", "bo", "la",
            "mieng", "con", "cay", "goi", "hop", "lon", "khuc", "lat", "tsp", "tbsp"};

    // Số lượng: "1 1/2", "1/2", "500", "1,5" / "1.5"; khoảng "2-3" thì lấy số đầu
    private static final String QTY =
            "(\\d+\\s+\\d+/\\d+|\\d+/\\d+|\\d+(?:[.,]\\d+)?)(?:\\s*[-–]\\s*\\d+(?:[.,]\\d+)?)?";
    private static final Pattern LEADING_QTY = Pattern.compile("^" + QTY + "\\s*");
    // "Hành lá 2 nhánh" → (Hành lá)(2)(nhánh); đơn vị được kiểm tra lại với bảng UNITS
    private static final Pattern TRAILING_QTY =
            Pattern.compile("^(.+?)[\\s:,\\-–]+" + QTY + "\\s*([a-z ]*?)\\s*\\.?$");
    private static final Pattern TO_TASTE =
            Pattern.compile("[\\s,:\\-–]*(?:vua an|tuy thich|tuy y|tuy khau vi)\\s*\\.?$");

    // Ký hiệu đầu dòng
    private static final Pattern BULLET = Pattern.compile("^[-–—•*+·●○▪►]+\\s*");
    // "1. Làm sạch", "2) ...", "1. 500 g cá" – nhưng KHÔNG nhận "1.5 kg" (số thập phân)
    private static final Pattern NUMBERED = Pattern.compile("^\\d{1,2}\\s*[.)](?:\\s+|(?=\\p{L}))");
    // "Bước 1:", "B2.", "bước 3 -"
    private static final Pattern STEP_WORD = Pattern.compile("^(?:buoc|b)\\s*\\d{1,2}\\s*[:.)\\-–]?\\s*");
    // Ký hiệu trước tiêu đề: "I. NGUYÊN LIỆU", "A) Cách làm", "2. Cách làm"
    private static final Pattern HEADER_PREFIX = Pattern.compile("^(?:[ivx]{1,4}|[a-e]|\\d{1,2})\\s*[.)/\\-]\\s*");
    // "Tên món: Canh chua", "Món: ..."
    private static final Pattern NAME_PREFIX = Pattern.compile("^(?:ten mon(?: an)?|mon an|mon|cong thuc)\\s*:\\s*");

    // Thời gian: "1 giờ 30 phút", "1,5 tiếng", "1h30", "35 phút", "35p", "10-15 phút" (lấy số đầu)
    private static final Pattern HOURS = Pattern.compile(
            "(\\d+(?:[.,]\\d+)?)(?:\\s*[-–]\\s*\\d+(?:[.,]\\d+)?)?\\s*(?:gio|tieng|h)(?![a-z])"
                    + "(?:\\s*(\\d{1,2})(?!\\d)(?!\\s*(?:phut|p)(?![a-z])))?");
    private static final Pattern MINUTES = Pattern.compile("(\\d+)(?:\\s*[-–]\\s*\\d+)?\\s*(?:phut|p)(?![a-z])");
    // Khẩu phần: "4 người", "4-6 người", "4 phần", "Khẩu phần: 4"
    private static final Pattern SERVINGS = Pattern.compile("(\\d+)(?:\\s*[-–]\\s*\\d+)?\\s*(?:nguoi|phan)(?![a-z])");
    private static final Pattern SERVINGS_AFTER_LABEL = Pattern.compile("khau phan\\s*:?\\s*(\\d+)");
    // Cả dòng chỉ là khẩu phần: "Cho 4 người", "4 người ăn"
    private static final Pattern SERVINGS_LINE =
            Pattern.compile("^(?:cho\\s+)?\\d+(?:\\s*[-–]\\s*\\d+)?\\s*nguoi(?:\\s+an)?\\s*[.)]?$");

    // ML Kit hay đọc chữ "l" thường thành "I" hoa: "cá Ióc". Chữ "I" hoa đứng giữa câu, sau nó là chữ thường
    // → gần như chắc chắn là "l" (tiếng Việt không có từ viết hoa kiểu đó giữa câu)
    private static final Pattern OCR_CAPITAL_I = Pattern.compile("(?<=[\\p{L}\\d,] )I(?=\\p{Ll})");

    private static final Locale VIETNAMESE = Locale.forLanguageTag("vi");

    private RecipeTextParser() {
        // Lớp tiện ích, không tạo đối tượng
    }

    // =====================================================================================
    // Hàm chính
    // =====================================================================================

    public static Result parse(@Nullable String text) {
        Result result = new Result();
        if (text == null) {
            return result;
        }

        Section section = Section.NONE;
        // true = đang ở phần được ĐOÁN (công thức không có dòng tiêu đề), false = có tiêu đề rõ ràng
        boolean guessedSection = false;

        for (String rawLine : text.split("\\r?\\n")) {
            String line = fixOcrErrors(VietnameseText.toNfc(rawLine).trim());
            if (!containsLetterOrDigit(line)) {
                continue;
            }
            String folded = VietnameseText.fold(line);

            // 1. Dòng tiêu đề → đổi phần
            Section header = detectHeader(line, folded);
            if (header != null) {
                section = header;
                guessedSection = false;
                handleHeaderLine(result, header, line, folded);
                continue;
            }

            // 2. Dòng thông tin thời gian / khẩu phần
            if (isInfoLine(folded)) {
                readInfo(result, folded);
                continue;
            }

            // 3. Bỏ ký hiệu đầu dòng ("- ", "1. ", "Bước 2:")
            Marker marker = Marker.NONE;
            int contentStart = 0;
            Matcher m;
            if ((m = STEP_WORD.matcher(folded)).find() || (m = NUMBERED.matcher(folded)).find()) {
                marker = Marker.NUMBERED;
                contentStart = m.end();
            } else if ((m = BULLET.matcher(folded)).find()) {
                marker = Marker.BULLET;
                contentStart = m.end();
            }
            String content = line.substring(contentStart).trim();

            // 4. Chưa vào phần nào: tên món, hoặc đoán phần theo ký hiệu đầu dòng
            if (section == Section.NONE) {
                boolean startsWithQuantity = LEADING_QTY.matcher(folded).find();
                if (result.name.isEmpty() && marker == Marker.NONE && !startsWithQuantity) {
                    result.name = cleanName(line, folded);
                    continue;
                }
                if (marker == Marker.NUMBERED) {
                    section = Section.STEPS;
                    guessedSection = true;
                } else if (marker == Marker.BULLET || startsWithQuantity) {
                    section = Section.INGREDIENTS;
                    guessedSection = true;
                } else {
                    result.otherLines.add(line);
                    continue;
                }
            } else if (section == Section.INGREDIENTS && guessedSection && marker == Marker.NUMBERED) {
                // Không có tiêu đề: sau các dòng "- nguyên liệu" là các dòng "1. bước" → sang phần Cách làm
                section = Section.STEPS;
            }

            // 5. Thêm vào phần đang đọc
            if (section == Section.INGREDIENTS) {
                // Dòng tiêu đề nhỏ trong phần nguyên liệu, vd "Nước dùng:" → bỏ qua
                if (content.endsWith(":") && !containsDigit(content)) {
                    continue;
                }
                addIngredient(result, content);
            } else {
                addStepLine(result, content, marker != Marker.NONE);
            }
        }

        // Số phút hẹn giờ của từng bước: "nấu khoảng 10 phút" → 10
        for (Step step : result.steps) {
            step.content = step.content.trim();
            step.timerMinutes = parseMinutes(VietnameseText.fold(step.content));
        }
        // Bỏ các bước rỗng (vd dòng "Bước 3" đứng một mình ở cuối ảnh)
        result.steps.removeIf(step -> step.content.isEmpty());
        return result;
    }

    // =====================================================================================
    // Tiêu đề và dòng thông tin
    // =====================================================================================

    /** Trả về phần mà dòng tiêu đề này mở ra, hoặc null nếu không phải dòng tiêu đề. */
    @Nullable
    private static Section detectHeader(String line, String folded) {
        Matcher prefix = HEADER_PREFIX.matcher(folded);
        String rest = prefix.find() ? folded.substring(prefix.end()) : folded;
        if (matchesHeader(rest, INGREDIENT_HEADERS, line)) {
            return Section.INGREDIENTS;
        }
        if (matchesHeader(rest, STEP_HEADERS, line)) {
            return Section.STEPS;
        }
        return null;
    }

    /**
     * Dòng bắt đầu bằng từ khoá VÀ trông giống tiêu đề: chỉ có từ khoá, hoặc từ khoá + ":" / "(",
     * hoặc cả dòng viết HOA, hoặc kết thúc bằng ":". Nhờ vậy câu "Chuẩn bị nồi nước sôi." không bị coi là tiêu đề.
     */
    private static boolean matchesHeader(String folded, String[] keywords, String line) {
        for (String keyword : keywords) {
            if (!folded.startsWith(keyword)) {
                continue;
            }
            String after = folded.substring(keyword.length()).trim();
            if (after.isEmpty() || after.startsWith(":") || after.startsWith("(")
                    || after.startsWith("-") || after.startsWith("–")
                    || line.endsWith(":") || isAllUpperCase(line)) {
                return true;
            }
        }
        return false;
    }

    /** Tiêu đề có kèm nội dung: "Nguyên liệu (cho 4 người): 500 g cá, 2 quả cà chua". */
    private static void handleHeaderLine(Result result, Section header, String line, String folded) {
        if (result.servings == 0) {
            result.servings = parseServings(folded);
        }
        int colon = line.indexOf(':');
        if (colon < 0) {
            return;
        }
        String inline = line.substring(colon + 1).trim();
        if (inline.isEmpty()) {
            return;
        }
        if (header == Section.INGREDIENTS) {
            for (String part : inline.split("[,;]")) {
                addIngredient(result, part.trim());
            }
        } else {
            addStepLine(result, inline, true);
        }
    }

    private static boolean isInfoLine(String folded) {
        for (String prefix : INFO_PREFIXES) {
            if (folded.startsWith(prefix)) {
                return true;
            }
        }
        return SERVINGS_LINE.matcher(folded).matches();
    }

    /** Lấy thời gian / khẩu phần từ dòng thông tin. Gặp nhiều lần thì giữ giá trị đầu tiên. */
    private static void readInfo(Result result, String folded) {
        if (result.cookTimeMinutes == 0) {
            result.cookTimeMinutes = parseMinutes(folded);
        }
        if (result.servings == 0) {
            result.servings = parseServings(folded);
        }
    }

    /** "1 giờ 30 phút" → 90, "1,5 tiếng" → 90, "1h30" → 90, "35p" → 35. Không thấy → 0. Chuỗi vào đã bỏ dấu. */
    static int parseMinutes(String folded) {
        double total = 0;
        Matcher hours = HOURS.matcher(folded);
        while (hours.find()) {
            total += parseNumber(hours.group(1)) * 60;
            if (hours.group(2) != null) {
                total += Integer.parseInt(hours.group(2));
            }
        }
        Matcher minutes = MINUTES.matcher(folded);
        while (minutes.find()) {
            total += Integer.parseInt(minutes.group(1));
        }
        return (int) Math.round(total);
    }

    /** "4 người", "4-6 người", "Khẩu phần: 4" → 4. Không thấy → 0. Chuỗi vào đã bỏ dấu. */
    static int parseServings(String folded) {
        Matcher m = SERVINGS.matcher(folded);
        if (m.find()) {
            return Integer.parseInt(m.group(1));
        }
        m = SERVINGS_AFTER_LABEL.matcher(folded);
        return m.find() ? Integer.parseInt(m.group(1)) : 0;
    }

    // =====================================================================================
    // Tên món
    // =====================================================================================

    /** Bỏ chữ "Tên món:" ở đầu; tên viết HOA toàn bộ → viết hoa chữ đầu ("CANH CHUA" → "Canh chua"). */
    private static String cleanName(String line, String folded) {
        Matcher prefix = NAME_PREFIX.matcher(folded);
        String name = prefix.find() ? line.substring(prefix.end()).trim() : line;
        if (isAllUpperCase(name)) {
            name = name.toLowerCase(VIETNAMESE);
        }
        return capitalize(trimPunctuation(name));
    }

    // =====================================================================================
    // Nguyên liệu
    // =====================================================================================

    private static void addIngredient(Result result, String text) {
        Ingredient ingredient = parseIngredient(text);
        if (ingredient != null) {
            result.ingredients.add(ingredient);
        }
    }

    /**
     * Một dòng nguyên liệu → Ingredient. Thử lần lượt các mẫu:
     * 1. "Muối vừa ăn"        → tên "Muối", số lượng 0 (hiển thị "vừa ăn")
     * 2. "Cà chua: 2 quả"     → tên trước dấu ":", số lượng + đơn vị sau dấu ":"
     * 3. "500 g cá lóc"       → số lượng + đơn vị đứng đầu
     * 4. "Hành lá 2 nhánh"    → số lượng + đơn vị đứng cuối
     * 5. "Rau om"             → chỉ có tên
     * Trả về null nếu dòng không có tên.
     */
    @Nullable
    static Ingredient parseIngredient(String text) {
        text = VietnameseText.toNfc(text).trim();
        String folded = VietnameseText.fold(text);
        Ingredient ingredient = new Ingredient();

        // 1. "vừa ăn", "tùy thích" ở cuối
        Matcher toTaste = TO_TASTE.matcher(folded);
        if (toTaste.find()) {
            ingredient.name = text.substring(0, toTaste.start());
            return finishIngredient(ingredient);
        }

        // 2. "Tên: số lượng đơn vị"
        int colon = text.indexOf(':');
        if (colon > 0 && readLeadingQuantity(ingredient, text.substring(colon + 1).trim())) {
            String extra = ingredient.name;
            ingredient.name = text.substring(0, colon) + (extra.isEmpty() ? "" : " " + extra);
            return finishIngredient(ingredient);
        }

        // 3. "số lượng đơn vị tên"
        if (readLeadingQuantity(ingredient, text)) {
            return finishIngredient(ingredient);
        }

        // 4. "tên số lượng đơn vị"
        Matcher trailing = TRAILING_QTY.matcher(folded);
        if (trailing.find()) {
            String unitFolded = trailing.group(3).trim();
            if (unitFolded.isEmpty() || isUnit(unitFolded)) {
                ingredient.name = text.substring(0, trailing.end(1));
                ingredient.quantity = parseNumber(trailing.group(2));
                ingredient.unit = text.substring(trailing.start(3), trailing.end(3)).trim().toLowerCase(VIETNAMESE);
                return finishIngredient(ingredient);
            }
        }

        // 5. Chỉ có tên
        ingredient.name = text;
        return finishIngredient(ingredient);
    }

    /**
     * Đọc "số lượng [đơn vị] [phần còn lại]" ở đầu chuỗi. Thành công → ghi quantity, unit,
     * và ghi phần còn lại vào ingredient.name (sẽ là tên nguyên liệu, hoặc chữ thêm như "to" trong "2 quả to").
     */
    private static boolean readLeadingQuantity(Ingredient ingredient, String text) {
        String folded = VietnameseText.fold(text);
        Matcher m = LEADING_QTY.matcher(folded);
        if (!m.find()) {
            return false;
        }
        int unitStart = m.end();
        int unitLength = matchUnit(folded, unitStart);
        ingredient.quantity = parseNumber(m.group(1));
        ingredient.unit = text.substring(unitStart, unitStart + unitLength).toLowerCase(VIETNAMESE);
        ingredient.name = text.substring(unitStart + unitLength).trim();
        return true;
    }

    /** Tại vị trí start có đơn vị nào không (phải là nguyên từ: "g" khớp "500 g cá" nhưng không khớp "500 gà"). */
    private static int matchUnit(String folded, int start) {
        for (String unit : UNITS) {
            int end = start + unit.length();
            if (folded.startsWith(unit, start)
                    && (end == folded.length() || !Character.isLetter(folded.charAt(end)))) {
                return unit.length();
            }
        }
        return 0;
    }

    private static boolean isUnit(String folded) {
        for (String unit : UNITS) {
            if (unit.equals(folded)) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    private static Ingredient finishIngredient(Ingredient ingredient) {
        ingredient.name = capitalize(trimPunctuation(ingredient.name));
        return ingredient.name.isEmpty() ? null : ingredient;
    }

    // =====================================================================================
    // Các bước nấu
    // =====================================================================================

    /**
     * Dòng có ký hiệu đầu ("1.", "Bước 2:", "-") → bước mới.
     * Không có ký hiệu: nếu bước trước chưa hết câu hoặc dòng này bắt đầu bằng chữ thường
     * → đây là phần bị ngắt dòng của bước trước, nối vào. Ngược lại → bước mới.
     */
    private static void addStepLine(Result result, String content, boolean hasMarker) {
        List<Step> steps = result.steps;
        if (!hasMarker && !steps.isEmpty()) {
            Step last = steps.get(steps.size() - 1);
            if (!endsSentence(last.content) || startsWithLowerCase(content)) {
                last.content = (last.content + " " + content).trim();
                return;
            }
        }
        Step step = new Step();
        step.content = content;
        steps.add(step);
    }

    private static boolean endsSentence(String text) {
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            return false;
        }
        char last = trimmed.charAt(trimmed.length() - 1);
        return last == '.' || last == '!' || last == '?' || last == ':';
    }

    // =====================================================================================
    // Tiện ích chữ / số
    // =====================================================================================

    /** "1 1/2" → 1.5, "1/2" → 0.5, "1,5" → 1.5, "500" → 500. */
    static double parseNumber(String text) {
        try {
            String value = text.trim();
            if (value.contains("/")) {
                double whole = 0;
                String[] parts = value.split("\\s+");
                if (parts.length == 2) {
                    whole = Double.parseDouble(parts[0]);
                    value = parts[1];
                }
                String[] fraction = value.split("/");
                double denominator = Double.parseDouble(fraction[1]);
                return denominator == 0 ? whole : whole + Double.parseDouble(fraction[0]) / denominator;
            }
            return Double.parseDouble(value.replace(',', '.'));
        } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
            return 0;
        }
    }

    static String fixOcrErrors(String line) {
        return OCR_CAPITAL_I.matcher(line).replaceAll("l");
    }

    /** Có ít nhất một chữ cái và không có chữ thường nào: "NGUYÊN LIỆU" → true, "Nguyên liệu" → false. */
    private static boolean isAllUpperCase(String text) {
        boolean hasLetter = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isLowerCase(c)) {
                return false;
            }
            if (Character.isLetter(c)) {
                hasLetter = true;
            }
        }
        return hasLetter;
    }

    private static boolean startsWithLowerCase(String text) {
        return !text.isEmpty() && Character.isLowerCase(text.charAt(0));
    }

    private static boolean containsLetterOrDigit(String text) {
        for (int i = 0; i < text.length(); i++) {
            if (Character.isLetterOrDigit(text.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsDigit(String text) {
        for (int i = 0; i < text.length(); i++) {
            if (Character.isDigit(text.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    /** Bỏ dấu câu thừa ở 2 đầu: "- Cà chua:" → "Cà chua". */
    private static String trimPunctuation(String text) {
        return text.replaceAll("^[\\s\\-–—•*+:,;.]+|[\\s\\-–—:,;.]+$", "");
    }

    private static String capitalize(String text) {
        if (text.isEmpty()) {
            return text;
        }
        return text.substring(0, 1).toUpperCase(VIETNAMESE) + text.substring(1);
    }
}
