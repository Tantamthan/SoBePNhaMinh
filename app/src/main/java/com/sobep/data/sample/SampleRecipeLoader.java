package com.sobep.data.sample;

import android.content.Context;

import com.sobep.data.local.entity.Ingredient;
import com.sobep.data.local.entity.Step;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Đọc danh sách món mẫu từ assets/mon_mau.json bằng org.json (có sẵn trong Android, không cần thư viện thêm).
 * Đọc file là thao tác chậm → phải gọi ở luồng nền, không gọi trên main thread.
 */
public final class SampleRecipeLoader {

    private static final String FILE_NAME = "mon_mau.json";

    private SampleRecipeLoader() {
        // Lớp tiện ích, không tạo đối tượng
    }

    public static List<SampleRecipe> load(Context context) throws IOException, JSONException {
        String json = readAsset(context, FILE_NAME);
        JSONArray array = new JSONObject(json).getJSONArray("recipes");

        List<SampleRecipe> result = new ArrayList<>(array.length());
        for (int i = 0; i < array.length(); i++) {
            result.add(parseRecipe(array.getJSONObject(i)));
        }
        return result;
    }

    private static SampleRecipe parseRecipe(JSONObject obj) throws JSONException {
        SampleRecipe recipe = new SampleRecipe();
        recipe.id = obj.getString("id");
        recipe.name = obj.getString("name");
        recipe.category = obj.optString("category", recipe.category);
        recipe.cookTimeMinutes = obj.optInt("cookTimeMinutes", 0);
        recipe.servings = obj.optInt("servings", recipe.servings);
        // optString trả "" nếu không có ghi chú → đổi thành null cho giống món tự nhập
        String note = obj.optString("note", "");
        recipe.note = note.isEmpty() ? null : note;

        JSONArray tags = obj.optJSONArray("tags");
        if (tags != null) {
            for (int i = 0; i < tags.length(); i++) {
                recipe.tags.add(tags.getString(i));
            }
        }

        JSONArray ingredients = obj.getJSONArray("ingredients");
        for (int i = 0; i < ingredients.length(); i++) {
            JSONObject item = ingredients.getJSONObject(i);
            Ingredient ingredient = new Ingredient();
            ingredient.name = item.getString("name");
            ingredient.quantity = item.optDouble("quantity", 0);
            ingredient.unit = item.optString("unit", "");
            ingredient.position = i;
            recipe.ingredients.add(ingredient);
        }

        JSONArray steps = obj.getJSONArray("steps");
        for (int i = 0; i < steps.length(); i++) {
            JSONObject item = steps.getJSONObject(i);
            Step step = new Step();
            step.content = item.getString("content");
            step.timerMinutes = item.optInt("timerMinutes", 0);
            step.position = i;
            recipe.steps.add(step);
        }
        return recipe;
    }

    /** Đọc cả file trong assets thành chuỗi UTF-8 (giữ đúng dấu tiếng Việt). */
    private static String readAsset(Context context, String fileName) throws IOException {
        try (InputStream input = context.getAssets().open(fileName);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            return output.toString(StandardCharsets.UTF_8.name());
        }
    }
}
