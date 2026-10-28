package com.sobep.ui.mealplan;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.sobep.R;
import com.sobep.databinding.ItemDayPlanBinding;
import com.sobep.util.DateUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter 7 thẻ ngày của Thực đơn tuần (item_day_plan.xml): tên ngày, ngày tháng, nhãn "Hôm nay",
 * nút + thêm món, và danh sách các bữa (RecyclerView lồng bên trong, dùng MealEntryAdapter).
 */
public class DayPlanAdapter extends RecyclerView.Adapter<DayPlanAdapter.DayViewHolder> {

    public interface OnDayActionListener extends MealEntryAdapter.OnMealClickListener {
        /** Bấm nút + của một ngày. */
        void onAddMealClick(DayPlan day);
    }

    private final OnDayActionListener listener;
    private List<DayPlan> items = new ArrayList<>();

    public DayPlanAdapter(OnDayActionListener listener) {
        this.listener = listener;
    }

    public void setItems(List<DayPlan> newItems) {
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
        ItemDayPlanBinding binding = ItemDayPlanBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new DayViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull DayViewHolder holder, int position) {
        holder.bind(items.get(position));
    }

    class DayViewHolder extends RecyclerView.ViewHolder {

        private final ItemDayPlanBinding binding;
        // Mỗi thẻ ngày có một adapter riêng cho các bữa, tạo một lần khi tạo thẻ
        private final MealEntryAdapter mealAdapter;

        DayViewHolder(ItemDayPlanBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
            mealAdapter = new MealEntryAdapter(listener);
            binding.recyclerMeals.setAdapter(mealAdapter);
        }

        void bind(DayPlan day) {
            Context context = binding.getRoot().getContext();
            String[] dayNames = context.getResources().getStringArray(R.array.day_names);
            binding.textDayName.setText(dayNames[day.dayIndex]);
            binding.textDate.setText(DateUtils.formatDayMonth(day.date));

            // Hôm nay: hiện nhãn + viền xanh 2dp; ngày khác: viền thường
            binding.textToday.setVisibility(day.today ? View.VISIBLE : View.GONE);
            binding.cardDay.setStrokeColor(ContextCompat.getColor(context,
                    day.today ? R.color.green_primary : R.color.border_card));
            binding.cardDay.setStrokeWidth(context.getResources().getDimensionPixelSize(
                    day.today ? R.dimen.stroke_today : R.dimen.stroke_card));

            boolean empty = day.meals.isEmpty();
            binding.textEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
            binding.recyclerMeals.setVisibility(empty ? View.GONE : View.VISIBLE);
            mealAdapter.setItems(day.meals);

            binding.buttonAddMeal.setOnClickListener(v -> listener.onAddMealClick(day));
        }
    }
}
