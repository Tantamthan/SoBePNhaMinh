package com.sobep.ui.discover;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.sobep.R;
import com.sobep.databinding.ItemDiscoverTileBinding;

import java.util.HashMap;
import java.util.Map;

/**
 * Adapter lưới 8 ô gợi ý nguyên liệu ở màn Khám phá (item_discover_tile.xml).
 * Danh sách ô cố định (KeywordTile.ALL); chỉ số món của mỗi ô và ô đang chọn là thay đổi.
 */
public class KeywordTileAdapter extends RecyclerView.Adapter<KeywordTileAdapter.TileViewHolder> {

    public interface OnTileClickListener {
        void onTileClick(KeywordTile tile);
    }

    private final OnTileClickListener listener;
    private Map<String, Integer> counts = new HashMap<>();
    @Nullable
    private String selectedKey;

    public KeywordTileAdapter(OnTileClickListener listener) {
        this.listener = listener;
    }

    public void setCounts(Map<String, Integer> newCounts) {
        counts = newCounts != null ? newCounts : new HashMap<>();
        notifyDataSetChanged();
    }

    public void setSelectedKey(@Nullable String key) {
        selectedKey = key;
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return KeywordTile.ALL.size();
    }

    @NonNull
    @Override
    public TileViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemDiscoverTileBinding binding = ItemDiscoverTileBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new TileViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull TileViewHolder holder, int position) {
        holder.bind(KeywordTile.ALL.get(position));
    }

    class TileViewHolder extends RecyclerView.ViewHolder {

        private final ItemDiscoverTileBinding binding;

        TileViewHolder(ItemDiscoverTileBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(KeywordTile tile) {
            Context context = binding.getRoot().getContext();
            int background = ContextCompat.getColor(context, tile.backgroundColor);
            int text = ContextCompat.getColor(context, tile.textColor);
            Integer count = counts.get(tile.key);

            binding.textLabel.setText(tile.labelRes);
            binding.textCount.setText(context.getString(R.string.format_recipe_count, count != null ? count : 0));
            binding.textLabel.setTextColor(text);
            binding.textCount.setTextColor(text);
            binding.cardTile.setCardBackgroundColor(background);

            // Ô đang chọn: viền xanh; chưa chọn: viền trùng màu nền (như không có viền)
            boolean selected = tile.key.equals(selectedKey);
            binding.cardTile.setStrokeColor(selected
                    ? ContextCompat.getColor(context, R.color.green_primary) : background);

            binding.cardTile.setOnClickListener(v -> listener.onTileClick(tile));
        }
    }
}
