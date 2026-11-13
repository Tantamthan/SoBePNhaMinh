package com.sobep.ui.discover;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.util.LruCache;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.sobep.R;
import com.sobep.data.community.CommunityRecipe;
import com.sobep.data.sample.SampleRecipe;
import com.sobep.databinding.ItemDiscoverRecipeBinding;
import com.sobep.util.CategoryNames;
import com.sobep.util.CloudPhoto;
import com.sobep.util.ImageStorage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Adapter danh sách món ở màn Khám phá (item_discover_recipe.xml), dùng cho cả 2 tab:
 * ảnh giữ chỗ (bài cộng đồng có ảnh thì hiện ảnh nhỏ thật), tên, "loại · thời gian" (bài cộng đồng thêm "· tên người đăng"),
 * bên phải là nhãn "Đã lưu" / "Của bạn" (bài mình đăng) hoặc nút +.
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
    // uid người đang đăng nhập, để nhận ra bài cộng đồng của chính mình
    @Nullable
    private String currentUid;
    // Ảnh nhỏ đã giải mã (khoá = CommunityRecipe.photoKey()): cuộn qua lại không phải giải mã lại.
    // Mỗi ảnh 240 px ≈ 170 KB trong RAM → 60 ảnh ≈ 10 MB, đủ cho 50 bài một lần tải
    private final LruCache<String, Bitmap> thumbCache = new LruCache<>(60);

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

    public void setCurrentUid(@Nullable String uid) {
        currentUid = uid;
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
            // Cắt ảnh theo nền bo góc của khung → ảnh thật cũng bo góc như ảnh giữ chỗ
            binding.layoutThumb.setClipToOutline(true);
        }

        void bind(SampleRecipe sample, int position) {
            Context context = binding.getRoot().getContext();
            binding.textTitle.setText(sample.name);
            boolean isMine = false;
            Bitmap photo = null;
            if (sample instanceof CommunityRecipe) {
                CommunityRecipe post = (CommunityRecipe) sample;
                isMine = post.isMine(currentUid);
                photo = thumbOf(post);
                binding.textMeta.setText(context.getString(R.string.format_community_meta,
                        CategoryNames.of(context, sample.category), sample.cookTimeMinutes,
                        authorOf(context, post)));
            } else {
                binding.textMeta.setText(context.getString(R.string.format_discover_meta,
                        CategoryNames.of(context, sample.category), sample.cookTimeMinutes));
            }

            int index = position % TINT_COLORS.length;
            ViewCompat.setBackgroundTintList(binding.layoutThumb,
                    ColorStateList.valueOf(ContextCompat.getColor(context, TINT_COLORS[index])));
            binding.imageThumb.setColorFilter(ContextCompat.getColor(context, INK_COLORS[index]));
            // Có ảnh → hiện ảnh; không có → icon bát. ViewHolder được dùng lại nên phải xoá ảnh cũ
            if (photo != null) {
                binding.imagePhoto.setImageBitmap(photo);
                binding.imagePhoto.setVisibility(View.VISIBLE);
                binding.imageThumb.setVisibility(View.GONE);
            } else {
                binding.imagePhoto.setImageDrawable(null);
                binding.imagePhoto.setVisibility(View.GONE);
                binding.imageThumb.setVisibility(View.VISIBLE);
            }

            // Bài mình đăng → nhãn "Của bạn"; đã lưu → nhãn "Đã lưu"; chưa lưu → nút +
            boolean isSaved = saved.containsKey(sample.id);
            boolean showBadge = isMine || isSaved;
            binding.textSaved.setText(isMine ? R.string.badge_mine : R.string.badge_saved);
            binding.textSaved.setVisibility(showBadge ? View.VISIBLE : View.GONE);
            binding.imageAdd.setVisibility(showBadge ? View.GONE : View.VISIBLE);

            binding.cardRecipe.setOnClickListener(v -> listener.onSampleClick(sample));
            binding.imageAdd.setOnClickListener(v -> listener.onAddClick(sample));
            // Nút + bấm được nên cần mô tả cho trình đọc màn hình
            binding.imageAdd.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
            binding.imageAdd.setContentDescription(context.getString(R.string.action_save_to_book));
        }
    }

    /**
     * Ảnh nhỏ của bài (byte JPEG ~10 KB → Bitmap). Giải mã ảnh 240 px chỉ mất khoảng 1 ms nên làm ngay
     * trên main thread, kết quả giữ trong thumbCache. Bài không có ảnh / ảnh hỏng → null.
     */
    @Nullable
    Bitmap thumbOf(CommunityRecipe post) {
        if (!post.hasPhoto()) {
            return null;
        }
        String key = post.photoKey();
        Bitmap cached = thumbCache.get(key);
        if (cached == null) {
            cached = ImageStorage.decodeBytes(post.thumb, CloudPhoto.THUMB_MAX_SIZE);
            if (cached != null) {
                thumbCache.put(key, cached);
            }
        }
        return cached;
    }

    /** Tên người đăng; bài thiếu tên thì ghi "Người dùng Sổ Bếp". Activity dùng chung cho bảng xem trước. */
    static String authorOf(Context context, CommunityRecipe post) {
        return post.authorName == null || post.authorName.isEmpty()
                ? context.getString(R.string.community_unknown_author) : post.authorName;
    }
}
