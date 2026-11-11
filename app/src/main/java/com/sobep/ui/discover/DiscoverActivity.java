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
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.sobep.R;
import com.sobep.data.community.CommunityRecipe;
import com.sobep.data.local.entity.Ingredient;
import com.sobep.data.repository.CommunityRepository;
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
 *
 * CN15: khi đã đăng nhập có thêm tab Cộng đồng (bài đọc từ Firestore). Dùng chung danh sách, bảng xem trước và
 * "Lưu vào sổ"; bài người khác có nút Báo cáo, bài của mình có nút Gỡ bài. Chưa đăng nhập thì ẩn hẳn thanh tab.
 */
public class DiscoverActivity extends BaseActivity
        implements DiscoverRecipeAdapter.OnSampleClickListener {

    // Lý do báo cáo: chữ hiển thị và mã gửi lên Firestore (cùng thứ tự)
    private static final int[] REPORT_REASON_LABELS = {
            R.string.report_reason_inappropriate, R.string.report_reason_spam, R.string.report_reason_other
    };
    private static final String[] REPORT_REASON_CODES = {
            CommunityRepository.REASON_INAPPROPRIATE, CommunityRepository.REASON_SPAM, CommunityRepository.REASON_OTHER
    };

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
        setupTabs();
        observeViewModel();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Có thể vừa đăng nhập / đăng xuất ở màn Tài khoản → ẩn/hiện tab Cộng đồng cho đúng
        viewModel.refreshSignIn();
        recipeAdapter.setCurrentUid(viewModel.getCurrentUid());
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

    /** Thanh 2 tab (MaterialButtonToggleGroup): bấm nút nào thì báo ViewModel đổi tab. */
    private void setupTabs() {
        binding.toggleTabs.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) {
                return;
            }
            // R.id không phải hằng số (AGP 8+) → dùng if/else
            if (checkedId == R.id.buttonTabCommunity) {
                viewModel.selectTab(DiscoverViewModel.TAB_COMMUNITY);
            } else {
                viewModel.selectTab(DiscoverViewModel.TAB_SAMPLES);
            }
        });
        binding.buttonReload.setOnClickListener(v -> viewModel.loadCommunity());
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

        // ---- Tab Cộng đồng ----
        viewModel.isSignedIn().observe(this, signedIn ->
                binding.toggleTabs.setVisibility(Boolean.TRUE.equals(signedIn) ? View.VISIBLE : View.GONE));
        viewModel.getTab().observe(this, this::showTab);
        viewModel.isCommunityLoading().observe(this, loading -> {
            boolean isLoading = Boolean.TRUE.equals(loading);
            binding.progressCommunity.setVisibility(isLoading && isCommunityTab() ? View.VISIBLE : View.GONE);
            binding.buttonReload.setEnabled(!isLoading);
            updateEmptyState();
        });
        viewModel.getMessage().observe(this, messageRes -> {
            if (messageRes != null) {
                showToast(messageRes);
                viewModel.consumeMessage();
            }
        });
    }

    private void showResults(List<SampleRecipe> results) {
        recipeAdapter.setItems(results);
        int count = results == null ? 0 : results.size();
        binding.textResultCount.setText(getString(R.string.format_recipe_count, count));
        updateEmptyState();
    }

    private boolean isCommunityTab() {
        return Integer.valueOf(DiscoverViewModel.TAB_COMMUNITY).equals(viewModel.getTab().getValue());
    }

    /** Đổi tab: ô gợi ý chỉ có ở Món mẫu, nút tải lại + vòng xoay chỉ có ở Cộng đồng. */
    private void showTab(Integer tab) {
        boolean community = Integer.valueOf(DiscoverViewModel.TAB_COMMUNITY).equals(tab);
        binding.toggleTabs.check(community ? R.id.buttonTabCommunity : R.id.buttonTabSamples);
        binding.layoutSuggest.setVisibility(community ? View.GONE : View.VISIBLE);
        binding.buttonReload.setVisibility(community ? View.VISIBLE : View.GONE);
        boolean loading = Boolean.TRUE.equals(viewModel.isCommunityLoading().getValue());
        binding.progressCommunity.setVisibility(community && loading ? View.VISIBLE : View.GONE);
        showHeading();
        updateEmptyState();
    }

    /** Dòng "không có món": ẩn khi đang tải; tab Cộng đồng chưa có bài nào thì mời đăng bài đầu tiên. */
    private void updateEmptyState() {
        boolean community = isCommunityTab();
        boolean loading = community && Boolean.TRUE.equals(viewModel.isCommunityLoading().getValue());
        boolean empty = recipeAdapter.getItemCount() == 0;
        String keyword = viewModel.getKeyword().getValue();
        boolean hasKeyword = keyword != null && !keyword.isEmpty();
        binding.textEmpty.setText(community && !hasKeyword ? R.string.community_empty : R.string.discover_empty);
        binding.textEmpty.setVisibility(empty && !loading ? View.VISIBLE : View.GONE);
    }

    /**
     * Tiêu đề kết quả, khớp đúng điều kiện đang lọc (ViewModel lọc theo CẢ chữ tìm VÀ ô đang chọn):
     * có chữ + có ô → "Kết quả cho “trung” trong Gà"; chỉ có chữ → "Kết quả cho …";
     * chỉ có ô → tên ô; không có gì → "Tất cả món". Tab Cộng đồng không có ô → "Món mới từ cộng đồng".
     */
    private void showHeading() {
        String keyword = viewModel.getKeyword().getValue();
        KeywordTile tile = viewModel.getSelectedTile().getValue() == null
                ? null : KeywordTile.find(viewModel.getSelectedTile().getValue());
        boolean hasKeyword = keyword != null && !keyword.isEmpty();
        if (isCommunityTab()) {
            if (hasKeyword) {
                binding.textResultHeading.setText(getString(R.string.format_search_heading, keyword));
            } else {
                binding.textResultHeading.setText(R.string.community_heading);
            }
        } else if (hasKeyword && tile != null) {
            binding.textResultHeading.setText(
                    getString(R.string.format_search_heading_in_tile, keyword, getString(tile.labelRes)));
        } else if (hasKeyword) {
            binding.textResultHeading.setText(getString(R.string.format_search_heading, keyword));
        } else if (tile != null) {
            binding.textResultHeading.setText(tile.labelRes);
        } else {
            binding.textResultHeading.setText(R.string.main_heading_all);
        }
    }

    // ---------- Bấm trên món ----------

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

        // Bài cộng đồng: "Đăng bởi …", nút Báo cáo / Gỡ bài (ẩn hiện trong updatePreviewSavedState)
        if (sample instanceof CommunityRecipe) {
            CommunityRecipe post = (CommunityRecipe) sample;
            previewBinding.textAuthor.setText(getString(R.string.format_posted_by,
                    DiscoverRecipeAdapter.authorOf(this, post)));
            previewBinding.textAuthor.setVisibility(View.VISIBLE);
            previewBinding.buttonReport.setOnClickListener(v -> confirmReport(post));
            previewBinding.buttonRemovePost.setOnClickListener(v -> confirmRemove(post));
        }

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

    /**
     * Chưa lưu → nút "Lưu vào sổ"; đã lưu → "Món này đã có trong sổ" + nút "Mở món trong sổ".
     * Bài cộng đồng của mình → không có phần lưu (món vốn ở trong sổ), chỉ có nút "Gỡ bài";
     * bài của người khác → thêm nút "Báo cáo".
     */
    private void updatePreviewSavedState() {
        if (previewBinding == null || previewSample == null) {
            return;
        }
        boolean isPost = previewSample instanceof CommunityRecipe;
        boolean isMine = isPost && ((CommunityRecipe) previewSample).isMine(viewModel.getCurrentUid());
        boolean saved = viewModel.isSaved(previewSample);
        previewBinding.buttonSaveToBook.setVisibility(!isMine && !saved ? View.VISIBLE : View.GONE);
        previewBinding.layoutSaved.setVisibility(!isMine && saved ? View.VISIBLE : View.GONE);
        previewBinding.buttonReport.setVisibility(isPost && !isMine ? View.VISIBLE : View.GONE);
        previewBinding.buttonRemovePost.setVisibility(isMine ? View.VISIBLE : View.GONE);
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

    // ---------- Báo cáo / gỡ bài cộng đồng ----------

    /** Hộp thoại chọn 1 lý do rồi gửi báo cáo. Gửi xong bài bị ẩn trên máy này. */
    private void confirmReport(CommunityRecipe post) {
        CharSequence[] labels = new CharSequence[REPORT_REASON_LABELS.length];
        for (int i = 0; i < labels.length; i++) {
            labels[i] = getString(REPORT_REASON_LABELS[i]);
        }
        // Mảng 1 phần tử để lambda bên dưới ghi lại được lựa chọn
        final int[] chosen = {0};
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.dialog_report_title)
                .setSingleChoiceItems(labels, 0, (dialog, which) -> chosen[0] = which)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_send_report, (dialog, which) -> {
                    dismissPreview();
                    viewModel.reportPost(post, REPORT_REASON_CODES[chosen[0]]);
                })
                .show();
    }

    /** Hỏi lại trước khi gỡ bài của mình. */
    private void confirmRemove(CommunityRecipe post) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.dialog_remove_post_title)
                .setMessage(R.string.dialog_remove_post_message)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_remove_post_confirm, (dialog, which) -> {
                    dismissPreview();
                    viewModel.deletePost(post);
                })
                .show();
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
