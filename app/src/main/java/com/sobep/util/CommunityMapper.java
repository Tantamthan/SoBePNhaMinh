package com.sobep.util;

import androidx.annotation.Nullable;

import com.sobep.data.community.CommunityRecipe;
import com.sobep.data.local.entity.Ingredient;
import com.sobep.data.local.entity.Recipe;
import com.sobep.data.local.entity.Step;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * CN15: chuyển qua lại giữa món trong sổ và document bài Cộng đồng trên Firestore.
 * Java thuần (không gọi Firebase) nên kiểm thử được bằng unit test (CommunityMapperTest).
 *
 * Document community_recipes/{postId}:
 * { authorUid, authorName, name, category, cookTimeMinutes, servings, note, postedAt,
 *   ingredients: [ {name, quantity, unit} ], steps: [ {content, timerMinutes} ] }
 * Không gửi link video, yêu thích, mã món trong máy.
 * Danh sách field phải khớp với keys().hasOnly([...]) trong firestore.rules.
 *
 * Bước 8d – ảnh: field tuỳ chọn "thumb" (Blob, ảnh nhỏ ≤ 30 KB) và document con photo/main { data: Blob ≤ 300 KB }.
 * Blob là kiểu của Firebase nên CommunityRepository tự ghi / đọc 2 phần này; lớp này chỉ giữ tên field.
 */
public final class CommunityMapper {

    public static final String F_AUTHOR_UID = "authorUid";
    public static final String F_AUTHOR_NAME = "authorName";
    public static final String F_NAME = "name";
    public static final String F_CATEGORY = "category";
    public static final String F_COOK_TIME = "cookTimeMinutes";
    public static final String F_SERVINGS = "servings";
    public static final String F_NOTE = "note";
    public static final String F_POSTED_AT = "postedAt";
    public static final String F_INGREDIENTS = "ingredients";
    public static final String F_STEPS = "steps";
    public static final String F_QUANTITY = "quantity";
    public static final String F_UNIT = "unit";
    public static final String F_CONTENT = "content";
    public static final String F_TIMER = "timerMinutes";
    // Bước 8d: ảnh nhỏ trong document bài; ảnh lớn ở document con photo/main, field "data"
    public static final String F_THUMB = "thumb";
    public static final String F_PHOTO_DATA = "data";

    // Giới hạn giống firestore.rules (máy chủ kiểm tra lại lần nữa)
    public static final int MAX_NAME_LENGTH = 100;
    public static final int MAX_ITEMS = 100;

    private CommunityMapper() {
    }

    /**
     * Mã bài = "{uid}_{cloudId}": mỗi món của mỗi người chỉ có MỘT bài.
     * Đăng lại cùng món → ghi đè bài cũ (cập nhật), không sinh bài trùng.
     */
    public static String postIdFor(String uid, String cloudId) {
        return uid + "_" + cloudId;
    }

    /** Món trong sổ → dữ liệu document để gửi lên. */
    public static Map<String, Object> toDocument(String authorUid, String authorName, Recipe recipe,
                                                 List<Ingredient> ingredients, List<Step> steps, long postedAt) {
        Map<String, Object> doc = new HashMap<>();
        doc.put(F_AUTHOR_UID, authorUid);
        doc.put(F_AUTHOR_NAME, authorName);
        doc.put(F_NAME, cut(recipe.name == null ? "" : recipe.name.trim(), MAX_NAME_LENGTH));
        doc.put(F_CATEGORY, recipe.category);
        doc.put(F_COOK_TIME, recipe.cookTimeMinutes);
        doc.put(F_SERVINGS, recipe.servings);
        doc.put(F_NOTE, recipe.note);
        doc.put(F_POSTED_AT, postedAt);

        List<Map<String, Object>> ingredientList = new ArrayList<>();
        for (Ingredient ingredient : ingredients) {
            if (ingredientList.size() == MAX_ITEMS) {
                break;
            }
            Map<String, Object> item = new HashMap<>();
            item.put(F_NAME, ingredient.name);
            item.put(F_QUANTITY, ingredient.quantity);
            item.put(F_UNIT, ingredient.unit);
            ingredientList.add(item);
        }
        doc.put(F_INGREDIENTS, ingredientList);

        List<Map<String, Object>> stepList = new ArrayList<>();
        for (Step step : steps) {
            if (stepList.size() == MAX_ITEMS) {
                break;
            }
            Map<String, Object> item = new HashMap<>();
            item.put(F_CONTENT, step.content);
            item.put(F_TIMER, step.timerMinutes);
            stepList.add(item);
        }
        doc.put(F_STEPS, stepList);
        return doc;
    }

    /** Document đọc về → bài Cộng đồng. Field thiếu / sai kiểu thì lấy giá trị mặc định, không crash. */
    public static CommunityRecipe fromDocument(String postId, @Nullable Map<String, Object> data) {
        Map<String, Object> doc = data != null ? data : new HashMap<>();
        CommunityRecipe post = new CommunityRecipe();
        post.postId = postId;
        post.id = CommunityRecipe.ID_PREFIX + postId;
        post.authorUid = stringOr(doc.get(F_AUTHOR_UID), "");
        post.authorName = stringOr(doc.get(F_AUTHOR_NAME), "");
        post.name = stringOr(doc.get(F_NAME), "");
        post.category = stringOr(doc.get(F_CATEGORY), Recipe.CATEGORY_MAIN);
        post.cookTimeMinutes = numberOr(doc.get(F_COOK_TIME)).intValue();
        int servings = numberOr(doc.get(F_SERVINGS)).intValue();
        post.servings = servings > 0 ? servings : 2;
        Object note = doc.get(F_NOTE);
        post.note = note instanceof String ? (String) note : null;
        post.postedAt = numberOr(doc.get(F_POSTED_AT)).longValue();

        for (Map<?, ?> item : mapList(doc.get(F_INGREDIENTS))) {
            Ingredient ingredient = new Ingredient();
            ingredient.name = stringOr(item.get(F_NAME), "");
            ingredient.quantity = numberOr(item.get(F_QUANTITY)).doubleValue();
            ingredient.unit = stringOr(item.get(F_UNIT), "");
            post.ingredients.add(ingredient);
        }
        for (Map<?, ?> item : mapList(doc.get(F_STEPS))) {
            Step step = new Step();
            step.content = stringOr(item.get(F_CONTENT), "");
            step.timerMinutes = numberOr(item.get(F_TIMER)).intValue();
            post.steps.add(step);
        }
        return post;
    }

    /**
     * Tên hiển thị của tác giả: tên tài khoản Google nếu có, không thì phần trước @ của email.
     * Không bao giờ đưa nguyên email lên cộng đồng.
     */
    public static String authorNameFor(@Nullable String displayName, @Nullable String email) {
        if (displayName != null && !displayName.trim().isEmpty()) {
            return cut(displayName.trim(), MAX_NAME_LENGTH);
        }
        if (email != null) {
            int at = email.indexOf('@');
            String local = at > 0 ? email.substring(0, at) : email;
            if (!local.trim().isEmpty()) {
                return cut(local.trim(), MAX_NAME_LENGTH);
            }
        }
        return "";
    }

    private static String cut(String value, int max) {
        return value.length() > max ? value.substring(0, max) : value;
    }

    // Firestore trả mảng dạng List<Object>; chỉ lấy phần tử là Map (bỏ qua dữ liệu hỏng)
    private static List<Map<?, ?>> mapList(@Nullable Object value) {
        List<Map<?, ?>> result = new ArrayList<>();
        if (value instanceof List) {
            for (Object item : (List<?>) value) {
                if (item instanceof Map) {
                    result.add((Map<?, ?>) item);
                }
            }
        }
        return result;
    }

    private static String stringOr(@Nullable Object value, String fallback) {
        return value instanceof String ? (String) value : fallback;
    }

    // Số trên Firestore có thể về dạng Long (2) hoặc Double (2.5) → đọc chung qua Number
    private static Number numberOr(@Nullable Object value) {
        return value instanceof Number ? (Number) value : 0;
    }
}
