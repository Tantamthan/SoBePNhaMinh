package com.sobep.ui.discover;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import com.sobep.data.local.entity.Ingredient;
import com.sobep.data.local.model.SavedSample;
import com.sobep.data.repository.RecipeRepository;
import com.sobep.data.sample.SampleRecipe;
import com.sobep.data.sample.SampleRecipeLoader;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * ViewModel màn Khám phá (CN06): đọc món mẫu từ assets, lọc theo từ khoá / ô gợi ý,
 * biết món mẫu nào đã lưu vào sổ (qua cột recipes.sourceSampleId) và lưu món mẫu thành món thật.
 */
public class DiscoverViewModel extends AndroidViewModel {

    /** Báo cho Activity khi vừa lưu xong một món mẫu (sự kiện một lần). */
    public static final class SavedEvent {
        public final String sampleName;

        SavedEvent(String sampleName) {
            this.sampleName = sampleName;
        }
    }

    private final RecipeRepository repository;
    // Luồng nền riêng để đọc file JSON (không đọc trên main thread)
    private final ExecutorService fileExecutor = Executors.newSingleThreadExecutor();

    private final MutableLiveData<List<SampleRecipe>> samples = new MutableLiveData<>();
    private final MutableLiveData<Boolean> loadError = new MutableLiveData<>(false);
    private final MutableLiveData<String> keyword = new MutableLiveData<>("");
    // Key của ô gợi ý đang chọn; null = không chọn ô nào
    private final MutableLiveData<String> selectedTile = new MutableLiveData<>(null);

    // Món mẫu sau khi lọc — kết hợp 3 nguồn: danh sách mẫu, từ khoá, ô đang chọn
    private final MediatorLiveData<List<SampleRecipe>> results = new MediatorLiveData<>();
    // Số món của từng ô gợi ý (key → số món)
    private final LiveData<Map<String, Integer>> tileCounts;
    // Món mẫu đã lưu: mã món mẫu → mã món trong sổ
    private final LiveData<Map<String, Long>> savedSamples;

    private final MutableLiveData<SavedEvent> savedEvent = new MutableLiveData<>();
    // Món mẫu đang lưu dở (chưa thấy trong CSDL) → chặn bấm lưu 2 lần thành 2 món trùng
    private final Set<String> savingIds = new HashSet<>();

    public DiscoverViewModel(@NonNull Application application) {
        super(application);
        repository = new RecipeRepository(application);

        results.addSource(samples, list -> filter());
        results.addSource(keyword, k -> filter());
        results.addSource(selectedTile, key -> filter());

        tileCounts = Transformations.map(samples, DiscoverViewModel::countTiles);
        savedSamples = Transformations.map(repository.getSavedSamples(), list -> {
            Map<String, Long> map = new HashMap<>();
            for (SavedSample saved : list) {
                map.put(saved.sourceSampleId, saved.id);
                // Đã thấy trong CSDL → không còn "đang lưu"
                savingIds.remove(saved.sourceSampleId);
            }
            return map;
        });

        loadSamples();
    }

    private void loadSamples() {
        fileExecutor.execute(() -> {
            try {
                samples.postValue(SampleRecipeLoader.load(getApplication()));
            } catch (Exception e) {
                // File hỏng hoặc thiếu → báo lỗi, danh sách trống, app không crash
                loadError.postValue(true);
                samples.postValue(new ArrayList<>());
            }
        });
    }

    public LiveData<List<SampleRecipe>> getResults() {
        return results;
    }

    public LiveData<Map<String, Integer>> getTileCounts() {
        return tileCounts;
    }

    public LiveData<Map<String, Long>> getSavedSamples() {
        return savedSamples;
    }

    public LiveData<String> getSelectedTile() {
        return selectedTile;
    }

    public LiveData<String> getKeyword() {
        return keyword;
    }

    public LiveData<Boolean> getLoadError() {
        return loadError;
    }

    public LiveData<SavedEvent> getSavedEvent() {
        return savedEvent;
    }

    public void consumeSavedEvent() {
        savedEvent.setValue(null);
    }

    // ---------- Lọc ----------

    public void setKeyword(String text) {
        String value = text == null ? "" : text.trim();
        if (!value.equals(keyword.getValue())) {
            keyword.setValue(value);
        }
    }

    /** Bấm ô gợi ý: chọn ô đó; bấm lại ô đang chọn thì bỏ chọn (hiện tất cả). */
    public void toggleTile(String key) {
        selectedTile.setValue(key.equals(selectedTile.getValue()) ? null : key);
    }

    private void filter() {
        List<SampleRecipe> all = samples.getValue();
        if (all == null) {
            return;
        }
        String query = normalize(keyword.getValue());
        KeywordTile tile = selectedTile.getValue() == null ? null : KeywordTile.find(selectedTile.getValue());

        List<SampleRecipe> filtered = new ArrayList<>();
        for (SampleRecipe recipe : all) {
            boolean matchTile = tile == null || tile.matches(recipe);
            boolean matchKeyword = query.isEmpty() || matchesKeyword(recipe, query);
            if (matchTile && matchKeyword) {
                filtered.add(recipe);
            }
        }
        results.setValue(filtered);
    }

    /** Khớp tên món hoặc tên một nguyên liệu, không phân biệt hoa thường và dấu. */
    private static boolean matchesKeyword(SampleRecipe recipe, String query) {
        if (normalize(recipe.name).contains(query)) {
            return true;
        }
        for (Ingredient ingredient : recipe.ingredients) {
            if (normalize(ingredient.name).contains(query)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Bỏ dấu tiếng Việt + chữ thường: "Trứng gà" → "trung ga", nên gõ "trung" vẫn ra "trứng".
     * NFD tách chữ và dấu ("ứ" → "u" + dấu), rồi xoá các dấu (\p{M}); riêng "đ" không tách được nên đổi tay.
     */
    static String normalize(@Nullable String text) {
        if (text == null) {
            return "";
        }
        String noAccent = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return noAccent.replace('đ', 'd').replace('Đ', 'D').toLowerCase(Locale.ROOT).trim();
    }

    private static Map<String, Integer> countTiles(List<SampleRecipe> list) {
        Map<String, Integer> counts = new HashMap<>();
        for (KeywordTile tile : KeywordTile.ALL) {
            int count = 0;
            for (SampleRecipe recipe : list) {
                if (tile.matches(recipe)) {
                    count++;
                }
            }
            counts.put(tile.key, count);
        }
        return counts;
    }

    // ---------- Lưu vào sổ ----------

    public boolean isSaved(SampleRecipe sample) {
        Map<String, Long> saved = savedSamples.getValue();
        return saved != null && saved.containsKey(sample.id);
    }

    /** Mã món trong sổ của món mẫu đã lưu; null nếu chưa lưu. */
    @Nullable
    public Long getSavedRecipeId(SampleRecipe sample) {
        Map<String, Long> saved = savedSamples.getValue();
        return saved == null ? null : saved.get(sample.id);
    }

    /** CN06: chép món mẫu vào bảng recipes (kèm nguyên liệu, các bước), ghi sourceSampleId. */
    public void saveToBook(SampleRecipe sample) {
        if (isSaved(sample) || savingIds.contains(sample.id)) {
            return;
        }
        savingIds.add(sample.id);
        repository.saveRecipe(sample.toRecipe(), sample.copyIngredients(), sample.copySteps(),
                // Gọi ở luồng nền → postValue
                recipeId -> savedEvent.postValue(new SavedEvent(sample.name)));
    }

    @Override
    protected void onCleared() {
        fileExecutor.shutdown();
        super.onCleared();
    }
}
