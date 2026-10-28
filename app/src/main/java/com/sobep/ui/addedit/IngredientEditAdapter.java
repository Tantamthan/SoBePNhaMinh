package com.sobep.ui.addedit;

import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.sobep.data.local.entity.Ingredient;
import com.sobep.databinding.ItemIngredientEditBinding;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Adapter các dòng nhập nguyên liệu ở màn Thêm/Sửa món (item_ingredient_edit.xml): tên, số lượng, đơn vị, nút xoá.
 *
 * Chữ người dùng gõ được ghi THẲNG vào đối tượng Ingredient trong danh sách nháp của ViewModel (qua TextWatcher),
 * nên dòng cuộn khuất hay xoay màn hình cũng không mất chữ.
 * Không dùng ListAdapter/DiffUtil vì các đối tượng bị sửa tại chỗ khi gõ, DiffUtil không so sánh được.
 */
public class IngredientEditAdapter extends RecyclerView.Adapter<IngredientEditAdapter.IngredientEditViewHolder> {

    /** Activity cài listener này để xoá dòng qua ViewModel. */
    public interface OnDeleteListener {
        void onDelete(int position);
    }

    // Số lượng hiện trong ô nhập dùng dấu chấm (250.5), bỏ số 0 thừa (250.0 → 250)
    private static final DecimalFormat QUANTITY_FORMAT =
            new DecimalFormat("#.##", DecimalFormatSymbols.getInstance(Locale.US));

    private final OnDeleteListener deleteListener;
    private List<Ingredient> items = new ArrayList<>();

    public IngredientEditAdapter(OnDeleteListener deleteListener) {
        this.deleteListener = deleteListener;
    }

    /** Nhận danh sách nháp từ ViewModel (sau khi thêm / xoá dòng) và vẽ lại. */
    public void setItems(List<Ingredient> newItems) {
        items = newItems != null ? newItems : new ArrayList<>();
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    @NonNull
    @Override
    public IngredientEditViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemIngredientEditBinding binding = ItemIngredientEditBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new IngredientEditViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull IngredientEditViewHolder holder, int position) {
        holder.bind(items.get(position));
    }

    /** "2.5" hoặc "2,5" → 2.5; ô trống hoặc gõ sai → 0 (= "vừa ăn"). */
    static double parseQuantity(String text) {
        try {
            return Double.parseDouble(text.trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    class IngredientEditViewHolder extends RecyclerView.ViewHolder {

        final ItemIngredientEditBinding binding;
        // Nguyên liệu dòng này đang hiển thị; null trong lúc bind để TextWatcher không ghi nhầm
        private Ingredient current;

        IngredientEditViewHolder(ItemIngredientEditBinding binding) {
            super(binding.getRoot());
            this.binding = binding;

            // Gắn TextWatcher MỘT lần khi tạo dòng, ghi vào nguyên liệu mà dòng đang hiển thị
            binding.editName.addTextChangedListener(new SimpleWatcher() {
                @Override
                public void afterTextChanged(Editable s) {
                    if (current != null) current.name = s.toString();
                }
            });
            binding.editQuantity.addTextChangedListener(new SimpleWatcher() {
                @Override
                public void afterTextChanged(Editable s) {
                    if (current != null) current.quantity = parseQuantity(s.toString());
                }
            });
            binding.editUnit.addTextChangedListener(new SimpleWatcher() {
                @Override
                public void afterTextChanged(Editable s) {
                    if (current != null) current.unit = s.toString();
                }
            });

            binding.buttonDelete.setOnClickListener(v -> {
                int position = getBindingAdapterPosition();
                if (position != RecyclerView.NO_POSITION) {
                    deleteListener.onDelete(position);
                }
            });
        }

        void bind(Ingredient ingredient) {
            // Tạm bỏ liên kết: setText bên dưới sẽ gọi TextWatcher, không được ghi vào nguyên liệu cũ
            current = null;
            binding.editName.setText(ingredient.name);
            binding.editQuantity.setText(ingredient.quantity > 0 ? QUANTITY_FORMAT.format(ingredient.quantity) : "");
            binding.editUnit.setText(ingredient.unit);
            current = ingredient;
        }
    }

    /** TextWatcher chỉ cần afterTextChanged; 2 hàm còn lại để trống. */
    abstract static class SimpleWatcher implements TextWatcher {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }
    }
}
