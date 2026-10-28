package com.sobep.ui.main;

import android.content.Context;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.sobep.R;
import com.sobep.data.local.entity.Recipe;
import com.sobep.databinding.ItemRecipeCardBinding;

import java.io.File;
import java.util.Objects;

/**
 * Adapter cho lưới món ở màn Sổ công thức: mỗi món là một thẻ item_recipe_card.xml.
 *
 * Dùng ListAdapter + DiffUtil: khi Room trả danh sách mới, DiffUtil so sánh với danh sách cũ
 * và chỉ vẽ lại thẻ nào thay đổi (vd bấm tim một món thì chỉ thẻ đó vẽ lại).
 */
public class RecipeAdapter extends ListAdapter<Recipe, RecipeAdapter.RecipeViewHolder> {

    /** Activity cài listener này để biết người dùng bấm vào đâu trên thẻ. */
    public interface OnRecipeClickListener {
        void onRecipeClick(Recipe recipe);

        void onFavoriteClick(Recipe recipe);
    }

    // Màu nền + màu icon cho thẻ món chưa có ảnh, chọn xoay vòng theo mã món
    private static final int[] TINT_COLORS = {
            R.color.dish_tint_1, R.color.dish_tint_2, R.color.dish_tint_3,
            R.color.dish_tint_4, R.color.dish_tint_5, R.color.dish_tint_6
    };
    private static final int[] INK_COLORS = {
            R.color.dish_ink_1, R.color.dish_ink_2, R.color.dish_ink_3,
            R.color.dish_ink_4, R.color.dish_ink_5, R.color.dish_ink_6
    };

    private final OnRecipeClickListener listener;

    public RecipeAdapter(OnRecipeClickListener listener) {
        super(DIFF_CALLBACK);
        this.listener = listener;
    }

    @NonNull
    @Override
    public RecipeViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemRecipeCardBinding binding = ItemRecipeCardBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new RecipeViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull RecipeViewHolder holder, int position) {
        holder.bind(getItem(position));
    }

    class RecipeViewHolder extends RecyclerView.ViewHolder {

        private final ItemRecipeCardBinding binding;

        RecipeViewHolder(ItemRecipeCardBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(Recipe recipe) {
            Context context = binding.getRoot().getContext();

            binding.textTitle.setText(recipe.name);
            binding.textTime.setText(context.getString(R.string.format_minutes, recipe.cookTimeMinutes));
            binding.textServings.setText(context.getString(R.string.format_people, recipe.servings));

            showPhoto(context, recipe);

            // Tim đầy = đã yêu thích, tim rỗng = chưa
            binding.buttonFavorite.setImageResource(recipe.favorite
                    ? R.drawable.ic_heart_filled : R.drawable.ic_heart_outline);
            binding.buttonFavorite.setContentDescription(context.getString(recipe.favorite
                    ? R.string.cd_remove_favorite : R.string.cd_add_favorite));

            binding.cardRecipe.setOnClickListener(v -> listener.onRecipeClick(recipe));
            binding.buttonFavorite.setOnClickListener(v -> listener.onFavoriteClick(recipe));
        }

        /** Có ảnh thì hiện ảnh; chưa có thì hiện icon bát trên nền màu. */
        private void showPhoto(Context context, Recipe recipe) {
            if (recipe.imagePath != null && new File(recipe.imagePath).exists()) {
                binding.imagePhoto.setImageURI(Uri.fromFile(new File(recipe.imagePath)));
                binding.imagePhoto.setVisibility(View.VISIBLE);
                binding.imagePlaceholder.setVisibility(View.GONE);
            } else {
                // ViewHolder được dùng lại cho món khác → phải xoá ảnh cũ
                binding.imagePhoto.setImageDrawable(null);
                binding.imagePhoto.setVisibility(View.GONE);
                binding.imagePlaceholder.setVisibility(View.VISIBLE);

                int index = (int) (recipe.id % TINT_COLORS.length);
                binding.layoutPhoto.setBackgroundColor(ContextCompat.getColor(context, TINT_COLORS[index]));
                binding.imagePlaceholder.setColorFilter(ContextCompat.getColor(context, INK_COLORS[index]));
            }
        }
    }

    // So sánh 2 danh sách: cùng mã món = cùng một thẻ; nội dung khác thì vẽ lại thẻ đó
    private static final DiffUtil.ItemCallback<Recipe> DIFF_CALLBACK = new DiffUtil.ItemCallback<Recipe>() {
        @Override
        public boolean areItemsTheSame(@NonNull Recipe oldItem, @NonNull Recipe newItem) {
            return oldItem.id == newItem.id;
        }

        @Override
        public boolean areContentsTheSame(@NonNull Recipe oldItem, @NonNull Recipe newItem) {
            return oldItem.name.equals(newItem.name)
                    && oldItem.cookTimeMinutes == newItem.cookTimeMinutes
                    && oldItem.servings == newItem.servings
                    && oldItem.favorite == newItem.favorite
                    && Objects.equals(oldItem.imagePath, newItem.imagePath);
        }
    };
}
