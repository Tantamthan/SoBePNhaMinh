package com.sobep.ui.mealplan;

import android.content.Intent;
import android.os.Bundle;

import com.sobep.R;
import com.sobep.databinding.ActivityMealPlanBinding;
import com.sobep.ui.BaseActivity;
import com.sobep.ui.shopping.ShoppingListActivity;
import com.sobep.util.DateUtils;
import com.sobep.util.IntentKeys;

import java.util.Calendar;

/**
 * Màn 7 · Thực đơn tuần: chuyển qua lại giữa các tuần, nút tạo danh sách đi chợ.
 * Bấm "Tạo danh sách đi chợ" sẽ mở ShoppingList và gửi kèm khoảng ngày của tuần đang xem.
 * Chưa làm: danh sách 7 ngày và các bữa ăn.
 */
public class MealPlanActivity extends BaseActivity {

    private static final String STATE_WEEK_OFFSET = "week_offset";

    private ActivityMealPlanBinding binding;
    // 0 = tuần này, -1 = tuần trước, 1 = tuần sau…
    private int weekOffset = 0;
    private String weekRange;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMealPlanBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        applySystemBarInsets(binding.getRoot());

        if (savedInstanceState != null) {
            weekOffset = savedInstanceState.getInt(STATE_WEEK_OFFSET, 0);
        }

        binding.buttonBack.setOnClickListener(v -> finish());
        binding.buttonPrevWeek.setOnClickListener(v -> {
            weekOffset--;
            showWeek();
        });
        binding.buttonNextWeek.setOnClickListener(v -> {
            weekOffset++;
            showWeek();
        });

        binding.fabCreateShoppingList.setOnClickListener(v -> {
            Intent intent = new Intent(this, ShoppingListActivity.class);
            intent.putExtra(IntentKeys.EXTRA_WEEK_RANGE, weekRange);
            startActivity(intent);
        });

        showWeek();
    }

    private void showWeek() {
        Calendar monday = DateUtils.getMondayOfWeek(weekOffset);
        weekRange = DateUtils.formatWeekRange(monday);
        binding.textWeekRange.setText(weekRange);

        if (weekOffset == 0) {
            binding.textWeekTitle.setText(R.string.this_week);
        } else if (weekOffset == -1) {
            binding.textWeekTitle.setText(R.string.week_previous);
        } else if (weekOffset == 1) {
            binding.textWeekTitle.setText(R.string.week_next);
        } else {
            binding.textWeekTitle.setText(getString(R.string.format_week_number, DateUtils.getWeekNumber(monday)));
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_WEEK_OFFSET, weekOffset);
    }
}
