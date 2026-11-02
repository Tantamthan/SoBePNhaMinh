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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

/**
 * Các việc với file ảnh:
 * - Tạo file ảnh tạm cho app Camera ghi vào (qua FileProvider), dùng ở màn Quét công thức.
 * - Đọc ảnh thu nhỏ để hiển thị (ảnh camera 12 MP mà đọc nguyên kích thước dễ hết bộ nhớ).
 */
public final class ImageStorage {

    // Thư mục ảnh quét tạm trong cache; trùng với <cache-path path="scan/"> trong res/xml/file_paths.xml
    private static final String SCAN_DIR = "scan";

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
    public static Uri createScanPhotoUri(Context context) throws IOException {
        File dir = new File(context.getCacheDir(), SCAN_DIR);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("Không tạo được thư mục " + dir);
        }
        File file = new File(dir, "scan_" + System.currentTimeMillis() + ".jpg");
        if (!file.exists() && !file.createNewFile()) {
            throw new IOException("Không tạo được file " + file);
        }
        return FileProvider.getUriForFile(context, authority(context), file);
    }

    /** Xoá hết ảnh quét tạm (gọi khi rời hẳn màn Quét). Chạy ở luồng nền. */
    public static void clearScanPhotos(Context context) {
        File[] files = new File(context.getCacheDir(), SCAN_DIR).listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
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
