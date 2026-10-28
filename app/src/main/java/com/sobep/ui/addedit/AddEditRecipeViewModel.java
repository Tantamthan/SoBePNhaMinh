package com.sobep.ui.addedit;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.sobep.data.local.entity.Ingredient;
import com.sobep.data.local.entity.Recipe;
import com.sobep.data.local.entity.Step;
import com.sobep.data.repository.RecipeRepository;
import com.sobep.util.IntentKeys;

import java.util.ArrayList;
import java.util.List;

/**
 * ViewModel màn Thêm / Sửa món (CN03, CN04).
 * Giữ bản nháp gồm món, danh sách nguyên liệu và các bước người dùng đang nhập,
 * nên xoay màn hình không mất các dòng nguyên liệu / bước vừa thêm.
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

    // ---------- Lưu ----------

    /**
     * Lưu món. Activity đã kiểm tra tên món không trống và đã chép dữ liệu form vào formRecipe.
     * Món có id = 0 → thêm mới; id khác 0 → cập nhật.
     * Dòng nguyên liệu không có tên / bước không có nội dung (người dùng thêm dòng rồi để trống) bị bỏ qua.
     */
    public void save(Recipe formRecipe) {
        // Bấm Lưu 2 lần liên tiếp → chỉ lưu 1 lần
        if (saving) {
            return;
        }
        saving = true;

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
        repository.saveRecipe(formRecipe, ingredientsToSave, stepsToSave, savedRecipeId::postValue);
    }

    // ---------- Điền form một lần ----------

    /** true = Activity đã điền dữ liệu món vào form; xoay màn hình thì không điền lại (giữ chữ đang gõ). */
    public boolean isFormFilled() {
        return formFilled;
    }

    public void markFormFilled() {
        formFilled = true;
    }

    private static <T> List<T> currentList(LiveData<List<T>> liveData) {
        List<T> list = liveData.getValue();
        return list != null ? list : new ArrayList<>();
    }
}
