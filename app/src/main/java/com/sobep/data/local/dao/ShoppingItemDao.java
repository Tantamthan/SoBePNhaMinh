package com.sobep.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Transaction;

import com.sobep.data.local.entity.ShoppingItem;
import com.sobep.data.local.model.IngredientTotal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Truy cập bảng shopping_items (danh sách đi chợ, CN11, CN12).
 */
@Dao
public abstract class ShoppingItemDao {

    // Theo ngày (dòng tự gõ có date = null nên xếp sau cùng), trong mỗi ngày dòng chưa mua lên trước
    @Query("SELECT * FROM shopping_items ORDER BY date IS NULL, date, bought, name")
    public abstract LiveData<List<ShoppingItem>> getAll();

    /**
     * Gộp nguyên liệu của các món trong thực đơn từ startDate đến endDate, THEO TỪNG NGÀY (CN11):
     * cùng ngày + cùng tên + cùng đơn vị (không phân biệt hoa thường, bỏ khoảng trắng thừa) thì cộng dồn số lượng.
     * GROUP_CONCAT(DISTINCT r.name) ghép tên các món dùng nguyên liệu đó trong ngày.
     */
    @Query("SELECT m.date AS date, TRIM(i.name) AS name, TRIM(i.unit) AS unit,"
            + " SUM(i.quantity) AS quantity, GROUP_CONCAT(DISTINCT r.name) AS forRecipes"
            + " FROM meal_plans m"
            + " INNER JOIN ingredients i ON i.recipeId = m.recipeId"
            + " INNER JOIN recipes r ON r.id = m.recipeId"
            + " WHERE m.date BETWEEN :startDate AND :endDate"
            + " GROUP BY m.date, LOWER(TRIM(i.name)), LOWER(TRIM(i.unit))"
            + " ORDER BY m.date, name")
    public abstract List<IngredientTotal> getIngredientTotals(String startDate, String endDate);

    // Các dòng sinh ra từ thực đơn (đọc một lần, để giữ dấu "đã mua" khi cập nhật lại)
    @Query("SELECT * FROM shopping_items WHERE manual = 0")
    public abstract List<ShoppingItem> getGeneratedNow();

    @Insert
    public abstract long insert(ShoppingItem item);

    @Insert
    public abstract void insertAll(List<ShoppingItem> items);

    @Query("UPDATE shopping_items SET bought = :bought WHERE id = :id")
    public abstract void setBought(long id, boolean bought);

    // Chỉ xoá dòng tự gõ đã mua; dòng từ thực đơn sẽ được sinh lại nên chỉ gạch ngang, không xoá
    @Query("DELETE FROM shopping_items WHERE bought = 1 AND manual = 1")
    public abstract void deleteBoughtManual();

    @Query("DELETE FROM shopping_items WHERE manual = 0")
    public abstract void deleteGenerated();

    /**
     * Cập nhật danh sách đi chợ theo thực đơn từ startDate đến endDate (chạy mỗi lần mở màn Đi chợ):
     * thay toàn bộ dòng tự sinh bằng nguyên liệu đã gộp theo ngày, giữ dòng người dùng tự gõ.
     * Giữ dấu "đã mua" của dòng cũ cùng ngày + tên + đơn vị, trừ khi số lượng cần mua tăng lên
     * (vd thêm món mới cùng ngày cần thêm trứng → phải mua thêm nên bỏ dấu đã mua).
     * Chạy trong một giao dịch.
     * @return số dòng nguyên liệu vừa sinh
     */
    @Transaction
    public int syncWithMealPlan(String startDate, String endDate) {
        // Ghi nhớ dòng cũ: khoá = ngày|tên|đơn vị
        Map<String, ShoppingItem> oldItems = new HashMap<>();
        for (ShoppingItem old : getGeneratedNow()) {
            oldItems.put(key(old.date, old.name, old.unit), old);
        }
        deleteGenerated();

        List<IngredientTotal> totals = getIngredientTotals(startDate, endDate);
        List<ShoppingItem> items = new ArrayList<>();
        for (IngredientTotal total : totals) {
            ShoppingItem item = new ShoppingItem();
            item.date = total.date;
            item.name = total.name;
            item.unit = total.unit;
            item.quantity = total.quantity;
            item.forRecipes = total.forRecipes == null ? null : total.forRecipes.replace(",", ", ");
            item.manual = false;

            ShoppingItem old = oldItems.get(key(item.date, item.name, item.unit));
            item.bought = old != null && old.bought && item.quantity <= old.quantity;
            items.add(item);
        }
        insertAll(items);
        return items.size();
    }

    private static String key(String date, String name, String unit) {
        return date + "|" + name.trim().toLowerCase(Locale.ROOT) + "|" + unit.trim().toLowerCase(Locale.ROOT);
    }
}
