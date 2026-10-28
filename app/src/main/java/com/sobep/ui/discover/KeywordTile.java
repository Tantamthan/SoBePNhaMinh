package com.sobep.ui.discover;

import androidx.annotation.ColorRes;
import androidx.annotation.StringRes;

import com.sobep.R;
import com.sobep.data.sample.SampleRecipe;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Một ô gợi ý ở màn Khám phá (Gà, Trứng, Thịt heo…): tên, màu nền / màu chữ, và cách lọc món mẫu.
 * Ô "Nấu nhanh" lọc theo thời gian nấu; các ô còn lại lọc theo nhãn (tags) trong mon_mau.json.
 */
public final class KeywordTile {

    public static final String KEY_QUICK = "nhanh";
    // Ô "Nấu nhanh ≤ 15 phút"
    public static final int QUICK_MAX_MINUTES = 15;

    /** 8 ô gợi ý, đúng thứ tự hiển thị trong lưới 2 cột. */
    public static final List<KeywordTile> ALL = Collections.unmodifiableList(Arrays.asList(
            new KeywordTile("ga", R.string.keyword_chicken, R.color.tile_chicken_bg, R.color.tile_chicken_fg),
            new KeywordTile("trung", R.string.keyword_egg, R.color.tile_egg_bg, R.color.tile_egg_fg),
            new KeywordTile("heo", R.string.keyword_pork, R.color.tile_pork_bg, R.color.tile_pork_fg),
            new KeywordTile("haisan", R.string.keyword_seafood, R.color.tile_seafood_bg, R.color.tile_seafood_fg),
            new KeywordTile("rau", R.string.keyword_veggie, R.color.tile_veggie_bg, R.color.tile_veggie_fg),
            new KeywordTile("chay", R.string.keyword_vegan, R.color.tile_vegan_bg, R.color.tile_vegan_fg),
            new KeywordTile("canh", R.string.keyword_soup, R.color.tile_soup_bg, R.color.tile_soup_fg),
            new KeywordTile(KEY_QUICK, R.string.keyword_quick, R.color.tile_quick_bg, R.color.tile_quick_fg)));

    public final String key;
    @StringRes
    public final int labelRes;
    @ColorRes
    public final int backgroundColor;
    @ColorRes
    public final int textColor;

    private KeywordTile(String key, @StringRes int labelRes, @ColorRes int backgroundColor, @ColorRes int textColor) {
        this.key = key;
        this.labelRes = labelRes;
        this.backgroundColor = backgroundColor;
        this.textColor = textColor;
    }

    /** Món mẫu có thuộc ô này không. */
    public boolean matches(SampleRecipe recipe) {
        if (KEY_QUICK.equals(key)) {
            return recipe.cookTimeMinutes <= QUICK_MAX_MINUTES;
        }
        return recipe.tags.contains(key);
    }

    /** Tìm ô theo key; null nếu không có. */
    public static KeywordTile find(String key) {
        for (KeywordTile tile : ALL) {
            if (tile.key.equals(key)) {
                return tile;
            }
        }
        return null;
    }
}
