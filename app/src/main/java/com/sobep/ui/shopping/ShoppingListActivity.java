package com.sobep.ui.shopping;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;

import com.sobep.R;
import com.sobep.databinding.ActivityShoppingListBinding;
import com.sobep.ui.BaseActivity;
import com.sobep.util.IntentKeys;

/**
 * Màn 8 · Danh sách đi chợ. Có thể được MealPlan mở kèm khoảng ngày của tuần (EXTRA_WEEK_RANGE).
 * Chưa làm: danh sách nguyên liệu cần mua, đánh dấu đã mua.
 */
public class ShoppingListActivity extends BaseActivity {

    private ActivityShoppingListBinding binding;
    private String weekRange;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityShoppingListBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        applySystemBarInsets(binding.getRoot());

        weekRange = getIntent().getStringExtra(IntentKeys.EXTRA_WEEK_RANGE);
        if (TextUtils.isEmpty(weekRange)) {
            binding.textSource.setVisibility(View.GONE);
        } else {
            binding.textSource.setVisibility(View.VISIBLE);
            binding.textSource.setText(getString(R.string.format_shopping_source, weekRange));
        }

        showProgress(0, 0);

        binding.buttonBack.setOnClickListener(v -> finish());
        binding.buttonShare.setOnClickListener(v -> shareList());
        binding.buttonAddItem.setOnClickListener(v -> showComingSoon());
        binding.buttonClearBought.setOnClickListener(v -> showComingSoon());
    }

    private void showProgress(int bought, int total) {
        binding.textProgress.setText(getString(R.string.format_shopping_progress, bought, total));
        binding.progressShopping.setMax(Math.max(total, 1));
        binding.progressShopping.setProgress(bought);
        binding.textToBuyHeading.setText(getString(R.string.format_to_buy, total - bought));
        binding.textBoughtHeading.setText(getString(R.string.format_bought, bought));
    }
    private void shareList() {
        String text = TextUtils.isEmpty(weekRange)
                ? getString(R.string.shopping_title)
                : getString(R.string.format_shopping_source, weekRange);
        Intent sendIntent = new Intent(Intent.ACTION_SEND);
        sendIntent.setType("text/plain");
        sendIntent.putExtra(Intent.EXTRA_TEXT, text);
        startActivity(Intent.createChooser(sendIntent, getString(R.string.share_chooser_title)));
    }
}
