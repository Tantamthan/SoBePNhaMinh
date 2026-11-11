package com.sobep.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.sobep.data.community.CommunityRecipe;
import com.sobep.data.local.entity.Ingredient;
import com.sobep.data.local.entity.Recipe;
import com.sobep.data.local.entity.Step;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/** Kiểm thử chuyển đổi món ↔ document bài Cộng đồng (CN15). Chạy: ./gradlew testDebugUnitTest */
public class CommunityMapperTest {

    private static Recipe sampleRecipe() {
        Recipe recipe = new Recipe();
        recipe.id = 7;
        recipe.cloudId = "abc";
        recipe.name = "  Canh chua cá lóc ";
        recipe.category = Recipe.CATEGORY_MAIN;
        recipe.cookTimeMinutes = 30;
        recipe.servings = 4;
        recipe.note = "Ăn nóng";
        recipe.imagePath = "/data/anh.jpg";
        recipe.videoUrl = "https://youtube.com/x";
        recipe.favorite = true;
        return recipe;
    }

    private static List<Ingredient> sampleIngredients() {
        Ingredient fish = new Ingredient();
        fish.name = "Cá lóc";
        fish.quantity = 500;
        fish.unit = "g";
        Ingredient salt = new Ingredient();
        salt.name = "Muối";
        salt.quantity = 0;
        salt.unit = "";
        return Arrays.asList(fish, salt);
    }

    private static List<Step> sampleSteps() {
        Step step = new Step();
        step.content = "Nấu nước";
        step.timerMinutes = 10;
        return Arrays.asList(step);
    }

    @Test
    public void postId_isUidUnderscoreCloudId() {
        assertEquals("u1_abc", CommunityMapper.postIdFor("u1", "abc"));
    }

    @Test
    public void toDocument_onlyAllowedFields_noPhotoVideoFavorite() {
        Map<String, Object> doc = CommunityMapper.toDocument("u1", "Lan", sampleRecipe(),
                sampleIngredients(), sampleSteps(), 123L);
        // Phải khớp keys().hasOnly([...]) trong firestore.rules
        assertEquals(new HashSet<>(Arrays.asList("authorUid", "authorName", "name", "category",
                "cookTimeMinutes", "servings", "note", "postedAt", "ingredients", "steps")), doc.keySet());
        assertEquals("u1", doc.get("authorUid"));
        assertEquals("Canh chua cá lóc", doc.get("name"));
        assertEquals(123L, doc.get("postedAt"));
    }

    @Test
    public void roundTrip_keepsRecipeText() {
        Map<String, Object> doc = CommunityMapper.toDocument("u1", "Lan", sampleRecipe(),
                sampleIngredients(), sampleSteps(), 123L);
        CommunityRecipe post = CommunityMapper.fromDocument("u1_abc", doc);

        assertEquals("u1_abc", post.postId);
        assertEquals("c-u1_abc", post.id);
        assertEquals("Lan", post.authorName);
        assertTrue(post.isMine("u1"));
        assertFalse(post.isMine("u2"));
        assertEquals(30, post.cookTimeMinutes);
        assertEquals(4, post.servings);
        assertEquals("Ăn nóng", post.note);
        assertEquals(2, post.ingredients.size());
        assertEquals("Cá lóc", post.ingredients.get(0).name);
        assertEquals(500, post.ingredients.get(0).quantity, 0.001);
        assertEquals(10, post.steps.get(0).timerMinutes);

        // Lưu vào sổ → sourceSampleId = "c-" + mã bài (để biết bài đã lưu)
        assertEquals("c-u1_abc", post.toRecipe().sourceSampleId);
    }

    @Test
    public void fromDocument_brokenData_usesDefaults() {
        Map<String, Object> doc = new HashMap<>();
        doc.put("name", 42);                       // sai kiểu
        doc.put("servings", 0);                    // không hợp lệ
        doc.put("ingredients", "không phải mảng");
        List<Object> steps = new ArrayList<>();
        steps.add("hỏng");
        doc.put("steps", steps);
        CommunityRecipe post = CommunityMapper.fromDocument("x", doc);

        assertEquals("", post.name);
        assertEquals(2, post.servings);
        assertEquals(Recipe.CATEGORY_MAIN, post.category);
        assertNull(post.note);
        assertTrue(post.ingredients.isEmpty());
        assertTrue(post.steps.isEmpty());

        CommunityRecipe empty = CommunityMapper.fromDocument("y", null);
        assertEquals("", empty.authorUid);
    }

    @Test
    public void toDocument_limitsNameAndItems() {
        Recipe recipe = sampleRecipe();
        StringBuilder longName = new StringBuilder();
        for (int i = 0; i < 150; i++) {
            longName.append('a');
        }
        recipe.name = longName.toString();
        List<Ingredient> many = new ArrayList<>();
        for (int i = 0; i < 120; i++) {
            Ingredient ingredient = new Ingredient();
            ingredient.name = "n" + i;
            ingredient.unit = "";
            many.add(ingredient);
        }
        Map<String, Object> doc = CommunityMapper.toDocument("u1", "Lan", recipe, many, sampleSteps(), 1L);
        assertEquals(CommunityMapper.MAX_NAME_LENGTH, ((String) doc.get("name")).length());
        assertEquals(CommunityMapper.MAX_ITEMS, ((List<?>) doc.get("ingredients")).size());
    }

    @Test
    public void authorName_neverShowsFullEmail() {
        assertEquals("Nguyễn Lan", CommunityMapper.authorNameFor(" Nguyễn Lan ", "lan@gmail.com"));
        assertEquals("lan.nguyen", CommunityMapper.authorNameFor(null, "lan.nguyen@gmail.com"));
        assertEquals("lan.nguyen", CommunityMapper.authorNameFor("  ", "lan.nguyen@gmail.com"));
        assertEquals("", CommunityMapper.authorNameFor(null, null));
    }
}
