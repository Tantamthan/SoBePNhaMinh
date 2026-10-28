package com.sobep.ui.shopping;

import android.content.Context;
import android.graphics.Paint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.sobep.R;
import com.sobep.data.local.entity.ShoppingItem;
import com.sobep.databinding.ItemShoppingBinding;
import com.sobep.util.QuantityFormat;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter các nguyên liệu trong MỘT ngày đi chợ (item_shopping.xml): ô tích, tên, "cho món nào", số lượng.
 * Nằm lồng trong ShoppingDayAdapter. Mục đã mua: chữ xám, gạch ngang, vẫn ở trong ngày của nó.
 */
public class ShoppingItemAdapter extends RecyclerView.Adapter<ShoppingItemAdapter.ShoppingViewHolder> {

    public interface OnItemToggleListener {
        void onToggle(ShoppingItem item);
    }

    private final OnItemToggleListener listener;
    private List<ShoppingItem> items = new ArrayList<>();
    // Ngày nấu từ 2 món trở lên → hiện dòng "cho món nào" dưới tên nguyên liệu
    private boolean showForRecipes;

    public ShoppingItemAdapter(OnItemToggleListener listener) {
        this.listener = listener;
    }

    public void setItems(List<ShoppingItem> newItems, boolean showForRecipes) {
        items = newItems != null ? newItems : new ArrayList<>();
        this.showForRecipes = showForRecipes;
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    @NonNull
    @Override
    public ShoppingViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemShoppingBinding binding = ItemShoppingBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ShoppingViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ShoppingViewHolder holder, int position) {
        holder.bind(items.get(position), position == items.size() - 1);
    }

    /** Số lượng hiển thị; dòng tự gõ không có số lượng → để trống (không ghi "vừa ăn"). */
    static String formatQuantity(Context context, ShoppingItem item) {
        if (item.manual && item.quantity <= 0) {
            return "";
        }
        return QuantityFormat.format(context, item.quantity, item.unit);
    }

    class ShoppingViewHolder extends RecyclerView.ViewHolder {

        private final ItemShoppingBinding binding;

        ShoppingViewHolder(ItemShoppingBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(ShoppingItem item, boolean isLast) {
            Context context = binding.getRoot().getContext();
            binding.textName.setText(item.name);
            binding.textQuantity.setText(formatQuantity(context, item));

            boolean showFor = showForRecipes && item.forRecipes != null && !item.forRecipes.isEmpty();
            binding.textForRecipes.setVisibility(showFor ? View.VISIBLE : View.GONE);
            if (showFor) {
                binding.textForRecipes.setText(context.getString(R.string.format_for_recipes, item.forRecipes));
            }

            // Gỡ listener trước khi setChecked, nếu không setChecked sẽ gọi listener cũ (ViewHolder được dùng lại)
            binding.checkBought.setOnCheckedChangeListener(null);
            binding.checkBought.setChecked(item.bought);
            binding.checkBought.setOnCheckedChangeListener((button, checked) -> listener.onToggle(item));
            // Bấm cả dòng cũng tích / bỏ tích
            binding.layoutRow.setOnClickListener(v -> listener.onToggle(item));

            // Đã mua: chữ xám + gạch ngang; chưa mua: bình thường
            int color = ContextCompat.getColor(context, item.bought ? R.color.text_secondary : R.color.text_primary);
            binding.textName.setTextColor(color);
            binding.textQuantity.setTextColor(color);
            int flags = binding.textName.getPaintFlags();
            binding.textName.setPaintFlags(item.bought
                    ? flags | Paint.STRIKE_THRU_TEXT_FLAG : flags & ~Paint.STRIKE_THRU_TEXT_FLAG);

            binding.divider.setVisibility(isLast ? View.GONE : View.VISIBLE);
        }
    }
}
