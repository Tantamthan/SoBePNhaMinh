package com.sobep.ui.shopping;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.sobep.R;
import com.sobep.data.local.model.MealPlanWithRecipe;
import com.sobep.databinding.ItemShoppingDayBinding;
import com.sobep.ui.mealplan.MealEntryAdapter;
import com.sobep.util.DateUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter các nhóm đi chợ theo ngày (item_shopping_day.xml): tiêu đề ngày + các món nấu trong ngày,
 * bên trong là danh sách nguyên liệu (RecyclerView lồng, dùng ShoppingItemAdapter).
 * Nhóm cuối "Tự thêm" gồm các thứ người dùng tự gõ.
 */
public class ShoppingDayAdapter extends RecyclerView.Adapter<ShoppingDayAdapter.DayViewHolder> {

    private final ShoppingItemAdapter.OnItemToggleListener listener;
    private List<ShoppingDay> items = new ArrayList<>();

    public ShoppingDayAdapter(ShoppingItemAdapter.OnItemToggleListener listener) {
        this.listener = listener;
    }

    public void setItems(List<ShoppingDay> newItems) {
        items = newItems != null ? newItems : new ArrayList<>();
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    @NonNull
    @Override
    public DayViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemShoppingDayBinding binding = ItemShoppingDayBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new DayViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull DayViewHolder holder, int position) {
        holder.bind(items.get(position));
    }

    /** "Tối: Canh trứng cà chua · Sáng: Trứng chiên hành lá" — các món nấu trong ngày. */
    static String buildMealsLine(Context context, ShoppingDay day) {
        StringBuilder line = new StringBuilder();
        for (MealPlanWithRecipe meal : day.meals) {
            if (line.length() > 0) {
                line.append(" · ");
            }
            String mealName = context.getString(MealEntryAdapter.mealNameRes(meal.mealPlan.mealType));
            line.append(context.getString(R.string.format_meal_line, mealName, meal.recipeName));
        }
        return line.toString();
    }

    class DayViewHolder extends RecyclerView.ViewHolder {

        private final ItemShoppingDayBinding binding;
        // Mỗi nhóm ngày có một adapter riêng cho các nguyên liệu, tạo một lần khi tạo thẻ
        private final ShoppingItemAdapter itemAdapter;

        DayViewHolder(ItemShoppingDayBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
            itemAdapter = new ShoppingItemAdapter(listener);
            binding.recyclerItems.setAdapter(itemAdapter);
        }

        void bind(ShoppingDay day) {
            Context context = binding.getRoot().getContext();

            if (day.isManualGroup() || day.date == null) {
                binding.textDayName.setText(R.string.shopping_manual_group);
                binding.textDate.setVisibility(View.GONE);
                binding.textMeals.setVisibility(View.GONE);
            } else {
                String[] dayNames = context.getResources().getStringArray(R.array.day_names);
                binding.textDayName.setText(dayNames[DateUtils.dayIndex(day.date)]);
                binding.textDate.setText(DateUtils.formatDayMonth(day.date));
                binding.textDate.setVisibility(View.VISIBLE);
                String mealsLine = buildMealsLine(context, day);
                binding.textMeals.setText(mealsLine);
                binding.textMeals.setVisibility(mealsLine.isEmpty() ? View.GONE : View.VISIBLE);
            }

            // Hôm nay: nhãn "Hôm nay" + viền xanh (giống thẻ ngày ở Thực đơn tuần)
            binding.textToday.setVisibility(day.today ? View.VISIBLE : View.GONE);
            binding.cardDay.setStrokeColor(ContextCompat.getColor(context,
                    day.today ? R.color.green_primary : R.color.border_card));
            binding.cardDay.setStrokeWidth(context.getResources().getDimensionPixelSize(
                    day.today ? R.dimen.stroke_today : R.dimen.stroke_card));

            binding.textDayProgress.setText(context.getString(R.string.format_day_progress,
                    day.boughtCount(), day.items.size()));
            itemAdapter.setItems(day.items, day.hasSeveralRecipes());
        }
    }
}
