package com.sobep.ui.detail;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.sobep.data.local.entity.Ingredient;
import com.sobep.databinding.ItemIngredientBinding;
import com.sobep.util.QuantityFormat;

import java.util.List;

/**
 * Adapter danh sách nguyên liệu ở màn Chi tiết món (item_ingredient.xml): tên bên trái, định lượng bên phải.
 * Số lượng nhận vào đã được RecipeDetailViewModel nhân theo số người đang chọn.
 */
public class IngredientAdapter extends ListAdapter<Ingredient, IngredientAdapter.IngredientViewHolder> {

    public IngredientAdapter() {
        super(DIFF_CALLBACK);
    }

    @NonNull
    @Override
    public IngredientViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemIngredientBinding binding = ItemIngredientBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new IngredientViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull IngredientViewHolder holder, int position) {
        boolean isLast = position == getItemCount() - 1;
        holder.bind(getItem(position), isLast);
    }

    /**
     * Danh sách đổi số dòng (vd sau khi sửa món) thì "dòng cuối" đổi chỗ, nhưng DiffUtil không vẽ lại
     * dòng có nội dung giữ nguyên → vẽ lại cả danh sách (ít dòng) để đường kẻ đúng.
     */
    @Override
    public void onCurrentListChanged(@NonNull List<Ingredient> previousList,
                                     @NonNull List<Ingredient> currentList) {
        if (previousList.size() != currentList.size() && !currentList.isEmpty()) {
            notifyItemRangeChanged(0, currentList.size());
        }
    }

    /** Định lượng hiển thị: "250 g", "2 quả"; số lượng 0 → "vừa ăn". Màn Khám phá cũng dùng hàm này. */
    public static String formatQuantity(Context context, Ingredient ingredient) {
        return QuantityFormat.format(context, ingredient.quantity, ingredient.unit);
    }

    static class IngredientViewHolder extends RecyclerView.ViewHolder {

        private final ItemIngredientBinding binding;

        IngredientViewHolder(ItemIngredientBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(Ingredient ingredient, boolean isLast) {
            Context context = binding.getRoot().getContext();
            binding.textName.setText(ingredient.name);
            binding.textQuantity.setText(formatQuantity(context, ingredient));
            // Dòng cuối không cần đường kẻ bên dưới
            binding.divider.setVisibility(isLast ? View.GONE : View.VISIBLE);
        }
    }

    private static final DiffUtil.ItemCallback<Ingredient> DIFF_CALLBACK = new DiffUtil.ItemCallback<Ingredient>() {
        @Override
        public boolean areItemsTheSame(@NonNull Ingredient oldItem, @NonNull Ingredient newItem) {
            return oldItem.id == newItem.id;
        }

        @Override
        public boolean areContentsTheSame(@NonNull Ingredient oldItem, @NonNull Ingredient newItem) {
            // Đổi số người → quantity đổi → dòng đó được vẽ lại
            return oldItem.name.equals(newItem.name)
                    && oldItem.unit.equals(newItem.unit)
                    && oldItem.quantity == newItem.quantity;
        }
    };
}
