# Sổ Bếp Nhà Mình – hướng dẫn cho Claude Code

## Dự án
Đồ án môn Lập trình Android (làm một mình). App sổ tay nấu ăn cá nhân, chạy offline hoàn toàn.
Trọng tâm thuyết trình: "Thiết kế ứng dụng nhiều màn hình (dùng nhiều Activity và Intent)".

## Ràng buộc kỹ thuật (bắt buộc)
- Ngôn ngữ: Java 17. KHÔNG dùng Kotlin, KHÔNG dùng Jetpack Compose.
- Giao diện: XML layout + ViewBinding. Mỗi màn hình là một Activity riêng
  (không dùng Fragment hay Navigation Component cho các màn hình chính).
- Kiến trúc MVVM: Activity → ViewModel (AndroidViewModel) → Repository → DAO.
- Room 2.8.x với `annotationProcessor` (KHÔNG dùng Room 3, KSP hay kapt vì dự án viết bằng Java).
- Ghi CSDL qua `AppDatabase.databaseWriteExecutor`; đọc bằng LiveData.
  KHÔNG thao tác CSDL trên main thread, KHÔNG dùng `allowMainThreadQueries()`.
- minSdk 24, package gốc `com.sobep`.
- Intent: tên key đặt trong `util/IntentKeys.java`; nhận kết quả bằng `registerForActivityResult`
  (KHÔNG dùng `startActivityForResult` đã lỗi thời).
- Mọi Activity kế thừa `ui/BaseActivity.java` và phải khai báo trong `AndroidManifest.xml`.
- Xin quyền lúc chạy (runtime permission) đúng cách cho CAMERA.

## Quy ước code
- Tên class, biến, hàm bằng tiếng Anh; chú thích (comment) bằng tiếng Việt.
- Chữ hiển thị đặt trong `res/values/strings.xml`, không viết cứng trong code Java.
- R.id không phải hằng số (AGP 8+): dùng if/else, không dùng switch.
- Viết file đầy đủ, không bỏ lửng bằng "..." hay "phần còn lại giữ nguyên".

## Cấu trúc package
Thiết kế đích:
```
com.sobep
├── data/local        AppDatabase, SampleData, entity/, dao/, model/
├── data/repository   RecipeRepository...
├── ui/               BaseActivity + mỗi màn hình một package (Activity, ViewModel, Adapter)
└── util/             IntentKeys, RecipeTextParser, ImageStorage, DateUtils
```
Hiện có (21/10/2026, sau khi dọn code demo):
```
com.sobep
├── ui/
│   ├── BaseActivity.java
│   ├── splash/     SplashActivity
│   ├── main/       MainActivity
│   ├── detail/     RecipeDetailActivity
│   ├── cooking/    CookingModeActivity
│   ├── addedit/    AddEditRecipeActivity
│   ├── scan/       ScanRecipeActivity
│   ├── mealplan/   MealPlanActivity
│   ├── shopping/   ShoppingListActivity
│   └── discover/   DiscoverActivity
├── data/local/     AppDatabase, SampleData, entity/ (5 bảng), dao/ (3 DAO), model/
├── data/repository/ RecipeRepository
└── util/           IntentKeys, DateUtils
```
ViewModel đã có ở main/, detail/, addedit/ (27/10/2026).
Chưa có: ViewModel các màn còn lại, Adapter, `RecipeTextParser`, `ImageStorage`.

## 9 màn hình (Activity)
Splash, Main (danh sách món), RecipeDetail, AddEditRecipe, CookingMode,
MealPlan, ShoppingList, ScanRecipe, Discover.

## Trạng thái (cập nhật 21/10/2026)
- Đã có: layout XML của 9 màn hình + 10 layout item + 1 bottom sheet, toàn bộ resource
  (màu, chữ, font, icon). 9 Activity đã khai báo trong Manifest, chuyển màn được bằng Intent
  (tường minh, ngầm định, `registerForActivityResult`). Build `assembleDebug` thành công.
- Đã dọn code demo: không còn dữ liệu giả (`DemoRecipe`, `demo_data.xml`), danh sách món đang trống.
- Hệ quả tạm thời: chưa mở được Chi tiết món và Chế độ nấu từ màn chính, vì chưa có món nào.
- Đã có Room (5 bảng, 3 DAO, AppDatabase, SampleData 2 món), build OK, nhưng chưa nối vào giao diện.
- 27/10: có `RecipeRepository` + 3 ViewModel (Main, RecipeDetail, AddEditRecipe), build OK, chưa nối vào Activity.
- Chưa làm: Adapter, nối ViewModel vào Activity,
  quyền CAMERA, quét chữ, hẹn giờ, thực đơn tuần, danh sách đi chợ, khám phá món.
- Các nút chưa có chức năng gọi `showComingSoon()` trong `BaseActivity`.

## Cách làm việc với tôi
- Trước khi sửa: tóm tắt sẽ làm gì, liệt kê các file sẽ tạo/sửa, rồi chờ tôi đồng ý.
- Khi có lỗi build hoặc lỗi chạy: chỉ ra nguyên nhân trước, rồi mới đưa cách sửa.
- Sau khi sửa: build lại để kiểm tra
  (Git Bash: `./gradlew assembleDebug`, PowerShell: `.\gradlew.bat assembleDebug`).
- Không tự ý commit hay push Git khi tôi chưa yêu cầu.
- Trả lời bằng tiếng Việt, giải thích dễ hiểu vì tôi cần trình bày lại khi bảo vệ đồ án.
