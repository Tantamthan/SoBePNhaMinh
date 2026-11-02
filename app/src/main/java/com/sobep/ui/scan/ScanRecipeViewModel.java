package com.sobep.ui.scan;

import android.app.Application;
import android.graphics.Bitmap;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import com.sobep.util.ImageStorage;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * ViewModel màn Quét công thức.
 * Nhận ảnh (chụp hoặc chọn) → đọc ảnh xem trước + nhận dạng chữ bằng ML Kit (chạy ngay trên máy, không cần mạng).
 * Ảnh, trạng thái và chữ đọc được nằm ở đây nên xoay màn hình không phải đọc lại.
 */
public class ScanRecipeViewModel extends AndroidViewModel {

    /** Trạng thái dòng thông báo trên màn hình. */
    public enum Status {
        IDLE,       // chưa có ảnh
        READING,    // đang đọc chữ
        DONE,       // đọc xong, có chữ
        NO_TEXT,    // đọc xong nhưng ảnh không có chữ
        ERROR       // không mở được ảnh / ML Kit báo lỗi
    }

    // Ảnh xem trước tối đa 1280 px mỗi cạnh: đủ nét trên điện thoại, không tốn bộ nhớ như ảnh gốc
    private static final int PREVIEW_MAX_SIZE = 1280;

    // Đọc file ảnh ở luồng nền (ảnh camera vài MB, đọc trên main thread sẽ giật)
    private final ExecutorService imageExecutor = Executors.newSingleThreadExecutor();
    // Bộ nhận dạng chữ Latin (có tiếng Việt). Đóng lại trong onCleared
    private final TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);

    private final MutableLiveData<Status> status = new MutableLiveData<>(Status.IDLE);
    private final MutableLiveData<Bitmap> preview = new MutableLiveData<>();
    private final MutableLiveData<String> recognizedText = new MutableLiveData<>();

    // Mỗi ảnh mới tăng 1. Kết quả của ảnh cũ về muộn (người dùng đổi ảnh khi đang đọc) thì bỏ qua.
    // volatile: luồng nền cũng đọc biến này
    private volatile int photoVersion;
    // true = Activity đã đưa chữ của ảnh hiện tại vào ô nhập; xoay màn hình thì không điền đè chữ người dùng đã sửa
    private boolean textShown;

    public ScanRecipeViewModel(@NonNull Application application) {
        super(application);
    }

    public LiveData<Status> getStatus() {
        return status;
    }

    public LiveData<Bitmap> getPreview() {
        return preview;
    }

    public LiveData<String> getRecognizedText() {
        return recognizedText;
    }

    /** true = đã chụp / chọn ít nhất một ảnh (nút đổi thành "Chụp lại", "Chọn ảnh khác"). */
    public boolean hasPhoto() {
        return photoVersion > 0;
    }

    public boolean isTextShown() {
        return textShown;
    }

    public void markTextShown() {
        textShown = true;
    }

    /**
     * Có ảnh mới (Uri từ camera hoặc từ bộ chọn ảnh) → hiện ảnh và đọc chữ.
     * Gọi trên main thread.
     */
    public void setPhoto(Uri uri) {
        final int version = ++photoVersion;
        textShown = false;
        recognizedText.setValue(null);
        status.setValue(Status.READING);

        Application app = getApplication();
        imageExecutor.execute(() -> {
            // 1. Ảnh xem trước (thu nhỏ, xoay đúng chiều)
            Bitmap bitmap = ImageStorage.decodePreview(app, uri, PREVIEW_MAX_SIZE);
            if (bitmap == null) {
                postError(version);
                return;
            }
            preview.postValue(bitmap);

            // 2. Ảnh cho ML Kit: đọc từ file gốc (đủ nét cho chữ nhỏ); ML Kit tự xoay theo EXIF
            InputImage image;
            try {
                image = InputImage.fromFilePath(app, uri);
            } catch (IOException | SecurityException e) {
                postError(version);
                return;
            }
            // process() chạy nhận dạng ở luồng của ML Kit; listener được gọi lại trên main thread
            recognizer.process(image)
                    .addOnSuccessListener(result -> {
                        if (version != photoVersion) {
                            return;
                        }
                        String text = result.getText().trim();
                        recognizedText.setValue(text);
                        status.setValue(text.isEmpty() ? Status.NO_TEXT : Status.DONE);
                    })
                    .addOnFailureListener(e -> {
                        if (version == photoVersion) {
                            status.setValue(Status.ERROR);
                        }
                    });
        });
    }

    /** Báo lỗi từ luồng nền (chỉ khi vẫn đang là ảnh mới nhất). */
    private void postError(int version) {
        if (version == photoVersion) {
            status.postValue(Status.ERROR);
        }
    }

    @Override
    protected void onCleared() {
        recognizer.close();
        // Rời hẳn màn Quét: xoá ảnh chụp tạm (chữ đã được trả về màn Thêm món)
        Application app = getApplication();
        imageExecutor.execute(() -> ImageStorage.clearScanPhotos(app));
        imageExecutor.shutdown();
        super.onCleared();
    }
}
