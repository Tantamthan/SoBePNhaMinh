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
```
com.sobep
├── data/local        AppDatabase, SampleData, entity/, dao/, model/
├── data/repository   RecipeRepository...
├── ui/               BaseActivity + mỗi màn hình một package (Activity, ViewModel, Adapter)
└── util/             IntentKeys, RecipeTextParser, ImageStorage, DateUtils
```

## 9 màn hình (Activity)
Splash, Main (danh sách món), RecipeDetail, AddEditRecipe, CookingMode,
MealPlan, ShoppingList, ScanRecipe, Discover.

## Cách làm việc với tôi
- Trước khi sửa: tóm tắt sẽ làm gì, liệt kê các file sẽ tạo/sửa, rồi chờ tôi đồng ý.
- Khi có lỗi build hoặc lỗi chạy: chỉ ra nguyên nhân trước, rồi mới đưa cách sửa.
- Sau khi sửa: build lại để kiểm tra
  (Git Bash: `./gradlew assembleDebug`, PowerShell: `.\gradlew.bat assembleDebug`).
- Không tự ý commit hay push Git khi tôi chưa yêu cầu.
- Trả lời bằng tiếng Việt, giải thích dễ hiểu vì tôi cần trình bày lại khi bảo vệ đồ án.
