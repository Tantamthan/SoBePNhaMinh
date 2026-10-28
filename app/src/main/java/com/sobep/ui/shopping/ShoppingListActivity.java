package com.sobep.ui.shopping;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.inputmethod.EditorInfo;

import androidx.lifecycle.ViewModelProvider;

import com.sobep.R;
import com.sobep.data.local.entity.ShoppingItem;
import com.sobep.databinding.ActivityShoppingListBinding;
import com.sobep.ui.BaseActivity;
import com.sobep.util.DateUtils;
import com.sobep.util.IntentKeys;

import java.util.ArrayList;
import java.util.List;

/**
 * Màn 8 · Danh sách đi chợ (CN11, CN12), chia theo từng ngày nấu trong tuần.
 * - Mở màn → tự lấy nguyên liệu theo thực đơn tuần (gợi ý mua gì cho món nào, ngày nào), giữ dấu đã mua.
 * - MealPlan mở kèm Thứ Hai của tuần đang xem (EXTRA_WEEK_START); mở từ màn chính → tuần này.
 * - Bấm ô tích → đã mua (gạch ngang, vẫn ở trong ngày đó); tự gõ thêm → nhóm "Tự thêm".
 * - Chia sẻ danh sách còn cần mua theo từng ngày bằng Intent ngầm định ACTION_SEND.
 */
public class ShoppingListActivity extends BaseActivity {

    private ActivityShoppingListBinding binding;
    private ShoppingListViewModel viewModel;
    private ShoppingDayAdapter dayAdapter;
    // Các nhóm hiện tại, dùng khi chia sẻ
    private List<ShoppingDay> currentDays = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityShoppingListBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        applySystemBarInsets(binding.getRoot());

        // Nhận Thứ Hai của tuần mà MealPlan gửi sang; không có (mở từ màn chính) → tuần này
        String weekStart = getIntent().getStringExtra(IntentKeys.EXTRA_WEEK_START);
        if (TextUtils.isEmpty(weekStart)) {
            weekStart = DateUtils.toDateKey(DateUtils.getMondayOfWeek(0));
        }
        binding.textSource.setText(getString(R.string.format_shopping_source,
                DateUtils.formatWeekRange(DateUtils.fromDateKey(weekStart))));

        viewModel = new ViewModelProvider(this).get(ShoppingListViewModel.class);
        viewModel.start(weekStart);

        // LinearLayoutManager đã khai báo trong activity_shopping_list.xml
        dayAdapter = new ShoppingDayAdapter(viewModel::toggleBought);
        binding.recyclerDays.setAdapter(dayAdapter);

        setupActions();
        viewModel.getDays().observe(this, this::showDays);
    }

    private void setupActions() {
        binding.buttonBack.setOnClickListener(v -> finish());
        binding.buttonShare.setOnClickListener(v -> shareList());
        binding.buttonAddItem.setOnClickListener(v -> addItem());
        binding.editNewItem.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                addItem();
                return true;
            }
            return false;
        });
        binding.buttonClearBought.setOnClickListener(v -> viewModel.clearBoughtManual());
    }

    private void showDays(List<ShoppingDay> days) {
        currentDays = days != null ? days : new ArrayList<>();
        dayAdapter.setItems(currentDays);
        binding.textEmpty.setVisibility(currentDays.isEmpty() ? View.VISIBLE : View.GONE);

        // Tiến độ cả tuần + nút xoá chỉ bật khi có dòng tự thêm đã mua
        int total = 0;
        int bought = 0;
        boolean hasBoughtManual = false;
        for (ShoppingDay day : currentDays) {
            total += day.items.size();
            bought += day.boughtCount();
            if (day.isManualGroup() && day.boughtCount() > 0) {
                hasBoughtManual = true;
            }
        }
        binding.textProgress.setText(getString(R.string.format_shopping_progress, bought, total));
        binding.progressShopping.setMax(Math.max(total, 1));
        binding.progressShopping.setProgress(bought);
        binding.buttonClearBought.setVisibility(hasBoughtManual ? View.VISIBLE : View.GONE);
    }

    private void addItem() {
        if (viewModel.addItem(binding.editNewItem.getText().toString())) {
            binding.editNewItem.setText("");
        }
    }

    /**
     * Intent ngầm định: gửi các thứ CÒN CẦN MUA (chưa tích), chia theo ngày, cho app khác (Zalo, tin nhắn…).
     * Vd: "Thứ Năm 29/10:\n- Cà chua 2 quả\n- Trứng gà 2 quả".
     */
    private void shareList() {
        StringBuilder text = new StringBuilder();
        text.append(binding.textSource.getText()).append('\n').append(getString(R.string.shopping_share_header));
        int count = 0;
        for (ShoppingDay day : currentDays) {
            List<ShoppingItem> toBuy = new ArrayList<>();
            for (ShoppingItem item : day.items) {
                if (!item.bought) {
                    toBuy.add(item);
                }
            }
            if (toBuy.isEmpty()) {
                continue;
            }
            text.append("\n\n").append(dayTitle(day));
            for (ShoppingItem item : toBuy) {
                text.append('\n').append(getString(R.string.format_share_line,
                        item.name, ShoppingItemAdapter.formatQuantity(this, item)).trim());
                count++;
            }
        }
        if (count == 0) {
            showToast(R.string.msg_shopping_nothing_to_share);
            return;
        }

        Intent sendIntent = new Intent(Intent.ACTION_SEND);
        sendIntent.setType("text/plain");
        sendIntent.putExtra(Intent.EXTRA_TEXT, text.toString());
        startActivity(Intent.createChooser(sendIntent, getString(R.string.share_chooser_title)));
    }

    /** "Thứ Năm 29/10:" hoặc "Tự thêm:" khi chia sẻ. */
    private String dayTitle(ShoppingDay day) {
        if (day.isManualGroup() || day.date == null) {
            return getString(R.string.shopping_manual_group) + ":";
        }
        String dayName = getResources().getStringArray(R.array.day_names)[DateUtils.dayIndex(day.date)];
        return getString(R.string.format_share_day, dayName, DateUtils.formatDayMonth(day.date));
    }
}
