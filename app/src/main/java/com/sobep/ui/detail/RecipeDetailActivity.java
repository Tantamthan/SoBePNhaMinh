package com.sobep.ui.detail;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import com.sobep.R;
import com.sobep.databinding.ActivityRecipeDetailBinding;
import com.sobep.ui.BaseActivity;
import com.sobep.ui.addedit.AddEditRecipeActivity;
import com.sobep.ui.cooking.CookingModeActivity;
import com.sobep.util.IntentKeys;

/**
 * Màn 3 · Chi tiết món. Nhận mã món qua Intent rồi hiển thị thông tin.
 * Minh hoạ đủ các loại Intent:
 * - Tường minh: mở Chế độ nấu, mở màn Sửa món (chờ kết quả trả về).
 * - Ngầm định: Chia sẻ (ACTION_SEND), mở Video (ACTION_VIEW) bằng app khác trên máy.
 * Chưa làm: đọc thông tin món từ CSDL (Room) theo mã món.
 */
public class RecipeDetailActivity extends BaseActivity {

    private static final int MIN_SERVINGS = 1;
    private static final int MAX_SERVINGS = 20;
    // Chưa lưu link video riêng nên tìm tên món trên YouTube
    private static final String VIDEO_SEARCH_URL = "https://www.youtube.com/results?search_query=";

    private ActivityRecipeDetailBinding binding;
    private long recipeId;
    private int servings = MIN_SERVINGS;

    // Mở màn Sửa món và chờ kết quả
    private final ActivityResultLauncher<Intent> editRecipeLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK) {
                    showToast(R.string.msg_recipe_updated);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityRecipeDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        applySystemBarInsets(binding.getRoot());

        // Đọc mã món mà màn trước gửi sang
        recipeId = getIntent().getLongExtra(IntentKeys.EXTRA_RECIPE_ID, IntentKeys.NO_RECIPE_ID);
        if (recipeId == IntentKeys.NO_RECIPE_ID) {
            showToast(R.string.msg_recipe_not_found);
            finish();
            return;
        }

        showServings();
        setupActions();
    }

    private void showServings() {
        binding.textServings.setText(getString(R.string.format_people, servings));
        binding.buttonCookNow.setText(getString(R.string.format_cook_now, servings));
        binding.buttonDecrease.setEnabled(servings > MIN_SERVINGS);
        binding.buttonIncrease.setEnabled(servings < MAX_SERVINGS);
    }

    private void setupActions() {
        binding.buttonBack.setOnClickListener(v -> finish());
        binding.buttonFavorite.setOnClickListener(v -> showComingSoon());

        binding.buttonDecrease.setOnClickListener(v -> {
            if (servings > MIN_SERVINGS) {
                servings--;
                showServings();
            }
        });
        binding.buttonIncrease.setOnClickListener(v -> {
            if (servings < MAX_SERVINGS) {
                servings++;
                showServings();
            }
        });

        binding.buttonShare.setOnClickListener(v -> shareRecipe());
        binding.buttonVideo.setOnClickListener(v -> openVideo());

        // Gửi kèm mã món → AddEditRecipe hiểu là chế độ Sửa
        binding.buttonEdit.setOnClickListener(v -> {
            Intent intent = new Intent(this, AddEditRecipeActivity.class);
            intent.putExtra(IntentKeys.EXTRA_RECIPE_ID, recipeId);
            editRecipeLauncher.launch(intent);
        });

        binding.buttonCookNow.setOnClickListener(v -> {
            Intent intent = new Intent(this, CookingModeActivity.class);
            intent.putExtra(IntentKeys.EXTRA_RECIPE_ID, recipeId);
            startActivity(intent);
        });
    }

    /** Tên món đang hiển thị trên màn hình. */
    private String getRecipeName() {
        return binding.textTitle.getText().toString();
    }

    /** Intent ngầm định: không chỉ rõ app nào, hệ thống hiện danh sách app có thể nhận chữ. */
    private void shareRecipe() {
        Intent sendIntent = new Intent(Intent.ACTION_SEND);
        sendIntent.setType("text/plain");
        sendIntent.putExtra(Intent.EXTRA_TEXT, getRecipeName());
        startActivity(Intent.createChooser(sendIntent, getString(R.string.share_chooser_title)));
    }

    /** Intent ngầm định: mở đường link bằng trình duyệt hoặc app YouTube. */
    private void openVideo() {
        Uri uri = Uri.parse(VIDEO_SEARCH_URL + Uri.encode(getRecipeName()));
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (ActivityNotFoundException e) {
            // Máy không có app nào mở được link
            showToast(R.string.msg_no_app_to_open);
        }
    }
}
