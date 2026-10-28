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
import com.sobep.data.local.entity.Step;
import com.sobep.databinding.ItemStepBinding;

/**
 * Adapter các bước nấu ở màn Chi tiết món (item_step.xml):
 * số thứ tự trong vòng tròn, nội dung bước, nhãn "Hẹn giờ x phút" nếu bước có hẹn giờ.
 */
public class StepAdapter extends ListAdapter<Step, StepAdapter.StepViewHolder> {

    public StepAdapter() {
        super(DIFF_CALLBACK);
    }

    @NonNull
    @Override
    public StepViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemStepBinding binding = ItemStepBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new StepViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull StepViewHolder holder, int position) {
        // Danh sách đã sắp theo thứ tự trong DAO → số bước = vị trí + 1
        holder.bind(getItem(position), position + 1);
    }

    static class StepViewHolder extends RecyclerView.ViewHolder {

        private final ItemStepBinding binding;

        StepViewHolder(ItemStepBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(Step step, int number) {
            Context context = binding.getRoot().getContext();
            binding.textNumber.setText(String.valueOf(number));
            binding.textContent.setText(step.content);

            // Chỉ hiện nhãn hẹn giờ khi bước có số phút
            if (step.timerMinutes > 0) {
                binding.textTimer.setText(context.getString(R.string.format_step_timer, step.timerMinutes));
                binding.textTimer.setVisibility(View.VISIBLE);
            } else {
                binding.textTimer.setVisibility(View.GONE);
            }
        }
    }

    private static final DiffUtil.ItemCallback<Step> DIFF_CALLBACK = new DiffUtil.ItemCallback<Step>() {
        @Override
        public boolean areItemsTheSame(@NonNull Step oldItem, @NonNull Step newItem) {
            return oldItem.id == newItem.id;
        }

        @Override
        public boolean areContentsTheSame(@NonNull Step oldItem, @NonNull Step newItem) {
            return oldItem.content.equals(newItem.content)
                    && oldItem.timerMinutes == newItem.timerMinutes
                    && oldItem.position == newItem.position;
        }
    };
}
