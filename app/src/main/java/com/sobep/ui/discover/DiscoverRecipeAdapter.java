package com.sobep.ui.discover;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.sobep.R;
import com.sobep.data.sample.SampleRecipe;
import com.sobep.databinding.ItemDiscoverRecipeBinding;
import com.sobep.util.CategoryNames;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Adapter danh sách món mẫu ở màn Khám phá (item_discover_recipe.xml):
 * ảnh giữ chỗ, tên, "loại · thời gian", bên phải là nhãn "Đã lưu" hoặc nút +.
 */
public class DiscoverRecipeAdapter extends RecyclerView.Adapter<DiscoverRecipeAdapter.DiscoverViewHolder> {

    public interface OnSampleClickListener {
        /** Bấm cả thẻ → xem trước món. */
        void onSampleClick(SampleRecipe sample);

        /** Bấm nút + → lưu ngay vào sổ. */
        void onAddClick(SampleRecipe sample);
    }

    // Màu nền + màu icon xoay vòng cho ảnh giữ chỗ (giống thẻ món ở màn chính)
    private static final int[] TINT_COLORS = {
            R.color.dish_tint_1, R.color.dish_tint_2, R.color.dish_tint_3,
            R.color.dish_tint_4, R.color.dish_tint_5, R.color.dish_tint_6
    };
    private static final int[] INK_COLORS = {
            R.color.dish_ink_1, R.color.dish_ink_2, R.color.dish_ink_3,
            R.color.dish_ink_4, R.color.dish_ink_5, R.color.dish_ink_6
    };

    private final OnSampleClickListener listener;
    private List<SampleRecipe> items = new ArrayList<>();
    // Mã món mẫu đã lưu → mã món trong sổ
    private Map<String, Long> saved = new HashMap<>();

    public DiscoverRecipeAdapter(OnSampleClickListener listener) {
        this.listener = listener;
    }

    public void setItems(List<SampleRecipe> newItems) {
        items = newItems != null ? newItems : new ArrayList<>();
        notifyDataSetChanged();
    }

    public void setSaved(Map<String, Long> newSaved) {
        saved = newSaved != null ? newSaved : new HashMap<>();
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    @NonNull
    @Override
    public DiscoverViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemDiscoverRecipeBinding binding = ItemDiscoverRecipeBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new DiscoverViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull DiscoverViewHolder holder, int position) {
        holder.bind(items.get(position), position);
    }

    class DiscoverViewHolder extends RecyclerView.ViewHolder {

        private final ItemDiscoverRecipeBinding binding;

        DiscoverViewHolder(ItemDiscoverRecipeBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(SampleRecipe sample, int position) {
            Context context = binding.getRoot().getContext();
            binding.textTitle.setText(sample.name);
            binding.textMeta.setText(context.getString(R.string.format_discover_meta,
                    CategoryNames.of(context, sample.category), sample.cookTimeMinutes));

            int index = position % TINT_COLORS.length;
            ViewCompat.setBackgroundTintList(binding.layoutThumb,
                    ColorStateList.valueOf(ContextCompat.getColor(context, TINT_COLORS[index])));
            binding.imageThumb.setColorFilter(ContextCompat.getColor(context, INK_COLORS[index]));

            // Đã lưu → nhãn "Đã lưu"; chưa lưu → nút +
            boolean isSaved = saved.containsKey(sample.id);
            binding.textSaved.setVisibility(isSaved ? View.VISIBLE : View.GONE);
            binding.imageAdd.setVisibility(isSaved ? View.GONE : View.VISIBLE);

            binding.cardRecipe.setOnClickListener(v -> listener.onSampleClick(sample));
            binding.imageAdd.setOnClickListener(v -> listener.onAddClick(sample));
            // Nút + bấm được nên cần mô tả cho trình đọc màn hình
            binding.imageAdd.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
            binding.imageAdd.setContentDescription(context.getString(R.string.action_save_to_book));
        }
    }
}
