package com.sobep.ui.discover;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;

import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.sobep.R;
import com.sobep.data.local.entity.Ingredient;
import com.sobep.data.sample.SampleRecipe;
import com.sobep.databinding.ActivityDiscoverBinding;
import com.sobep.databinding.BottomSheetRecipePreviewBinding;
import com.sobep.ui.BaseActivity;
import com.sobep.ui.detail.IngredientAdapter;
import com.sobep.ui.detail.RecipeDetailActivity;
import com.sobep.util.CategoryNames;
import com.sobep.util.IntentKeys;

import java.util.List;

/**
 * Màn 9 · Khám phá món (CN06): món mẫu đọc từ assets/mon_mau.json (không cần mạng, không cần đăng nhập).
 * Tìm theo tên món / nguyên liệu (bỏ dấu), lọc theo 8 ô gợi ý; bấm món → bảng xem trước (BottomSheetDialog);
 * bấm "Lưu vào sổ" hoặc nút + → chép thành món thật trong CSDL. Món đã lưu → "Mở món trong sổ" (Intent tường minh).
 */
public class DiscoverActivity extends BaseActivity
        implements DiscoverRecipeAdapter.OnSampleClickListener {

    private ActivityDiscoverBinding binding;
    private DiscoverViewModel viewModel;
    private KeywordTileAdapter tileAdapter;
    private DiscoverRecipeAdapter recipeAdapter;

    // Bảng xem trước đang mở (null khi đã đóng)
    private BottomSheetDialog previewDialog;
    private BottomSheetRecipePreviewBinding previewBinding;
    private SampleRecipe previewSample;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityDiscoverBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        applySystemBarInsets(binding.getRoot());

        viewModel = new ViewModelProvider(this).get(DiscoverViewModel.class);

        binding.buttonBack.setOnClickListener(v -> finish());
        setupLists();
        setupSearch();
        observeViewModel();
    }

    private void setupLists() {
        // GridLayoutManager / LinearLayoutManager đã khai báo trong activity_discover.xml
        tileAdapter = new KeywordTileAdapter(tile -> viewModel.toggleTile(tile.key));
        binding.recyclerKeywords.setAdapter(tileAdapter);
        recipeAdapter = new DiscoverRecipeAdapter(this);
        binding.recyclerResults.setAdapter(recipeAdapter);
    }

    private void setupSearch() {
        binding.editSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                viewModel.setKeyword(s.toString());
            }
        });
        binding.editSearch.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                hideKeyboard();
                return true;
            }
            return false;
        });
    }

    private void observeViewModel() {
        viewModel.getResults().observe(this, this::showResults);
        viewModel.getTileCounts().observe(this, tileAdapter::setCounts);
        viewModel.getSelectedTile().observe(this, key -> {
            tileAdapter.setSelectedKey(key);
            showHeading();
        });
        viewModel.getKeyword().observe(this, k -> showHeading());

        // Món nào đã lưu (đọc từ Room) → nhãn "Đã lưu" trong danh sách và trạng thái bảng xem trước
        viewModel.getSavedSamples().observe(this, saved -> {
            recipeAdapter.setSaved(saved);
            updatePreviewSavedState();
        });
        viewModel.getSavedEvent().observe(this, event -> {
            if (event != null) {
                Toast.makeText(this, getString(R.string.format_saved_to_book, event.sampleName),
                        Toast.LENGTH_SHORT).show();
                viewModel.consumeSavedEvent();
            }
        });
        viewModel.getLoadError().observe(this, error -> {
            if (Boolean.TRUE.equals(error)) {
                showToast(R.string.msg_sample_load_error);
            }
        });
    }

    private void showResults(List<SampleRecipe> results) {
        recipeAdapter.setItems(results);
        int count = results == null ? 0 : results.size();
        binding.textResultCount.setText(getString(R.string.format_recipe_count, count));
        binding.textEmpty.setVisibility(count == 0 ? View.VISIBLE : View.GONE);
    }

    /** Tiêu đề kết quả: đang tìm → "Kết quả cho …"; chọn ô → tên ô; không thì "Tất cả món". */
    private void showHeading() {
        String keyword = viewModel.getKeyword().getValue();
        KeywordTile tile = viewModel.getSelectedTile().getValue() == null
                ? null : KeywordTile.find(viewModel.getSelectedTile().getValue());
        if (keyword != null && !keyword.isEmpty()) {
            binding.textResultHeading.setText(getString(R.string.format_search_heading, keyword));
        } else if (tile != null) {
            binding.textResultHeading.setText(tile.labelRes);
        } else {
            binding.textResultHeading.setText(R.string.main_heading_all);
        }
    }

    // ---------- Bấm trên món mẫu ----------

    @Override
    public void onSampleClick(SampleRecipe sample) {
        showPreview(sample);
    }

    @Override
    public void onAddClick(SampleRecipe sample) {
        viewModel.saveToBook(sample);
    }

    /** Bảng xem trước trượt từ dưới lên (BottomSheetDialog + bottom_sheet_recipe_preview.xml). */
    private void showPreview(SampleRecipe sample) {
        dismissPreview();
        previewSample = sample;
        previewBinding = BottomSheetRecipePreviewBinding.inflate(getLayoutInflater());
        previewDialog = new BottomSheetDialog(this);
        previewDialog.setContentView(previewBinding.getRoot());

        previewBinding.textTitle.setText(sample.name);
        previewBinding.textMeta.setText(getString(R.string.format_discover_meta,
                CategoryNames.of(this, sample.category), sample.cookTimeMinutes));
        previewBinding.textIngredients.setText(buildIngredientLines(sample));
        previewBinding.textStepsCount.setText(getString(R.string.format_steps_count, sample.steps.size()));

        previewBinding.buttonClose.setOnClickListener(v -> dismissPreview());
        previewBinding.buttonSaveToBook.setOnClickListener(v -> viewModel.saveToBook(sample));
        previewBinding.buttonOpenInBook.setOnClickListener(v -> openInBook(sample));
        previewDialog.setOnDismissListener(d -> {
            previewDialog = null;
            previewBinding = null;
            previewSample = null;
        });

        updatePreviewSavedState();
        previewDialog.show();
    }

    /** "· Trứng gà 2 quả", mỗi nguyên liệu một dòng. */
    private String buildIngredientLines(SampleRecipe sample) {
        StringBuilder lines = new StringBuilder();
        for (Ingredient ingredient : sample.ingredients) {
            if (lines.length() > 0) {
                lines.append('\n');
            }
            lines.append(getString(R.string.format_ingredient_line,
                    ingredient.name, IngredientAdapter.formatQuantity(this, ingredient)));
        }
        return lines.toString();
    }

    /** Chưa lưu → nút "Lưu vào sổ"; đã lưu → "Món này đã có trong sổ" + nút "Mở món trong sổ". */
    private void updatePreviewSavedState() {
        if (previewBinding == null || previewSample == null) {
            return;
        }
        boolean saved = viewModel.isSaved(previewSample);
        previewBinding.buttonSaveToBook.setVisibility(saved ? View.GONE : View.VISIBLE);
        previewBinding.layoutSaved.setVisibility(saved ? View.VISIBLE : View.GONE);
    }

    /** Intent tường minh: mở món đã lưu ở màn Chi tiết món, gửi kèm mã món trong sổ. */
    private void openInBook(SampleRecipe sample) {
        Long recipeId = viewModel.getSavedRecipeId(sample);
        if (recipeId == null) {
            return;
        }
        dismissPreview();
        Intent intent = new Intent(this, RecipeDetailActivity.class);
        intent.putExtra(IntentKeys.EXTRA_RECIPE_ID, recipeId);
        startActivity(intent);
    }

    private void dismissPreview() {
        if (previewDialog != null) {
            previewDialog.dismiss();
        }
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(binding.editSearch.getWindowToken(), 0);
        }
        binding.editSearch.clearFocus();
    }

    @Override
    protected void onDestroy() {
        // Đóng bảng xem trước trước khi Activity bị huỷ (vd xoay màn hình), tránh rò cửa sổ
        dismissPreview();
        super.onDestroy();
    }
}
