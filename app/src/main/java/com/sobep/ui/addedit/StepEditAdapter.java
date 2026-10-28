package com.sobep.ui.addedit;

import android.text.Editable;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.sobep.data.local.entity.Step;
import com.sobep.databinding.ItemStepEditBinding;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter các dòng nhập bước nấu ở màn Thêm/Sửa món (item_step_edit.xml): số thứ tự, nội dung, số phút hẹn giờ, nút xoá.
 * Cách ghi chữ vào bản nháp giống IngredientEditAdapter.
 */
public class StepEditAdapter extends RecyclerView.Adapter<StepEditAdapter.StepEditViewHolder> {

    /** Activity cài listener này để xoá bước qua ViewModel. */
    public interface OnDeleteListener {
        void onDelete(int position);
    }

    private final OnDeleteListener deleteListener;
    private List<Step> items = new ArrayList<>();

    public StepEditAdapter(OnDeleteListener deleteListener) {
        this.deleteListener = deleteListener;
    }

    /** Nhận danh sách nháp từ ViewModel và vẽ lại (số thứ tự các bước cũng được đánh lại). */
    public void setItems(List<Step> newItems) {
        items = newItems != null ? newItems : new ArrayList<>();
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    @NonNull
    @Override
    public StepEditViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemStepEditBinding binding = ItemStepEditBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new StepEditViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull StepEditViewHolder holder, int position) {
        holder.bind(items.get(position), position + 1);
    }

    /** Ô trống hoặc gõ sai → 0 phút (= bước không hẹn giờ). */
    static int parseMinutes(String text) {
        try {
            return Math.max(0, Integer.parseInt(text.trim()));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    class StepEditViewHolder extends RecyclerView.ViewHolder {

        final ItemStepEditBinding binding;
        // Bước dòng này đang hiển thị; null trong lúc bind để TextWatcher không ghi nhầm
        private Step current;

        StepEditViewHolder(ItemStepEditBinding binding) {
            super(binding.getRoot());
            this.binding = binding;

            binding.editContent.addTextChangedListener(new IngredientEditAdapter.SimpleWatcher() {
                @Override
                public void afterTextChanged(Editable s) {
                    if (current != null) current.content = s.toString();
                }
            });
            binding.editTimer.addTextChangedListener(new IngredientEditAdapter.SimpleWatcher() {
                @Override
                public void afterTextChanged(Editable s) {
                    if (current != null) current.timerMinutes = parseMinutes(s.toString());
                }
            });

            binding.buttonDelete.setOnClickListener(v -> {
                int position = getBindingAdapterPosition();
                if (position != RecyclerView.NO_POSITION) {
                    deleteListener.onDelete(position);
                }
            });
        }

        void bind(Step step, int number) {
            current = null;
            binding.textNumber.setText(String.valueOf(number));
            binding.editContent.setText(step.content);
            binding.editTimer.setText(step.timerMinutes > 0 ? String.valueOf(step.timerMinutes) : "");
            current = step;
        }
    }
}
