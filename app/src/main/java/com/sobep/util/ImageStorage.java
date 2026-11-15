package com.sobep.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;
import androidx.exifinterface.media.ExifInterface;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Các việc với file ảnh:
 * - Tạo file ảnh tạm cho app Camera ghi vào (qua FileProvider), dùng ở màn Quét công thức và màn Thêm món.
 * - Đọc ảnh thu nhỏ để hiển thị (ảnh camera 12 MP mà đọc nguyên kích thước dễ hết bộ nhớ).
 * - Lưu ảnh món ăn lâu dài vào bộ nhớ riêng của app (files/recipe_photos), xoá ảnh món khi không dùng nữa.
 * - CN15 (8d): nén ảnh món thành byte JPEG nhỏ để đăng lên Cộng đồng, và ngược lại (byte → Bitmap / file).
 *   CN14 dùng lại đúng cách này để đồng bộ ảnh món cá nhân giữa các máy.
 */
public final class ImageStorage {

    // Thư mục ảnh chụp tạm trong cache; trùng với <cache-path path="scan/"> trong res/xml/file_paths.xml
    private static final String CAMERA_DIR = "scan";
    // Thư mục ảnh món ăn lâu dài trong files/ (bộ nhớ riêng của app, không cần xin quyền, gỡ app thì mất theo)
    private static final String RECIPE_PHOTO_DIR = "recipe_photos";
    // Ảnh món lưu với cạnh dài tối đa 1280 px, nén JPEG chất lượng 85: rõ nét trên điện thoại, mỗi ảnh chỉ vài trăm KB
    private static final int RECIPE_PHOTO_MAX_SIZE = 1280;
    private static final int RECIPE_PHOTO_QUALITY = 85;

    private ImageStorage() {
        // Lớp tiện ích, không cho tạo đối tượng
    }

    /** Tên authority của FileProvider, phải khớp với khai báo <provider> trong AndroidManifest.xml. */
    private static String authority(Context context) {
        return context.getPackageName() + ".fileprovider";
    }

    /**
     * Tạo một file rỗng trong cache/scan và trả về content:// Uri của nó.
     * App Camera không được ghi thẳng vào thư mục riêng của app mình, nên phải đưa Uri qua FileProvider.
     */
    @NonNull
    public static Uri createCameraPhotoUri(Context context) throws IOException {
        File dir = new File(context.getCacheDir(), CAMERA_DIR);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("Không tạo được thư mục " + dir);
        }
        File file = new File(dir, "camera_" + System.currentTimeMillis() + ".jpg");
        if (!file.exists() && !file.createNewFile()) {
            throw new IOException("Không tạo được file " + file);
        }
        return FileProvider.getUriForFile(context, authority(context), file);
    }

    /**
     * Xoá một ảnh chụp tạm (Uri do createCameraPhotoUri tạo) sau khi đã lưu xong thành ảnh món.
     * FileProvider hỗ trợ delete(): xoá đúng file nằm sau Uri. Chạy ở luồng nền.
     */
    public static void deleteCameraPhoto(Context context, Uri uri) {
        try {
            context.getContentResolver().delete(uri, null, null);
        } catch (IllegalArgumentException | SecurityException e) {
            // Uri không thuộc FileProvider của app (vd ảnh từ bộ chọn ảnh) → không xoá
        }
    }

    /** Xoá hết ảnh chụp tạm trong cache/scan (gọi khi đã dùng xong ảnh). Chạy ở luồng nền. */
    public static void clearCameraPhotos(Context context) {
        File[] files = new File(context.getCacheDir(), CAMERA_DIR).listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            //noinspection ResultOfMethodCallIgnored
            file.delete();
        }
    }

    /**
     * Lưu ảnh món ăn (Uri từ camera hoặc bộ chọn ảnh) thành file JPEG trong files/recipe_photos.
     * Ảnh được thu nhỏ (cạnh dài tối đa 1280 px) và xoay sẵn đúng chiều, nên màn chính và Chi tiết món
     * chỉ cần setImageURI là hiện đúng. Mỗi lần lưu là một file tên mới (theo thời gian).
     * Chạy ở luồng nền. Trả về đường dẫn tuyệt đối của file, lỗi → null.
     */
    @Nullable
    public static String saveRecipePhoto(Context context, Uri uri) {
        Bitmap bitmap = decodePreview(context, uri, RECIPE_PHOTO_MAX_SIZE);
        if (bitmap == null) {
            return null;
        }
        // decodePreview chỉ thu nhỏ theo bậc 1/2, 1/4... → ảnh có thể còn lớn hơn 1280 px, thu nhỏ nốt cho đúng
        bitmap = scaleDown(bitmap, RECIPE_PHOTO_MAX_SIZE);

        File file = newRecipePhotoFile(context);
        if (file == null) {
            bitmap.recycle();
            return null;
        }
        try (OutputStream out = new FileOutputStream(file)) {
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, RECIPE_PHOTO_QUALITY, out)) {
                throw new IOException("Không nén được ảnh");
            }
        } catch (IOException e) {
            //noinspection ResultOfMethodCallIgnored
            file.delete();
            return null;
        } finally {
            bitmap.recycle();
        }
        return file.getAbsolutePath();
    }

    // ---------- Ảnh lên mây (CN15, bước 8d) ----------

    /**
     * "Phân giải" ảnh món trong máy thành mảng byte JPEG nhỏ để gửi lên Firestore:
     * 1. Đọc ảnh thu nhỏ bằng inSampleSize (decodePreview), không nạp nguyên ảnh lớn vào RAM.
     * 2. Thu nhỏ nốt để cạnh dài = maxSize (CloudPhoto.fitInside).
     * 3. Nén JPEG ở chất lượng startQuality; còn lớn hơn maxBytes thì giảm 10 rồi nén lại (thấp nhất 40).
     * Chạy ở luồng nền. Không có file / đọc lỗi / vẫn quá lớn → null (bài sẽ đăng không kèm ảnh).
     */
    @Nullable
    public static byte[] compressForUpload(Context context, @Nullable String path, int maxSize,
                                           int startQuality, int maxBytes) {
        if (path == null || path.isEmpty() || !new File(path).exists()) {
            return null;
        }
        Bitmap bitmap = decodePreview(context, Uri.fromFile(new File(path)), maxSize);
        if (bitmap == null) {
            return null;
        }
        bitmap = scaleDown(bitmap, maxSize);
        try {
            for (int quality : CloudPhoto.qualitySteps(startQuality)) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                if (bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out) && out.size() <= maxBytes) {
                    return out.toByteArray();
                }
            }
            return null;
        } finally {
            bitmap.recycle();
        }
    }

    /**
     * Ghi ảnh tải từ Cộng đồng (byte JPEG) thành file ảnh món trong files/recipe_photos, dùng khi lưu bài vào sổ.
     * Kiểm tra trước là ảnh đọc được (dữ liệu từ mạng có thể hỏng). Chạy ở luồng nền. Lỗi → null.
     */
    @Nullable
    public static String saveRecipePhotoBytes(Context context, @Nullable byte[] data) {
        return saveRecipePhotoBytes(context, data, null);
    }

    /**
     * CN14 (đồng bộ ảnh món): như trên nhưng lưu đúng tên file = mã ảnh trên mây (CloudPhoto.photoIdOf),
     * để lần đồng bộ sau hai máy nhận ra cùng một ảnh. fileName = null hoặc tên lạ → đặt tên mới theo thời gian.
     */
    @Nullable
    public static String saveRecipePhotoBytes(Context context, @Nullable byte[] data, @Nullable String fileName) {
        if (data == null || data.length == 0) {
            return null;
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, options);
        if (options.outWidth <= 0 || options.outHeight <= 0) {
            return null;
        }
        File file = newRecipePhotoFile(context);
        if (file != null && CloudPhoto.isSafePhotoId(fileName)) {
            file = new File(file.getParentFile(), fileName);
        }
        if (file == null) {
            return null;
        }
        try (OutputStream out = new FileOutputStream(file)) {
            out.write(data);
        } catch (IOException e) {
            //noinspection ResultOfMethodCallIgnored
            file.delete();
            return null;
        }
        return file.getAbsolutePath();
    }

    /**
     * Giải mã byte JPEG (ảnh tải từ Firestore) thành Bitmap để hiện lên ImageView,
     * cạnh dài không quá maxSize (đọc thu nhỏ bằng inSampleSize). Dữ liệu hỏng → null.
     */
    @Nullable
    public static Bitmap decodeBytes(@Nullable byte[] data, int maxSize) {
        if (data == null || data.length == 0) {
            return null;
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, options);
        if (options.outWidth <= 0 || options.outHeight <= 0) {
            return null;
        }
        int sample = 1;
        while (Math.max(options.outWidth, options.outHeight) / (sample * 2) >= maxSize) {
            sample *= 2;
        }
        options.inJustDecodeBounds = false;
        options.inSampleSize = sample;
        return BitmapFactory.decodeByteArray(data, 0, data.length, options);
    }

    /** Thu nhỏ để cạnh dài không quá maxSize (ảnh nhỏ hơn thì giữ nguyên). Ảnh cũ bị thay thì recycle. */
    private static Bitmap scaleDown(Bitmap bitmap, int maxSize) {
        int[] size = CloudPhoto.fitInside(bitmap.getWidth(), bitmap.getHeight(), maxSize);
        if (size[0] == bitmap.getWidth() && size[1] == bitmap.getHeight()) {
            return bitmap;
        }
        Bitmap scaled = Bitmap.createScaledBitmap(bitmap, size[0], size[1], true);
        if (scaled != bitmap) {
            bitmap.recycle();
        }
        return scaled;
    }

    /** File mới (chưa tạo) trong files/recipe_photos, tên theo thời gian. Không tạo được thư mục → null. */
    @Nullable
    private static File newRecipePhotoFile(Context context) {
        File dir = new File(context.getFilesDir(), RECIPE_PHOTO_DIR);
        if (!dir.exists() && !dir.mkdirs()) {
            return null;
        }
        return new File(dir, "recipe_" + System.currentTimeMillis() + ".jpg");
    }

    /**
     * Xoá file ảnh món ăn. Chỉ xoá file nằm trong thư mục recipe_photos (đề phòng đường dẫn lạ trong CSDL).
     * path = null → không làm gì. Chạy ở luồng nền.
     */
    public static void deleteRecipePhoto(@Nullable String path) {
        if (path == null || path.isEmpty()) {
            return;
        }
        File file = new File(path);
        File parent = file.getParentFile();
        if (parent != null && RECIPE_PHOTO_DIR.equals(parent.getName())) {
            //noinspection ResultOfMethodCallIgnored
            file.delete();
        }
    }

    /**
     * Đọc ảnh thu nhỏ để hiển thị: cạnh dài nhất không quá maxSize pixel, xoay đúng chiều theo EXIF
     * (ảnh chụp dọc thường được lưu nằm ngang kèm thông tin "xoay 90°").
     * Chạy ở luồng nền. Đọc lỗi → null.
     */
    @Nullable
    public static Bitmap decodePreview(Context context, Uri uri, int maxSize) {
        try {
            // Lần 1: chỉ đọc kích thước ảnh, chưa tốn bộ nhớ
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            try (InputStream in = context.getContentResolver().openInputStream(uri)) {
                BitmapFactory.decodeStream(in, null, options);
            }
            if (options.outWidth <= 0 || options.outHeight <= 0) {
                return null;
            }

            // Lần 2: đọc ảnh đã thu nhỏ (inSampleSize = 2 → mỗi chiều còn 1/2)
            int sample = 1;
            while (Math.max(options.outWidth, options.outHeight) / (sample * 2) >= maxSize) {
                sample *= 2;
            }
            options.inJustDecodeBounds = false;
            options.inSampleSize = sample;
            Bitmap bitmap;
            try (InputStream in = context.getContentResolver().openInputStream(uri)) {
                bitmap = BitmapFactory.decodeStream(in, null, options);
            }
            if (bitmap == null) {
                return null;
            }
            return rotateByExif(context, uri, bitmap);
        } catch (IOException | SecurityException e) {
            return null;
        }
    }

    /** Xoay ảnh theo thông tin hướng chụp (EXIF) lưu trong file. */
    private static Bitmap rotateByExif(Context context, Uri uri, Bitmap bitmap) throws IOException {
        int orientation;
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            if (in == null) {
                return bitmap;
            }
            orientation = new ExifInterface(in).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
        }
        int degrees;
        if (orientation == ExifInterface.ORIENTATION_ROTATE_90) {
            degrees = 90;
        } else if (orientation == ExifInterface.ORIENTATION_ROTATE_180) {
            degrees = 180;
        } else if (orientation == ExifInterface.ORIENTATION_ROTATE_270) {
            degrees = 270;
        } else {
            return bitmap;
        }
        Matrix matrix = new Matrix();
        matrix.postRotate(degrees);
        Bitmap rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        if (rotated != bitmap) {
            bitmap.recycle();
        }
        return rotated;
    }
}
