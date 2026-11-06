package com.sobep.ui.addedit;

import android.app.Application;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.sobep.data.local.entity.Ingredient;
import com.sobep.data.local.entity.Recipe;
import com.sobep.data.local.entity.Step;
import com.sobep.data.repository.RecipeRepository;
import com.sobep.util.ImageStorage;
import com.sobep.util.IntentKeys;
import com.sobep.util.RecipeTextParser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * ViewModel màn Thêm / Sửa món (CN03, CN04).
 * Giữ bản nháp gồm món, ảnh món, danh sách nguyên liệu và các bước người dùng đang nhập,
 * nên xoay màn hình không mất ảnh vừa chọn hay các dòng nguyên liệu / bước vừa thêm.
 */
public class AddEditRecipeViewModel extends AndroidViewModel {

    private final RecipeRepository repository;

    private long recipeId = IntentKeys.NO_RECIPE_ID;
    // true = đã nạp dữ liệu (tránh đọc lại CSDL và ghi đè chữ người dùng đang gõ khi xoay màn hình)
    private boolean loaded;
    private boolean formFilled;
    private boolean saving;

    // Món đang sửa (chế độ Sửa) hoặc món trống (chế độ Thêm). Activity dùng để điền form
    private final MutableLiveData<Recipe> recipe = new MutableLiveData<>();
    private final MutableLiveData<List<Ingredient>> ingredients = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<List<Step>> steps = new MutableLiveData<>(new ArrayList<>());

    // Có giá trị (mã món) khi đã lưu xong → Activity setResult(RESULT_OK) rồi finish()
    private final MutableLiveData<Long> savedRecipeId = new MutableLiveData<>();
    // true khi chế độ Sửa mà không tìm thấy món (có thể món đã bị xoá)
    private final MutableLiveData<Boolean> notFound = new MutableLiveData<>(false);

    // ---------- Ảnh món ----------

    // Lưu ảnh (thu nhỏ, xoay, nén JPEG) ở luồng nền; một luồng duy nhất nên các ảnh xử lý lần lượt
    private final ExecutorService imageExecutor = Executors.newSingleThreadExecutor();
    // Đường dẫn ảnh đang hiện trong khung (null = chưa có ảnh). Lưu món thì ghi vào recipes.imagePath
    private final MutableLiveData<String> photoPath = new MutableLiveData<>();
    // true = đang lưu ảnh vừa chọn; chưa xong thì chưa cho bấm Lưu món
    private final MutableLiveData<Boolean> photoProcessing = new MutableLiveData<>(false);
    // true = không đọc được ảnh vừa chọn → Activity báo lỗi rồi gọi clearPhotoError()
    private final MutableLiveData<Boolean> photoError = new MutableLiveData<>(false);
    // Ảnh của món lúc mở màn Sửa. Lưu với ảnh khác (hoặc xoá ảnh) thì file này bị xoá
    private volatile String originalPhotoPath;
    // Các file ảnh tạo ra trong lần mở màn này. Không lưu món → xoá hết; lưu → chỉ giữ ảnh đang chọn.
    // synchronizedList: luồng nền thêm vào, main thread đọc
    private final List<String> createdPhotoPaths = Collections.synchronizedList(new ArrayList<>());
    // Mỗi lần chọn / xoá ảnh tăng 1. Ảnh cũ xử lý xong muộn (người dùng đã đổi ảnh khác) thì bỏ đi
    private volatile int photoVersion;

    public AddEditRecipeViewModel(@NonNull Application application) {
        super(application);
        repository = new RecipeRepository(application);
    }

    /**
     * Gọi trong onCreate với mã món lấy từ Intent.
     * NO_RECIPE_ID → chế độ Thêm (món trống); ngược lại → đọc món từ CSDL để điền sẵn.
     */
    public void start(long id) {
        if (loaded) {
            return;
        }
        loaded = true;
        recipeId = id;

        if (id == IntentKeys.NO_RECIPE_ID) {
            recipe.setValue(new Recipe());
            return;
        }
        // Đọc ở luồng nền, xong thì postValue về main thread
        repository.loadFullRecipe(id, (r, ingredientList, stepList) -> {
            if (r == null) {
                notFound.postValue(true);
                return;
            }
            originalPhotoPath = r.imagePath;
            photoPath.postValue(r.imagePath);
            recipe.postValue(r);
            ingredients.postValue(new ArrayList<>(ingredientList));
            steps.postValue(new ArrayList<>(stepList));
        });
    }

    public boolean isEditMode() {
        return recipeId != IntentKeys.NO_RECIPE_ID;
    }

    public LiveData<Recipe> getRecipe() {
        return recipe;
    }

    public LiveData<List<Ingredient>> getIngredients() {
        return ingredients;
    }

    public LiveData<List<Step>> getSteps() {
        return steps;
    }

    public LiveData<Long> getSavedRecipeId() {
        return savedRecipeId;
    }

    public LiveData<Boolean> getNotFound() {
        return notFound;
    }

    public LiveData<String> getPhotoPath() {
        return photoPath;
    }

    public LiveData<Boolean> getPhotoError() {
        return photoError;
    }

    /** Activity đã báo lỗi ảnh → tắt cờ để xoay màn hình không báo lại. */
    public void clearPhotoError() {
        photoError.setValue(false);
    }

    public boolean isPhotoProcessing() {
        return Boolean.TRUE.equals(photoProcessing.getValue());
    }

    // ---------- Sửa bản nháp nguyên liệu / bước ----------

    public void addIngredient(Ingredient ingredient) {
        List<Ingredient> list = new ArrayList<>(currentList(ingredients));
        list.add(ingredient);
        ingredients.setValue(list);
    }

    public void removeIngredient(int index) {
        List<Ingredient> list = new ArrayList<>(currentList(ingredients));
        if (index >= 0 && index < list.size()) {
            list.remove(index);
            ingredients.setValue(list);
        }
    }

    public void addStep(Step step) {
        List<Step> list = new ArrayList<>(currentList(steps));
        list.add(step);
        steps.setValue(list);
    }

    public void removeStep(int index) {
        List<Step> list = new ArrayList<>(currentList(steps));
        if (index >= 0 && index < list.size()) {
            list.remove(index);
            steps.setValue(list);
        }
    }

    // ---------- Ảnh món ----------

    /**
     * Có ảnh mới (Uri từ camera hoặc bộ chọn ảnh) → lưu thành file trong files/recipe_photos ở luồng nền.
     * fromCamera = true: Uri là file chụp tạm trong cache, lưu xong thì xoá file tạm đó.
     * Gọi trên main thread.
     */
    public void setPhoto(Uri uri, boolean fromCamera) {
        if (saving) {
            return;
        }
        final int version = ++photoVersion;
        photoProcessing.setValue(true);

        Application app = getApplication();
        imageExecutor.execute(() -> {
            String path = ImageStorage.saveRecipePhoto(app, uri);
            if (fromCamera) {
                ImageStorage.deleteCameraPhoto(app, uri);
            }
            if (path != null) {
                createdPhotoPaths.add(path);
            }
            if (version != photoVersion) {
                // Người dùng đã chọn ảnh khác / xoá ảnh trong lúc đang xử lý → ảnh này không dùng nữa
                ImageStorage.deleteRecipePhoto(path);
                return;
            }
            if (path != null) {
                photoPath.postValue(path);
            } else {
                photoError.postValue(true);
            }
            photoProcessing.postValue(false);
        });
    }

    /** Bỏ ảnh món (khung ảnh trở lại nút Chọn ảnh / Chụp ảnh). File chỉ bị xoá khi bấm Lưu món. */
    public void removePhoto() {
        photoVersion++;
        photoProcessing.setValue(false);
        photoPath.setValue(null);
    }

    // ---------- Chữ quét từ ảnh ----------

    /**
     * Tách chữ quét được (RecipeTextParser) rồi THÊM nguyên liệu và các bước vào CUỐI bản nháp
     * (không xoá những gì người dùng đã nhập). Dòng trống người dùng thêm mà chưa gõ gì thì bỏ đi trước.
     * Trả về kết quả tách để Activity điền tên món / thời gian / khẩu phần / ghi chú vào các ô đang trống.
     */
    public RecipeTextParser.Result applyScannedText(String scannedText) {
        RecipeTextParser.Result result = RecipeTextParser.parse(scannedText);

        List<Ingredient> ingredientList = new ArrayList<>();
        for (Ingredient ingredient : currentList(ingredients)) {
            if (!ingredient.name.trim().isEmpty()) {
                ingredientList.add(ingredient);
            }
        }
        ingredientList.addAll(result.ingredients);
        ingredients.setValue(ingredientList);

        List<Step> stepList = new ArrayList<>();
        for (Step step : currentList(steps)) {
            if (!step.content.trim().isEmpty()) {
                stepList.add(step);
            }
        }
        stepList.addAll(result.steps);
        steps.setValue(stepList);
        return result;
    }

    // ---------- Lưu ----------

    /**
     * Lưu món. Activity đã kiểm tra tên món không trống, ảnh đã xử lý xong và đã chép dữ liệu form vào formRecipe.
     * Món có id = 0 → thêm mới; id khác 0 → cập nhật.
     * Dòng nguyên liệu không có tên / bước không có nội dung (người dùng thêm dòng rồi để trống) bị bỏ qua.
     * Lưu xong mới xoá các file ảnh không dùng (ảnh cũ đã bị thay, ảnh đã chọn rồi đổi ý).
     */
    public void save(Recipe formRecipe) {
        // Bấm Lưu 2 lần liên tiếp → chỉ lưu 1 lần
        if (saving) {
            return;
        }
        saving = true;

        String keptPhoto = photoPath.getValue();
        formRecipe.imagePath = keptPhoto;
        // Các file sẽ xoá sau khi lưu: mọi ảnh tạo ra lần này trừ ảnh được giữ, và ảnh cũ nếu đã bị thay / xoá
        List<String> unusedPhotos = new ArrayList<>();
        synchronized (createdPhotoPaths) {
            for (String path : createdPhotoPaths) {
                if (!path.equals(keptPhoto)) {
                    unusedPhotos.add(path);
                }
            }
        }
        String original = originalPhotoPath;
        if (original != null && !original.equals(keptPhoto)) {
            unusedPhotos.add(original);
        }

        List<Ingredient> ingredientsToSave = new ArrayList<>();
        for (Ingredient ingredient : currentList(ingredients)) {
            ingredient.name = ingredient.name.trim();
            ingredient.unit = ingredient.unit.trim();
            if (!ingredient.name.isEmpty()) {
                ingredientsToSave.add(ingredient);
            }
        }
        List<Step> stepsToSave = new ArrayList<>();
        for (Step step : currentList(steps)) {
            step.content = step.content.trim();
            if (!step.content.isEmpty()) {
                stepsToSave.add(step);
            }
        }
        // Listener chạy ở luồng nền của CSDL, sau khi ghi xong → xoá file ở đây không làm giật giao diện
        repository.saveRecipe(formRecipe, ingredientsToSave, stepsToSave, savedId -> {
            for (String path : unusedPhotos) {
                ImageStorage.deleteRecipePhoto(path);
            }
            savedRecipeId.postValue(savedId);
        });
    }

    // ---------- Điền form một lần ----------

    /** true = Activity đã điền dữ liệu món vào form; xoay màn hình thì không điền lại (giữ chữ đang gõ). */
    public boolean isFormFilled() {
        return formFilled;
    }

    public void markFormFilled() {
        formFilled = true;
    }

    @Override
    protected void onCleared() {
        // Rời màn mà KHÔNG lưu → xoá các ảnh vừa tạo (ảnh cũ của món giữ nguyên).
        // Đã bấm Lưu thì phần dọn ảnh nằm trong listener của save().
        if (!saving) {
            imageExecutor.execute(() -> {
                synchronized (createdPhotoPaths) {
                    for (String path : createdPhotoPaths) {
                        ImageStorage.deleteRecipePhoto(path);
                    }
                }
            });
        }
        // shutdown: không nhận việc mới nhưng vẫn chạy hết việc đang chờ (kể cả việc xoá ở trên)
        imageExecutor.shutdown();
        super.onCleared();
    }

    private static <T> List<T> currentList(LiveData<List<T>> liveData) {
        List<T> list = liveData.getValue();
        return list != null ? list : new ArrayList<>();
    }
}
