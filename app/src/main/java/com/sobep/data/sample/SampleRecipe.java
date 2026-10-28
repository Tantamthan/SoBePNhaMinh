package com.sobep.data.sample;

import androidx.annotation.Nullable;

import com.sobep.data.local.entity.Ingredient;
import com.sobep.data.local.entity.Recipe;
import com.sobep.data.local.entity.Step;

import java.util.ArrayList;
import java.util.List;

/**
 * Một món mẫu đọc từ assets/mon_mau.json (màn Khám phá, CN06).
 * Món mẫu KHÔNG nằm trong CSDL; chỉ khi người dùng bấm "Lưu vào sổ" mới chép thành món thật (bảng recipes).
 */
public class SampleRecipe {

    public String id = "";          // mã món mẫu, vd "m01" → lưu vào Recipe.sourceSampleId
    public String name = "";
    public String category = Recipe.CATEGORY_MAIN;
    public int cookTimeMinutes;
    public int servings = 2;
    @Nullable
    public String note;
    // Nhãn để lọc theo ô gợi ý: ga, trung, heo, haisan, rau, chay, canh
    public List<String> tags = new ArrayList<>();
    public List<Ingredient> ingredients = new ArrayList<>();
    public List<Step> steps = new ArrayList<>();

    /** Tạo món thật để lưu vào sổ. id = 0 → Room hiểu là món mới. */
    public Recipe toRecipe() {
        Recipe recipe = new Recipe();
        recipe.name = name;
        recipe.category = category;
        recipe.cookTimeMinutes = cookTimeMinutes;
        recipe.servings = servings;
        recipe.note = note;
        recipe.sourceSampleId = id;
        return recipe;
    }

    /** Bản sao nguyên liệu: khi lưu, DAO gán id/recipeId vào đối tượng, không được sửa vào món mẫu. */
    public List<Ingredient> copyIngredients() {
        List<Ingredient> copies = new ArrayList<>(ingredients.size());
        for (Ingredient original : ingredients) {
            Ingredient copy = new Ingredient();
            copy.name = original.name;
            copy.quantity = original.quantity;
            copy.unit = original.unit;
            copies.add(copy);
        }
        return copies;
    }

    public List<Step> copySteps() {
        List<Step> copies = new ArrayList<>(steps.size());
        for (Step original : steps) {
            Step copy = new Step();
            copy.content = original.content;
            copy.timerMinutes = original.timerMinutes;
            copies.add(copy);
        }
        return copies;
    }
}
