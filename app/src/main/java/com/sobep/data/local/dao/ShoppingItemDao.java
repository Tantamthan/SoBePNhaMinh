package com.sobep.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Transaction;

import com.sobep.data.local.entity.ShoppingItem;
import com.sobep.data.local.model.IngredientTotal;

import java.util.ArrayList;
import java.util.List;

/**
 * Truy cập bảng shopping_items (danh sách đi chợ, CN11, CN12).
 */
@Dao
public abstract class ShoppingItemDao {

    // Dòng chưa mua lên trước, đã mua xuống sau
    @Query("SELECT * FROM shopping_items ORDER BY bought, id")
    public abstract LiveData<List<ShoppingItem>> getAll();

    /**
     * Gộp nguyên liệu của mọi món trong thực đơn từ startDate đến endDate (CN11):
     * cùng tên + cùng đơn vị (không phân biệt hoa thường, bỏ khoảng trắng thừa) thì cộng dồn số lượng.
     * Món xếp 2 bữa thì nguyên liệu được tính 2 lần, vì JOIN sinh ra 2 dòng.
     */
    @Query("SELECT TRIM(i.name) AS name, TRIM(i.unit) AS unit, SUM(i.quantity) AS quantity"
            + " FROM meal_plans m INNER JOIN ingredients i ON i.recipeId = m.recipeId"
            + " WHERE m.date BETWEEN :startDate AND :endDate"
            + " GROUP BY LOWER(TRIM(i.name)), LOWER(TRIM(i.unit))"
            + " ORDER BY name")
    public abstract List<IngredientTotal> getIngredientTotals(String startDate, String endDate);

    @Insert
    public abstract long insert(ShoppingItem item);

    @Insert
    public abstract void insertAll(List<ShoppingItem> items);

    @Query("UPDATE shopping_items SET bought = :bought WHERE id = :id")
    public abstract void setBought(long id, boolean bought);

    @Query("DELETE FROM shopping_items WHERE bought = 1")
    public abstract void deleteBought();

    @Query("DELETE FROM shopping_items WHERE manual = 0")
    public abstract void deleteGenerated();

    /**
     * Tạo lại danh sách đi chợ từ thực đơn: xoá các dòng tự sinh cũ (giữ dòng người dùng tự gõ),
     * rồi chèn các nguyên liệu đã gộp. Chạy trong một giao dịch.
     */
    @Transaction
    public void regenerate(String startDate, String endDate) {
        deleteGenerated();
        List<IngredientTotal> totals = getIngredientTotals(startDate, endDate);
        List<ShoppingItem> items = new ArrayList<>();
        for (IngredientTotal total : totals) {
            ShoppingItem item = new ShoppingItem();
            item.name = total.name;
            item.unit = total.unit;
            item.quantity = total.quantity;
            item.manual = false;
            items.add(item);
        }
        insertAll(items);
    }
}
