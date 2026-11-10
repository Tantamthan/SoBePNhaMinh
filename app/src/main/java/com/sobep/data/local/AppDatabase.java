package com.sobep.data.local;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.sobep.data.local.dao.MealPlanDao;
import com.sobep.data.local.dao.RecipeDao;
import com.sobep.data.local.dao.ShoppingItemDao;
import com.sobep.data.local.entity.Ingredient;
import com.sobep.data.local.entity.MealPlan;
import com.sobep.data.local.entity.Recipe;
import com.sobep.data.local.entity.ShoppingItem;
import com.sobep.data.local.entity.Step;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * CSDL Room của app (file sobep.db), gồm 5 bảng.
 * Dùng mẫu Singleton: cả app chỉ mở một kết nối CSDL duy nhất.
 * Lịch sử version:
 * - 1: 5 bảng ban đầu.
 * - 2: shopping_items thêm cột date, forRecipes (đi chợ chia theo ngày).
 * - 3: recipes thêm cột cloudId (đồng bộ Firestore, CN14).
 */
@Database(
        entities = {Recipe.class, Ingredient.class, Step.class, MealPlan.class, ShoppingItem.class},
        version = 3,
        exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {

    private static final String DATABASE_NAME = "sobep.db";

    /**
     * Nâng CSDL từ version 1 lên 2 mà KHÔNG mất dữ liệu: chỉ thêm 2 cột mới (cho phép null) vào shopping_items.
     * Món, nguyên liệu, thực đơn người dùng đã nhập vẫn giữ nguyên.
     */
    static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE shopping_items ADD COLUMN date TEXT");
            db.execSQL("ALTER TABLE shopping_items ADD COLUMN forRecipes TEXT");
        }
    };

    /**
     * Nâng CSDL từ version 2 lên 3 mà KHÔNG mất dữ liệu: thêm cột cloudId cho bảng recipes
     * và cấp luôn cloudId cho các món đang có:
     * - món chép từ món mẫu: "sample-" + sourceSampleId (giống cách Recipe.newCloudId làm cho món mới);
     * - món tự nhập: 32 ký tự hex ngẫu nhiên (randomblob của SQLite, vai trò như UUID).
     * Tên index phải đúng "index_recipes_cloudId" như Room tự đặt cho @Index trong Recipe, nếu không Room báo lỗi schema.
     */
    static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE recipes ADD COLUMN cloudId TEXT");
            db.execSQL("UPDATE recipes SET cloudId = 'sample-' || sourceSampleId WHERE sourceSampleId IS NOT NULL");
            // 2 món tạo sẵn (SampleData): dùng đúng mã cố định như bản cài mới, tránh trùng khi đồng bộ với máy khác.
            // Chỉ lấy món có id nhỏ nhất mang tên đó (phòng người dùng tự nhập thêm món trùng tên)
            db.execSQL("UPDATE recipes SET cloudId = 'seed-canh-chua' WHERE id = (SELECT MIN(id) FROM recipes"
                    + " WHERE name = 'Canh chua cá lóc' AND sourceSampleId IS NULL)");
            db.execSQL("UPDATE recipes SET cloudId = 'seed-thit-kho' WHERE id = (SELECT MIN(id) FROM recipes"
                    + " WHERE name = 'Thịt kho trứng' AND sourceSampleId IS NULL)");
            // Món tạo sẵn CHƯA từng sửa (updatedAt = createdAt) → về thời điểm cũ nhất như SampleData.SEED_TIME,
            // để bản trên mây (đã sửa / đã xoá ở máy khác) luôn thắng khi đồng bộ
            db.execSQL("UPDATE recipes SET createdAt = 1, updatedAt = 1"
                    + " WHERE cloudId IN ('seed-canh-chua', 'seed-thit-kho') AND updatedAt = createdAt");
            db.execSQL("UPDATE recipes SET cloudId = lower(hex(randomblob(16))) WHERE cloudId IS NULL");
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_recipes_cloudId ON recipes (cloudId)");
        }
    };

    // Nhóm 4 luồng nền dùng cho mọi thao tác ghi (thêm/sửa/xoá), để không chặn main thread
    public static final ExecutorService databaseWriteExecutor = Executors.newFixedThreadPool(4);

    // volatile: mọi luồng đều thấy ngay giá trị mới nhất của INSTANCE
    private static volatile AppDatabase INSTANCE;

    public abstract RecipeDao recipeDao();

    public abstract MealPlanDao mealPlanDao();

    public abstract ShoppingItemDao shoppingItemDao();

    public static AppDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                // Kiểm tra lại trong khối synchronized, phòng 2 luồng cùng vào một lúc
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                                    AppDatabase.class, DATABASE_NAME)
                            .addCallback(seedCallback)
                            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                            // Dự phòng: version nào chưa có Migration thì xoá CSDL tạo lại (không để app crash)
                            .fallbackToDestructiveMigration(true)
                            .build();
                }
            }
        }
        return INSTANCE;
    }

    // Chạy đúng một lần khi file CSDL được tạo lần đầu: chèn vài món mẫu để sổ không trống
    private static final RoomDatabase.Callback seedCallback = new RoomDatabase.Callback() {
        @Override
        public void onCreate(@NonNull SupportSQLiteDatabase db) {
            super.onCreate(db);
            databaseWriteExecutor.execute(() -> SampleData.insertInto(INSTANCE.recipeDao()));
        }
    };
}
