package com.sobep.util;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * CN15 (bước 8d): quy định "phân giải" ảnh món trước khi đưa lên Firestore (không dùng Firebase Storage
 * vì Storage bắt buộc gói Blaze trả phí).
 *
 * Mỗi bài cộng đồng có ảnh gồm 2 bản, đều là JPEG lưu kiểu Blob (byte thô, không Base64 vì Base64 nặng thêm 33%):
 * - Ảnh nhỏ (thumb): cạnh dài 240 px, ≤ 30 KB, nằm ngay trong document bài → hiện trong danh sách.
 * - Ảnh lớn: cạnh dài 800 px, ≤ 300 KB, nằm ở document riêng community_recipes/{postId}/photo/main
 *   → chỉ tải khi mở bảng xem trước hoặc lưu vào sổ.
 * Một document Firestore tối đa 1 MiB nên 300 KB còn xa giới hạn. Gói miễn phí có 1 GiB ≈ 10.000 ảnh.
 * Giới hạn byte phải khớp với firestore.rules (máy chủ kiểm tra lại lần nữa).
 *
 * Java thuần (không dùng Bitmap) nên kiểm thử được bằng unit test (CloudPhotoTest).
 * Phần đọc / nén Bitmap thật nằm ở ImageStorage.compressForUpload.
 */
public final class CloudPhoto {

    public static final int PHOTO_MAX_SIZE = 800;
    public static final int PHOTO_QUALITY = 70;
    public static final int PHOTO_MAX_BYTES = 300 * 1024;

    public static final int THUMB_MAX_SIZE = 240;
    public static final int THUMB_QUALITY = 60;
    public static final int THUMB_MAX_BYTES = 30 * 1024;

    /** Chất lượng JPEG thấp nhất chấp nhận; thấp hơn ảnh bị vỡ khối rõ. */
    public static final int MIN_QUALITY = 40;
    /** Mỗi lần nén vẫn quá giới hạn thì giảm chất lượng đi 10 rồi nén lại. */
    public static final int QUALITY_STEP = 10;

    private CloudPhoto() {
    }

    /**
     * Kích thước mới (rộng, cao) để cạnh dài nhất không quá maxSize, giữ nguyên tỉ lệ.
     * Ảnh đã nhỏ hơn thì giữ nguyên (không phóng to). Mỗi cạnh tối thiểu 1 px.
     */
    public static int[] fitInside(int width, int height, int maxSize) {
        int longSide = Math.max(width, height);
        if (longSide <= maxSize || longSide <= 0) {
            return new int[]{width, height};
        }
        float scale = (float) maxSize / longSide;
        return new int[]{
                Math.max(1, Math.round(width * scale)),
                Math.max(1, Math.round(height * scale))
        };
    }

    /**
     * CN14 (đồng bộ ảnh món): mã ảnh = tên file ảnh trong máy, vd "recipe_1731400000000.jpg".
     * Máy khác tải ảnh về sẽ lưu đúng tên đó → hai máy cùng mã = cùng một ảnh, không tải / gửi lại.
     * Không có ảnh, hoặc tên file lạ → null.
     */
    @Nullable
    public static String photoIdOf(@Nullable String path) {
        if (path == null) {
            return null;
        }
        int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        String name = path.substring(slash + 1);
        return isSafePhotoId(name) ? name : null;
    }

    /**
     * Mã ảnh đọc từ Firestore sẽ được dùng làm tên file → chỉ nhận chữ, số, "_", "-" và đuôi ".jpg"
     * (chặn kiểu "../../databases/x" ghi ra ngoài thư mục ảnh).
     */
    public static boolean isSafePhotoId(@Nullable String photoId) {
        return photoId != null && photoId.matches("[A-Za-z0-9_-]{1,64}\\.jpg");
    }

    /**
     * Các mức chất lượng sẽ thử lần lượt, vd bắt đầu 70 → [70, 60, 50, 40].
     * Mức bắt đầu thấp hơn MIN_QUALITY thì chỉ thử đúng mức đó.
     */
    public static List<Integer> qualitySteps(int startQuality) {
        List<Integer> steps = new ArrayList<>();
        int quality = Math.min(100, startQuality);
        steps.add(quality);
        while (quality - QUALITY_STEP >= MIN_QUALITY) {
            quality -= QUALITY_STEP;
            steps.add(quality);
        }
        return steps;
    }
}
