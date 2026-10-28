package com.sobep.ui.mealplan;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.sobep.R;
import com.sobep.data.local.entity.MealPlan;
import com.sobep.data.local.model.MealPlanWithRecipe;
import com.sobep.databinding.ItemMealEntryBinding;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter các bữa trong một thẻ ngày (item_meal_entry.xml): nhãn Sáng/Trưa/Tối + tên món.
 * Nằm lồng bên trong DayPlanAdapter (mỗi thẻ ngày có một MealEntryAdapter riêng).
 */
public class MealEntryAdapter extends RecyclerView.Adapter<MealEntryAdapter.MealViewHolder> {

    public interface OnMealClickListener {
        /** Bấm bữa → mở chi tiết món. */
        void onMealClick(MealPlanWithRecipe meal);

        /** Nhấn giữ bữa → hỏi bỏ món khỏi thực đơn. */
        void onMealLongClick(MealPlanWithRecipe meal);
    }

    private final OnMealClickListener listener;
    private List<MealPlanWithRecipe> items = new ArrayList<>();

    public MealEntryAdapter(OnMealClickListener listener) {
        this.listener = listener;
    }

    public void setItems(List<MealPlanWithRecipe> newItems) {
        items = newItems != null ? newItems : new ArrayList<>();
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    @NonNull
    @Override
    public MealViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemMealEntryBinding binding = ItemMealEntryBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new MealViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull MealViewHolder holder, int position) {
        holder.bind(items.get(position));
    }

    /** Tên bữa: 0 → "Sáng", 1 → "Trưa", 2 → "Tối". Màn Đi chợ cũng dùng để ghi tiêu đề ngày. */
    public static int mealNameRes(int mealType) {
        if (mealType == MealPlan.MEAL_BREAKFAST) {
            return R.string.meal_breakfast;
        } else if (mealType == MealPlan.MEAL_LUNCH) {
            return R.string.meal_lunch;
        }
        return R.string.meal_dinner;
    }

    class MealViewHolder extends RecyclerView.ViewHolder {

        private final ItemMealEntryBinding binding;

        MealViewHolder(ItemMealEntryBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(MealPlanWithRecipe meal) {
            Context context = binding.getRoot().getContext();
            int mealType = meal.mealPlan.mealType;

            // Mỗi bữa một màu nhãn: Sáng vàng, Trưa xanh dương, Tối tím
            int background;
            int text;
            if (mealType == MealPlan.MEAL_BREAKFAST) {
                background = R.color.meal_breakfast_bg;
                text = R.color.meal_breakfast_fg;
            } else if (mealType == MealPlan.MEAL_LUNCH) {
                background = R.color.meal_lunch_bg;
                text = R.color.meal_lunch_fg;
            } else {
                background = R.color.meal_dinner_bg;
                text = R.color.meal_dinner_fg;
            }
            binding.textMealTag.setText(mealNameRes(mealType));
            ViewCompat.setBackgroundTintList(binding.textMealTag,
                    ColorStateList.valueOf(ContextCompat.getColor(context, background)));
            binding.textMealTag.setTextColor(ContextCompat.getColor(context, text));
            binding.textRecipeTitle.setText(meal.recipeName);

            binding.layoutMeal.setOnClickListener(v -> listener.onMealClick(meal));
            binding.layoutMeal.setOnLongClickListener(v -> {
                listener.onMealLongClick(meal);
                return true;
            });
        }
    }
}
