package com.sobep.data.local;

import com.sobep.data.local.dao.RecipeDao;
import com.sobep.data.local.entity.Ingredient;
import com.sobep.data.local.entity.Recipe;
import com.sobep.data.local.entity.Step;

import java.util.ArrayList;
import java.util.List;

/**
 * Hai món mẫu chèn vào sổ khi CSDL được tạo lần đầu (gọi từ AppDatabase, chạy ở luồng nền).
 * Giúp màn chính có sẵn món để mở Chi tiết món và Chế độ nấu khi chạy thử.
 */
final class SampleData {

    private SampleData() {
        // Lớp chỉ chứa hàm tĩnh, không cho tạo đối tượng
    }

    static void insertInto(RecipeDao dao) {
        insertCanhChua(dao);
        insertThitKho(dao);
    }

    private static void insertCanhChua(RecipeDao dao) {
        Recipe recipe = new Recipe();
        recipe.name = "Canh chua cá lóc";
        recipe.category = Recipe.CATEGORY_SOUP;
        recipe.cookTimeMinutes = 35;
        recipe.servings = 4;
        recipe.note = "Có thể thay cá lóc bằng cá basa hoặc tôm.";

        List<Ingredient> ingredients = new ArrayList<>();
        ingredients.add(ingredient("Cá lóc", 500, "g"));
        ingredients.add(ingredient("Me chua", 50, "g"));
        ingredients.add(ingredient("Cà chua", 2, "quả"));
        ingredients.add(ingredient("Thơm (dứa)", 0.25, "quả"));
        ingredients.add(ingredient("Bạc hà", 2, "cây"));
        ingredients.add(ingredient("Giá đỗ", 100, "g"));
        ingredients.add(ingredient("Ngò om, ngò gai", 1, "bó"));
        ingredients.add(ingredient("Nước mắm", 2, "muỗng canh"));
        ingredients.add(ingredient("Đường", 1, "muỗng canh"));

        List<Step> steps = new ArrayList<>();
        steps.add(step("Làm sạch cá, cắt khúc, ướp với chút nước mắm và tiêu.", 10));
        steps.add(step("Ngâm me với nước ấm, lọc lấy nước cốt. Cà chua, thơm cắt miếng; bạc hà tước vỏ, cắt xéo.", 0));
        steps.add(step("Đun sôi 1,5 lít nước, cho nước me và thơm vào, nêm nước mắm, đường.", 5));
        steps.add(step("Cho cá vào nấu chín, hớt bọt cho nước trong.", 10));
        steps.add(step("Thêm cà chua, bạc hà, giá; sôi lại thì tắt bếp, rắc ngò om, ngò gai.", 2));

        dao.insertFullRecipe(recipe, ingredients, steps);
    }

    private static void insertThitKho(RecipeDao dao) {
        Recipe recipe = new Recipe();
        recipe.name = "Thịt kho trứng";
        recipe.category = Recipe.CATEGORY_MAIN;
        recipe.cookTimeMinutes = 90;
        recipe.servings = 4;

        List<Ingredient> ingredients = new ArrayList<>();
        ingredients.add(ingredient("Thịt ba chỉ", 600, "g"));
        ingredients.add(ingredient("Trứng vịt", 6, "quả"));
        ingredients.add(ingredient("Nước dừa tươi", 500, "ml"));
        ingredients.add(ingredient("Hành tím", 3, "củ"));
        ingredients.add(ingredient("Tỏi", 3, "tép"));
        ingredients.add(ingredient("Nước mắm", 3, "muỗng canh"));
        ingredients.add(ingredient("Đường", 2, "muỗng canh"));

        List<Step> steps = new ArrayList<>();
        steps.add(step("Luộc trứng chín, bóc vỏ. Thịt rửa sạch, cắt miếng vuông.", 10));
        steps.add(step("Ướp thịt với hành, tỏi băm, nước mắm, đường.", 30));
        steps.add(step("Thắng đường cho có màu cánh gián, cho thịt vào đảo săn.", 0));
        steps.add(step("Đổ nước dừa ngập thịt, cho trứng vào, kho lửa nhỏ đến khi thịt mềm.", 45));

        dao.insertFullRecipe(recipe, ingredients, steps);
    }

    private static Ingredient ingredient(String name, double quantity, String unit) {
        Ingredient ingredient = new Ingredient();
        ingredient.name = name;
        ingredient.quantity = quantity;
        ingredient.unit = unit;
        return ingredient;
    }

    private static Step step(String content, int timerMinutes) {
        Step step = new Step();
        step.content = content;
        step.timerMinutes = timerMinutes;
        return step;
    }
}
