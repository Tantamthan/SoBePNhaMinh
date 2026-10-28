package com.sobep.ui.detail;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.sobep.R;
import com.sobep.data.local.entity.Ingredient;
import com.sobep.databinding.ItemIngredientBinding;

import java.text.DecimalFormat;
import java.util.List;

/**
 * Adapter danh sách nguyên liệu ở màn Chi tiết món (item_ingredient.xml): tên bên trái, định lượng bên phải.
 * Số lượng nhận vào đã được RecipeDetailViewModel nhân theo số người đang chọn.
 */
public class IngredientAdapter extends ListAdapter<Ingredient, IngredientAdapter.IngredientViewHolder> {

    // Tối đa 2 chữ số thập phân, bỏ số 0 thừa: 250.0 → "250", 0.125 → "0,13" (dấu phẩy theo ngôn ngữ máy)
    private static final DecimalFormat QUANTITY_FORMAT = new DecimalFormat("#.##");

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

    /** Định lượng hiển thị: "250 g", "2 quả"; số lượng 0 → "vừa ăn". */
    static String formatQuantity(Context context, Ingredient ingredient) {
        if (ingredient.quantity <= 0) {
            return context.getString(R.string.qty_to_taste);
        }
        String number = QUANTITY_FORMAT.format(ingredient.quantity);
        if (ingredient.unit.trim().isEmpty()) {
            return number;
        }
        return context.getString(R.string.format_quantity, number, ingredient.unit.trim());
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
