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
│   ├── discover/   DiscoverActivity
│   ├── auth/       LoginActivity, RegisterActivity
│   └── account/    AccountActivity
├── data/local/     AppDatabase, SampleData, entity/ (5 bảng), dao/ (3 DAO), model/
├── data/repository/ RecipeRepository, MealPlanRepository, ShoppingRepository, AuthRepository, SyncRepository,
│                    CommunityRepository
├── data/sample/    SampleRecipe, SampleRecipeLoader (món mẫu từ assets/mon_mau.json)
├── data/community/ CommunityRecipe (bài cộng đồng, kế thừa SampleRecipe)
└── util/           IntentKeys, DateUtils, CategoryNames, QuantityFormat, ImageStorage, RecipeTextParser, VietnameseText,
                    SyncMerge, CommunityMapper, FirestoreErrors
```
ViewModel đã có ở main/, detail/, addedit/, cooking/, discover/, mealplan/, shopping/, scan/, auth/, account/
(mỗi màn trừ splash).
Adapter đã có: `main/RecipeAdapter`, `detail/IngredientAdapter`, `detail/StepAdapter`,
`addedit/IngredientEditAdapter`, `addedit/StepEditAdapter`, `discover/KeywordTileAdapter`, `discover/DiscoverRecipeAdapter`,
`mealplan/DayPlanAdapter`, `mealplan/MealEntryAdapter`, `shopping/ShoppingDayAdapter`, `shopping/ShoppingItemAdapter`.
Unit test: `app/src/test/java/com/sobep/util/` – `RecipeTextParserTest`, `VietnameseTextTest`, `SyncMergeTest`,
`CommunityMapperTest` (`./gradlew testDebugUnitTest`, 30 test).

## 12 màn hình (Activity)
Splash, Main (danh sách món), RecipeDetail, AddEditRecipe, CookingMode,
MealPlan, ShoppingList, ScanRecipe, Discover, Login, Register, Account.

## Trạng thái (cập nhật 21/10/2026)
- Đã có: layout XML của 9 màn hình + 10 layout item + 1 bottom sheet, toàn bộ resource
  (màu, chữ, font, icon). 9 Activity đã khai báo trong Manifest, chuyển màn được bằng Intent
  (tường minh, ngầm định, `registerForActivityResult`). Build `assembleDebug` thành công.
- Đã dọn code demo: không còn dữ liệu giả (`DemoRecipe`, `demo_data.xml`), danh sách món đang trống.
- Hệ quả tạm thời: chưa mở được Chi tiết món và Chế độ nấu từ màn chính, vì chưa có món nào.
- Đã có Room (5 bảng, 3 DAO, AppDatabase, SampleData 2 món), build OK, nhưng chưa nối vào giao diện.
- 27/10: có `RecipeRepository` + 3 ViewModel (Main, RecipeDetail, AddEditRecipe), build OK, chưa nối vào Activity.
- 28/10: màn chính đọc CSDL (`RecipeAdapter` + `MainViewModel`), bấm thẻ mở Chi tiết món, bấm tim đổi yêu thích.
- 28/10: màn Chi tiết món đọc CSDL (`IngredientAdapter`, `StepAdapter` + `RecipeDetailViewModel`), quy đổi số người.
- 28/10: Thêm/Sửa món lưu CSDL (`IngredientEditAdapter`, `StepEditAdapter` + `AddEditRecipeViewModel`);
  xoá món = nhấn giữ thẻ ở màn chính + hộp thoại xác nhận. Đã test trên máy ảo.
- 28/10: Chế độ nấu đọc CSDL (`CookingModeViewModel`), hẹn giờ `CountDownTimer`, chuông khi hết giờ.
- 29/10: tìm kiếm (tên món / nguyên liệu) + chip lọc (loại món, Yêu thích, Dưới 30 phút) ở màn chính.
- 29/10: Khám phá món (`DiscoverViewModel`), 12 món mẫu trong `assets/mon_mau.json` (`data/sample/`), lưu vào sổ
  qua `sourceSampleId`. Thêm `util/CategoryNames`.
- 29/10: Thực đơn tuần (`MealPlanViewModel`, RecyclerView lồng nhau) → Đi chợ (`ShoppingListViewModel`):
  chia theo ngày nấu, tự lấy nguyên liệu theo thực đơn khi mở màn (`syncWithMealPlan`), tích, tự thêm, chia sẻ.
  Thêm `MealPlanRepository`, `ShoppingRepository`, `util/QuantityFormat`.
- CSDL **version 3**: `MIGRATION_1_2` thêm cột `date`, `forRecipes` vào `shopping_items`; `MIGRATION_2_3` thêm
  `recipes.cloudId` (UNIQUE). Đổi schema lần sau phải tăng version và viết Migration mới (không xoá dữ liệu người dùng).
- 02/11: màn Quét xin quyền CAMERA lúc chạy, chụp ảnh (`TakePicture` + FileProvider), chọn ảnh (`PickVisualMedia`),
  đọc chữ bằng ML Kit (`ScanRecipeViewModel`), thêm `util/ImageStorage`. Chữ quét mới điền tên món.
- 02/11: `util/RecipeTextParser` tách chữ quét vào tên món, thời gian, khẩu phần, nguyên liệu, các bước (+ hẹn giờ);
  `util/VietnameseText` bỏ dấu dùng chung với Khám phá. 8 unit test.
- 02/11: ảnh món ăn ở màn Thêm/Sửa (7c): chọn ảnh / chụp ảnh (xin quyền CAMERA), `ImageStorage.saveRecipePhoto`
  thu nhỏ 1280 px + xoay EXIF → `files/recipe_photos/`, đường dẫn ghi vào `recipes.imagePath` (không đổi CSDL).
  Bấm ảnh để đổi / xoá. File thừa xoá sau khi lưu; thoát không lưu thì xoá ảnh mới; xoá món thì xoá ảnh.
- 02/11: tìm kiếm màn chính không phân biệt dấu: `RecipeDao.filter` chỉ lọc chip, `getIngredientNames` (GROUP_CONCAT);
  `MainViewModel` ghép bằng `MediatorLiveData` và lọc từ khoá bằng `VietnameseText.containsNormalized`. Không đổi CSDL.
- 07/11: Khám phá – tiêu đề kết quả ghi cả từ khoá lẫn ô đang chọn ("Kết quả cho “trung” trong Gà"),
  khớp với cách `DiscoverViewModel` lọc (từ khoá VÀ ô). Chỉ sửa `DiscoverActivity.showHeading()` + 1 string.
- 07/11: CN13 (bước 8a) Firebase Auth email/mật khẩu, tuỳ chọn. `data/repository/AuthRepository` (không đụng Room),
  `ui/auth/` (LoginActivity, RegisterActivity, LoginViewModel, RegisterViewModel, AuthFormViewModel, AuthFormHelper),
  `ui/account/` (AccountActivity, AccountViewModel). Main có nút Tài khoản. Quyền INTERNET. **12 Activity**.
  Firebase project `sotaynauan-78901`, gói Spark (miễn phí); `app/google-services.json` nằm trong `.gitignore`.
  BoM 35.1.0, plugin google-services 4.5.0. Đã test máy ảo với tài khoản `sobep.test0711@example.com`.
- 09/11: thêm đăng nhập Google: Credential Manager (`credentials` 1.5.0 + `googleid` 1.1.1, `GetSignInWithGoogleOption`)
  ở `LoginActivity` → ID token → `LoginViewModel.signInWithGoogle` → `AuthRepository.signInWithGoogle`
  (`GoogleAuthProvider` + `signInWithCredential`). `signOut(context)` xoá thêm trạng thái Credential Manager.
  Console: đã bật Google provider, SHA-1 debug `38:7F:23:...:8E:63`. Không thêm Activity.
- 10/11: bản debug ký bằng khoá dùng chung `app/sobep-shared.keystore` (alias `sobep`, mật khẩu `android`, có trong Git)
  qua `signingConfigs.debug` → mọi máy build ra cùng SHA-1 `9E:83:17:...:8D:2E` (đăng ký trên Firebase) → Google chạy mọi máy.
  Không dùng lại `~/.android/debug.keystore`. Máy đã cài bản ký khoá cũ phải gỡ app trước khi cài lại.
- 10/11: CN14 (bước 8b) đồng bộ Firestore: `data/repository/SyncRepository` (gộp 2 chiều theo `updatedAt`,
  bia mộ `{deleted:true}` khi xoá món, `get(Source.SERVER)`, `Tasks.await` 30 s trên `databaseWriteExecutor`),
  `util/SyncMerge` + `SyncMergeTest` (9 test). Firestore: `users/{uid}/recipes/{cloudId}`, nguyên liệu/bước là mảng
  trong document, không gửi `id`/`imagePath`. Món tạo sẵn `cloudId` = `seed-canh-chua`/`seed-thit-kho`, `updatedAt = 1`;
  món mẫu `cloudId` = `"sample-" + sourceSampleId`. Rules: chỉ `request.auth.uid == userId`. Màn Tài khoản có thanh chạy,
  kết quả, lần đồng bộ gần nhất. Đã test máy ảo (máy mới, xoá lan sang máy khác, migration từ v2, mất mạng).
- 10/11: CN15 (bước 8c) tab Cộng đồng – chỉ hiện khi đã đăng nhập (tab ở Khám phá + nút "Đăng lên cộng đồng" ở Chi tiết món,
  `onResume` kiểm tra lại). Firestore `community_recipes/{uid}_{cloudId}` (chỉ chữ, 10 field, xem `util/CommunityMapper`),
  báo cáo `community_recipes/{postId}/reports/{uid}`; bài đã báo cáo ẩn trên máy (SharedPreferences `community`).
  `CommunityRecipe extends SampleRecipe`, `id = "c-" + postId` → dùng lại Adapter / bảng xem trước / `saveToBook`.
  `CommunityRepository` dùng `Tasks.withTimeout` 15 s, callback trên main thread. Rules ở `firestore.rules` (thư mục gốc,
  phải dán tay lên Console). `util/FirestoreErrors` dùng chung với `AccountViewModel`. Không đổi CSDL, vẫn 12 Activity.
  Build + unit test OK; đã test máy ảo 10/11 (2 tài khoản, mất mạng, xoay); Rules trên Console đã kiểm bằng REST (403 đúng chỗ).
- Các nút chưa có chức năng gọi `showComingSoon()` trong `BaseActivity`.

## Cách làm việc với tôi
- Trước khi sửa: tóm tắt sẽ làm gì, liệt kê các file sẽ tạo/sửa, rồi chờ tôi đồng ý.
- Khi có lỗi build hoặc lỗi chạy: chỉ ra nguyên nhân trước, rồi mới đưa cách sửa.
- Sau khi sửa: build lại để kiểm tra
  (Git Bash: `./gradlew assembleDebug`, PowerShell: `.\gradlew.bat assembleDebug`).
- Không tự ý commit hay push Git khi tôi chưa yêu cầu.
- Trả lời bằng tiếng Việt, giải thích dễ hiểu vì tôi cần trình bày lại khi bảo vệ đồ án.
