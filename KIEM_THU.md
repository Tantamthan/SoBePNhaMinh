# SỔ BẾP NHÀ MÌNH – KỊCH BẢN & KẾT QUẢ KIỂM THỬ

Người test: Claude (đóng vai tester) · Bắt đầu: 16/11/2026 · Bản app: commit `acb7000` (nhánh `TAN`)
Môi trường: máy ảo `Medium_Phone` (Android API 37, 1080×2400), đang đăng nhập Google `taptanhlamgame@gmail.com`.
Cách test: chạy thật trên máy ảo, bấm bằng `adb` + đọc màn hình (`uiautomator dump`) + chụp màn hình,
kiểm dữ liệu bằng cách kéo `sobep.db` về đọc bằng sqlite (xem Phụ lục).

**Quy tắc đã thống nhất với chủ dự án**
- Chỉ đụng dữ liệu **trong máy**; món thử đặt tên bắt đầu bằng `TEST …`, test xong **xoá hết**.
- **KHÔNG** bấm "Đồng bộ ngay", **KHÔNG** đăng bài lên Cộng đồng (chỉ xem tab).
- **KHÔNG đăng xuất** tài khoản Google trên máy ảo → màn Đăng nhập / Đăng ký / Quên mật khẩu chỉ đánh giá qua code
  (hoặc chủ dự án tự bấm thử).

Ký hiệu: ✅ Đạt · ❌ Lỗi · ⚠️ Đạt nhưng có điểm cần lưu ý · ⏳ Chưa test · 🔍 Chỉ đánh giá qua code

---------------------------------------------------------------------------

## 1. TIẾN ĐỘ (cập nhật 18/11/2026 – phiên 3, xong nhóm G, H; tiếp theo J → K → L → I)

Thứ tự còn lại: **J, K, L trước rồi mới I** – để khi xoá món `TEST Ga kho gung` (đã có trong thực đơn / đi chợ)
kiểm được dữ liệu liên quan bị xoá theo.

> **Lưu ý 18/11**: máy ảo mở bằng `-no-snapshot-save` nên tắt đi là **mất hết thay đổi** (quay về bản lưu cũ ngày 10/11):
> món `TEST Mi xao bo 2` và ảnh thử đã mất, bản app cũng là bản 10/11 → đã `./gradlew installDebug` lại bản `acb7000`
> và tạo món thử mới. Phiên sau nên mở máy ảo **không** có `-no-snapshot-save` để giữ trạng thái.

| Nhóm | Chức năng | Trạng thái |
|------|-----------|-----------|
| A | Splash → Màn chính | ✅ Xong |
| B | Màn chính: danh sách, tìm kiếm, chip lọc, yêu thích (CN01, CN07–CN09) | ✅ Xong |
| C | Thêm món (CN03) | ✅ Xong (3 điểm ⚠️) |
| D | Chi tiết món + quy đổi khẩu phần (CN02) | ✅ Xong (1 điểm ⚠️) |
| E | Sửa món (CN04) | ✅ Xong |
| F | Chế độ nấu + hẹn giờ | ✅ Xong (1 điểm ⚠️) |
| G | Ảnh món (chọn / chụp / đổi / xoá) | ✅ Xong (18/11) |
| H | Quét chữ: quyền CAMERA + ML Kit + tách chữ | ✅ Xong (18/11) |
| I | Xoá món (CN05) | ⏳ |
| J | Khám phá món mẫu (CN06) | ⏳ |
| K | Thực đơn tuần (CN10) | ⏳ |
| L | Đi chợ + chia sẻ (CN11, CN12) | ⏳ |
| M | Tài khoản + Đồng bộ (CN14) – chỉ xem, không bấm đồng bộ | ⏳ |
| N | Tab Cộng đồng (CN15) – chỉ xem, xem trước bài | ⏳ |
| O | Đăng nhập / Đăng ký / Quên mật khẩu (CN13) | 🔍 ⏳ |
| P | Độ bền: xoay màn hình, tắt mở lại app, mất mạng, nút Back | ⏳ |
| Q | Dọn dữ liệu thử + tổng kết, chấm điểm từng chức năng | ⏳ |

**Trạng thái máy ảo (18/11, lúc dừng)**: đang ở màn **Quét công thức** (mở từ form Thêm món chưa lưu) với ảnh không chữ
→ phiên sau bấm ← hai lần về màn chính. Máy ảo đang chạy với `-no-snapshot-save` → **tắt đi là mất dữ liệu thử**
(nếu mất: cài lại app, tạo lại món TEST, đẩy lại ảnh).
Ảnh thử trong `/sdcard/Pictures/`: `sobep_test_photo.png`, `sobep_scan_text.png`, `sobep_scan_blank.png` (dọn ở nhóm Q).
Xoay tự động đang **tắt** (`accelerometer_rotation 0`, `user_rotation 0`) – nhóm Q bật lại bằng `accelerometer_rotation 1`.
Quyền CAMERA của app **đã cấp** (While using the app).

**Dữ liệu thử đã tạo** (phải dọn ở nhóm Q):
- id **5** `TEST Anh mon` (Món chính, 0 phút, 2 người, không nguyên liệu/bước/ảnh, không yêu thích).
- id **6** `TEST Ga kho gung` (từ Quét chữ: Món chính, 30', 3 người, 3 nguyên liệu, 3 bước, bước 3 hẹn 10') – dùng cho K, L, I.
Món `TEST Mi xao bo 2` của phiên 17/11 đã mất cùng bản lưu máy ảo.

**Dữ liệu ban đầu trong máy** (để dọn đúng về trạng thái này):

| id | Tên | cloudId | Ghi chú |
|----|-----|---------|---------|
| 1 | Canh chua cá lóc | `seed-canh-chua` | món tạo sẵn |
| 3 | Canh trứng cà chua | `sample-m01` | lưu từ Khám phá |
| 4 | Canh chua cá lóc | `sample-c-jGk8…_seed-canh-chua` | lưu từ bài Cộng đồng của chính mình |

Cả 3 món: không ảnh, không yêu thích.

---------------------------------------------------------------------------

## 2. KỊCH BẢN & KẾT QUẢ

### A. Splash → Màn chính

| Mã | Bước | Mong đợi | Kết quả |
|----|------|----------|---------|
| A1 | Mở app từ biểu tượng | Hiện `SplashActivity` rồi tự sang `MainActivity` | ✅ ~3 giây |
| A2 | Màn chính khi có món | Tiêu đề "Tất cả món", đếm "3 món", lưới 2 cột, nút "Thêm món" | ✅ |
| A3 | Bấm Back ở màn chính | Thoát app | ✅ (không hỏi lại – bình thường) |

### B. Màn chính – tìm kiếm, chip lọc, yêu thích (CN01, CN07, CN08, CN09)

| Mã | Bước | Mong đợi | Kết quả |
|----|------|----------|---------|
| B1 | Tìm `canh chua` (không dấu) | 2 món Canh chua cá lóc | ✅ 2 món, tiêu đề "Kết quả cho “canh chua”" |
| B2 | Tìm `trung` | Canh trứng cà chua | ✅ 1 món |
| B3 | Tìm `ca chua` (là **nguyên liệu** "Cà chua" của cả 3 món) | 3 món | ✅ 3 món – tìm theo nguyên liệu chạy |
| B4 | Tìm `CA LOC` (chữ hoa) | 2 món | ✅ không phân biệt hoa/thường |
| B5 | Tìm `me` (nguyên liệu "Me chua") | 2 món canh chua | ✅ |
| B6 | Tìm `  canh  ` (khoảng trắng thừa) | 3 món, tiêu đề "canh" | ✅ tự cắt khoảng trắng |
| B7 | Tìm `xyzabc` | 0 món + câu báo | ✅ "Không tìm thấy món nào khớp với từ khoá" |
| B8 | Chip **Dưới 30 phút** | Chỉ món ≤ 30 phút | ✅ 1 món (Canh trứng 15') |
| B9 | Chip **Món chính** / **Tráng miệng** | 0 món + câu báo | ✅ "Chưa có món nào ở mục này" |
| B10 | Chip **Canh** | 3 món | ✅ |
| B11 | Chip **Yêu thích** khi chưa có món yêu thích | 0 món | ✅ |
| B12 | Bấm tim món "Canh trứng" → chip Yêu thích | Món đó hiện trong Yêu thích | ✅ 1 món |
| B13 | Đang ở Yêu thích, bỏ tim | Món biến mất ngay, báo trống | ✅ |
| B14 | Chip Dưới 30 phút **+** từ khoá `chua` | Lọc kiểu VÀ | ✅ 1 món · ⚠️ tiêu đề chỉ ghi "Kết quả cho “chua”", không nói đang lọc "Dưới 30 phút" (màn Khám phá thì có ghi "… trong Gà") |

### C. Thêm món (CN03)

| Mã | Bước | Mong đợi | Kết quả |
|----|------|----------|---------|
| C1 | Bấm "Thêm món" | Mở "Thêm món mới": ảnh (Chọn/Chụp), thẻ Quét chữ, Tên, Loại món, Thời gian, Khẩu phần (mặc định 2), Link video, Nguyên liệu, Bước, Ghi chú | ✅ |
| C2 | Bấm **Lưu** khi tên trống | Báo lỗi ở ô Tên, không lưu | ✅ "Vui lòng nhập tên món" |
| C3 | Khẩu phần = 0 hoặc 99 | Báo lỗi | ✅ "Khẩu phần từ 1 đến 20 người"; ô chỉ nhận tối đa 2 chữ số (gõ 999 → 99) |
| C4 | Nhập đủ: tên, loại, 25', 2 người, 2 nguyên liệu (`Thit bo 300 g`, `Mi trung 1.5 goi`), 2 bước (1 bước có hẹn giờ) → Lưu | Về màn chính, có món mới, đếm 4 món; CSDL có đủ nguyên liệu/bước đúng thứ tự | ✅ id 5, "4 món", món mới đứng đầu; CSDL đúng thứ tự (position 0,1), hẹn giờ 1' ở bước 1, có `cloudId` |
| C5 | Dòng nguyên liệu để trống tên | Bị bỏ qua khi lưu (không tạo dòng rỗng) | ✅ dòng tên trống có số lượng 2 vẫn bị bỏ |
| C6 | Số lượng dạng `1,5` (dấu phẩy) và `1.5` | Hiểu đúng 1,5 | `1.5` ✅ lưu 1.5 · `1,5` ⚠️ ô hiện **15** – xem Lỗi #4 |
| C7 | Xoá 1 dòng nguyên liệu (nút X) | Dòng biến mất | ✅ |
| C8 | Xoay màn hình khi đang gõ dở | Giữ nguyên chữ đã gõ, giữ dòng nguyên liệu/bước | ✅ giữ đủ mọi ô, 3 dòng nguyên liệu, 2 bước + hẹn giờ (chỉ vị trí cuộn về đầu trang – chấp nhận được) |
| C9 | Gõ dở rồi bấm Back | Hỏi xác nhận bỏ thay đổi (hoặc ghi rõ hành vi hiện tại) | ⚠️ thoát ngay, mất chữ đã gõ – xem Lỗi #5 |
| C10 | Link video sai dạng (vd `abc`) | Báo lỗi hoặc bỏ qua – ghi lại hành vi | ⚠️ không kiểm tra, lưu nguyên `abc` – xem Lỗi #6 |

### D. Chi tiết món (CN02, CN09)

| Mã | Bước | Mong đợi | Kết quả |
|----|------|----------|---------|
| D1 | Mở món TEST | Đủ ảnh/tên/loại/thời gian/khẩu phần/nguyên liệu/bước | ✅ "Món chính", 25 phút, "Công thức cho 2 người", 2 nguyên liệu |
| D2 | Tăng / giảm số người | Nhân theo tỉ lệ; chặn ở 1 và 20 | ✅ 4 người → 600 g / 3 gói; 1 người → 150 g / 0.75 gói; 20 → 3000 g / 15 gói; nút mờ ở 1 và 20; nút "Nấu ngay cho N người" đổi theo; xoay màn vẫn giữ 20 người · ⚠️ xem Lỗi #7 |
| D3 | Nguyên liệu số lượng 0 ("Muối" – Canh trứng) | Hiện chữ thay cho 0 | ✅ "vừa ăn" |
| D4 | Bấm tim ở Chi tiết → về màn chính | Màn chính đổi theo | ✅ |
| D5 | Bấm Video khi link sai `abc` | Không crash | ✅ bắt `ActivityNotFoundException` → Toast · ⚠️ câu báo "không có app mở được" chưa đúng lý do (link sai). Link đúng: thử ở E |
| D6 | Nút "Đăng lên cộng đồng" khi đã đăng nhập | Hiện | ✅ (không bấm) |

### E. Sửa món (CN04)

| Mã | Bước | Mong đợi | Kết quả |
|----|------|----------|---------|
| E1 | Chi tiết → Sửa | Tiêu đề "Sửa món", form điền sẵn | ✅ đủ tên, loại, 25', 2 người, link, nguyên liệu, bước |
| E2 | Đổi tên → `TEST Mi xao bo 2`, link → `https://www.youtube.com`, thêm `Hanh tay 1 cu`, xoá bước 2 rồi thêm bước mới → Lưu | Về Chi tiết, cập nhật ngay; CSDL đúng | ✅ về Chi tiết, tên mới hiện ngay; CSDL 3 nguyên liệu (position 0–2), 2 bước đánh số lại đúng; `updatedAt` tăng, **giữ** `createdAt`, yêu thích, `cloudId` |
| E3 | (D5 tiếp) Bấm Video với link đúng | Mở app khác bằng Intent ngầm định | ✅ mở app YouTube; Back 2 lần về lại Chi tiết món |

### F. Chế độ nấu

| Mã | Bước | Mong đợi | Kết quả |
|----|------|----------|---------|
| F1 | Chi tiết → "Nấu ngay" | Bước 1/2; Bước tiếp / Bước trước | ✅ thanh tiến độ 2 đoạn; bước không hẹn giờ thì ẩn đồng hồ; bước cuối nút thành "Hoàn thành"; "Bước trước" mờ ở bước 1 |
| F2 | Hẹn giờ: Bắt đầu → Tạm dừng → Tiếp tục → hết giờ | Đếm lùi, dừng giữ số, hết giờ có chuông + "Xong" sang bước sau | ✅ tạm dừng giữ 00:31; hết giờ hiện "00:00 – Hết giờ!", có phát âm thanh (`MediaPlayer`), nút "Xong" → sang bước 2 |
| F3 | Xoay màn khi đang đếm | Không mất giờ | ✅ vẫn đếm tiếp tới hết giờ (đồng hồ nằm trong ViewModel) · ⚠️ xoay ngang: vòng đồng hồ bị đẩy xuống dưới, **phải cuộn mới thấy số và nút Tạm dừng** – xem Lỗi #8 |
| F4 | Bấm ✕ khi đang đếm | Thoát gọn, không kêu "ma" | ✅ về Chi tiết; `onCleared()` huỷ `CountDownTimer` · ⚠️ không hỏi lại dù đồng hồ đang chạy (góp ý nhỏ) |

### G. Ảnh món

| Mã | Bước | Mong đợi | Kết quả |
|----|------|----------|---------|
| G0 | Bấm "Chọn ảnh" | Mở bộ chọn ảnh hệ thống, không xin quyền bộ nhớ | ✅ Photo Picker (`PickVisualMedia`, chỉ ảnh); Android 17 hiện thêm nút "Done" – hành vi của hệ thống, không phải lỗi app |
| G4 | Chọn ảnh → thoát **không lưu** | File ảnh mới bị xoá | ✅ chọn xong có `recipe_….jpg` 39 KB (PNG 2400 px → JPEG thu nhỏ), bấm ← thì file bị xoá |
| G1 | Chọn ảnh → Lưu (món mới `TEST Anh mon`) | Ảnh hiện ở form, Chi tiết, thẻ màn chính; `imagePath` ghi vào CSDL | ✅ ảnh hiện ở cả 3 chỗ; `imagePath` = `files/recipe_photos/recipe_….jpg` (26 KB) · ⚠️ để trống thời gian → hiện "0 phút" (Lỗi #10) · món không nguyên liệu/bước vẫn lưu được (#11) |
| G1b | (phụ) Món không link → bấm Video; món không bước → Nấu ngay | Không crash | ✅ Video tự **tìm tên món trên YouTube** (cố ý, `VIDEO_SEARCH_URL`); Chế độ nấu báo "Món này chưa có bước nấu. Bấm Sửa…" |
| G2 | Bấm ảnh → "Chụp ảnh khác" lần đầu → **Don't allow**; thử lại → **cho phép** | Báo hợp lý; chụp bằng camera ảo | ✅ lần 1 hỏi quyền hệ thống; từ chối → Toast "Chưa có quyền camera. Bạn vẫn có thể chọn ảnh có sẵn", ảnh cũ giữ nguyên. Lần 2 app hiện **hộp thoại giải thích** "Cho phép dùng camera" (Để sau / Cho phép – `shouldShowRequestPermissionRationale`) rồi mới hỏi hệ thống; cho phép → mở Camera ngay, chụp → Done → ảnh mới (15 KB) thay vào form |
| G3 | Đổi ảnh → Lưu; rồi Xoá ảnh → Lưu | File cũ bị xoá sau khi lưu | ✅ bấm ảnh hiện hộp thoại Chọn ảnh khác / Chụp ảnh khác / Xoá ảnh / Huỷ (ảnh có content-desc cho TalkBack). Đổi + Lưu → file cũ bị xoá, CSDL trỏ file mới. Xoá ảnh → form về 2 nút Chọn/Chụp, file **chưa** xoá cho tới khi Lưu; Lưu → thư mục ảnh trống, `imagePath` = NULL |

### H. Quét chữ (ML Kit)

Ảnh thử vẽ bằng PowerShell `System.Drawing` (file script phải lưu UTF-8 **có BOM**, không thì PowerShell 5.1 làm hỏng dấu):
`sobep_scan_text.png` (tên, thời gian, khẩu phần, 3 nguyên liệu, 3 bước, bước 2–3 có "15 phút"/"10 phút") và
`sobep_scan_blank.png` (2 hình tròn, không chữ) – đã đẩy vào `/sdcard/Pictures/`.

| Mã | Bước | Mong đợi | Kết quả |
|----|------|----------|---------|
| H0 | Thêm món → thẻ "Quét chữ từ ảnh" | Mở màn Quét | ✅ đã có quyền CAMERA nên mở thẳng Camera; huỷ chụp → màn "Quét công thức" chờ, câu "Chụp hoặc chọn một ảnh có công thức", có nút Chụp ảnh / Chọn ảnh |
| H1 | Chọn ảnh công thức → đọc chữ → "Dùng kết quả này" → Lưu | Đọc được chữ, điền đúng các ô | ✅ báo "Đã đọc xong chữ trong ảnh" (~3 giây, chạy trên máy, không cần mạng). ML Kit sai vài dấu: `Khầu`, `Thit`, `miêng`, `Uớp`, `pht`, `thẩm` (giới hạn mô hình chữ Latin, không phải lỗi app; người dùng sửa được trong ô chữ). Form điền: tên `TEST Ga kho gung`, **30** phút, **3** người (vẫn hiểu dù chữ "Khầu"), 3 nguyên liệu tách đúng tên/số lượng/đơn vị (`500 g`, `1 củ`, `2 muỗng`), 3 bước; bước 3 tự hẹn giờ **10** phút; bước 2 không hẹn giờ vì OCR đọc "15 pht". Lưu → CSDL id **6** đúng thứ tự |
| H2 | Chọn ảnh không có chữ | Câu báo nền cam | ✅ "Không thấy chữ trong ảnh. Thử chụp lại thẳng và đủ sáng hơn" (nền cam); bấm "Dùng kết quả này" khi ô trống → Toast "Chưa có chữ nào để dùng", không chuyển màn |

### I. Xoá món (CN05) – ⏳
- I1 Nhấn giữ thẻ TEST → hộp thoại → Huỷ (món còn). I2 → Xoá (món mất, nguyên liệu/bước/ảnh/thực đơn liên quan bị xoá theo).

### J. Khám phá (CN06) – ⏳
- J1 12 món mẫu, ô từ khoá (nguyên liệu), tìm không dấu, tiêu đề "Kết quả cho … trong …".
- J2 Xem trước món mẫu → "Lưu vào sổ". J3 Lưu lại món đã lưu → không tạo bản trùng (kiểm `sourceSampleId`).

### K. Thực đơn tuần (CN10) – ⏳
- K1 Thêm món TEST vào 1 bữa → hiện đúng ngày/bữa. K2 Bỏ món khỏi bữa. K3 Chuyển tuần (nếu có).

### L. Đi chợ (CN11, CN12) – ⏳
- L1 Mở Đi chợ → tự có nguyên liệu theo thực đơn, chia theo ngày. L2 Tích / bỏ tích. L3 Tự thêm 1 món đồ.
- L4 Chia sẻ → mở bảng chia sẻ Android (Intent `ACTION_SEND`) – chỉ xem rồi huỷ.

### M. Tài khoản + Đồng bộ (CN14) – ⏳
- M1 Hiện email đang đăng nhập + "Đồng bộ gần nhất". (Đã thấy 16/11: `taptanhlamgame@gmail.com`, 08:43 10/11/2026.)
- M2 Nút Đăng xuất → hộp thoại có "Huỷ" ✅ (đã thấy, bấm Huỷ). **Không bấm Đồng bộ.**

### N. Cộng đồng (CN15) – ⏳
- N1 Tab Cộng đồng hiện ở Khám phá khi đăng nhập; danh sách bài + ảnh nhỏ.
- N2 Xem trước bài (ảnh lớn). N3 Chế độ máy bay → câu "Chưa tải được…".
- N4 Đánh giá: có nên ẩn / cảnh báo "Lưu vào sổ" với **bài của chính mình** (đang tạo món trùng – xem món id 4).

### O. Đăng nhập / Đăng ký / Quên mật khẩu (CN13) – 🔍 ⏳
- Đánh giá qua code `ui/auth/*`, `AuthRepository`: kiểm tra ô, câu lỗi tiếng Việt, khoá nút khi đang gửi, Google, quên mật khẩu.
- Chủ dự án tự thử 5 bước "Quên mật khẩu" (xem nhật ký 16/11 trong `GHI_CHU_THUYET_TRINH.md`).

### P. Độ bền – ⏳
- P1 Xoay màn hình ở mọi màn có nhập liệu. P2 Tắt hẳn app rồi mở lại → dữ liệu còn.
- P3 Chế độ máy bay ở các màn không cần mạng → vẫn dùng đủ.
- P4 Back khi bàn phím đang mở (xem Lỗi/Lưu ý #1).

---------------------------------------------------------------------------

## 3. LỖI & ĐIỂM CẦN LƯU Ý ĐÃ GHI NHẬN

| # | Mức | Mô tả | Trạng thái |
|---|-----|-------|-----------|
| 1 | ❓ Cần xác minh | Màn chính: bàn phím đang mở (vừa bấm ô tìm kiếm), gửi phím **Back qua adb** → **thoát luôn app** thay vì chỉ ẩn bàn phím. Không có code xử lý Back riêng trong app. Có thể do cách `adb input keyevent 4` chạy trên Android 17 (API 37), **chưa chắc là lỗi app** | Cần chủ dự án thử **bằng tay** trên máy ảo / máy thật |
| 2 | ⚠️ Góp ý giao diện | Màn chính: lọc chip + từ khoá, tiêu đề chỉ ghi từ khoá, không ghi chip đang chọn (khác màn Khám phá) | Ghi nhận |
| 3 | ⚠️ Góp ý | Sổ có 2 món "Canh chua cá lóc" vì đã "Lưu vào sổ" bài Cộng đồng **của chính mình** → nên ẩn nút Lưu hoặc báo "Đây là món của bạn" | Ghi nhận, kiểm tiếp ở nhóm N |
| 4 | ⚠️ Cần thử tay | Ô Số lượng (`inputType="numberDecimal"`): gõ `1,5` qua adb → Android lọc mất dấu phẩy → ô thành **15** (sai ×10). Code đã có `replace(',', '.')` (`IngredientEditAdapter.java:72`) nhưng chữ `,` không tới được code. Máy ảo đang ngôn ngữ mặc định (Anh); máy để tiếng Việt thì bàn phím/ô có thể nhận `,`. Bàn phím số thường chỉ có `.` nên người dùng ít gặp | Chủ dự án thử tay trên máy để tiếng Việt |
| 5 | ⚠️ Góp ý | Thêm/Sửa món: gõ dở rồi bấm ← (hoặc Back) → thoát ngay, **mất dữ liệu**, không hỏi "Bỏ thay đổi?". `buttonBack` gọi thẳng `finish()` (`AddEditRecipeActivity.java:231`), không có `OnBackPressedCallback` | Ghi nhận |
| 6 | ⚠️ Góp ý | Link video không kiểm tra định dạng: `abc` vẫn lưu (`AddEditRecipeActivity.java:391`). Có thể kiểm bằng `Patterns.WEB_URL` | Xem D5 bấm link sai |
| 7 | ⚠️ Góp ý | Quy đổi khẩu phần ra số lẻ khó dùng: Canh trứng 3 → 4 người thành **"2.67 quả"** trứng, **"1066.67 ml"** nước, "2.67 nhánh" hành; số lẻ dùng dấu chấm (`0.75`) thay vì dấu phẩy kiểu Việt; `3000 g` không đổi sang `3 kg`. Gợi ý: đơn vị đếm (quả, nhánh, gói, muỗng…) làm tròn 0,5; g/ml làm tròn số nguyên | Ghi nhận |
| 8 | ⚠️ Giao diện | Chế độ nấu khi **xoay ngang**: vòng hẹn giờ bị cắt, dòng "Màn hình luôn sáng" đè lên vòng; số giây + nút Tạm dừng/Đặt lại nằm dưới, phải cuộn mới thấy (ảnh `f3_land.png`). Gợi ý: thêm `layout-land/activity_cooking_mode.xml` (chữ bước bên trái, đồng hồ bên phải) hoặc thu nhỏ vòng tròn | Ghi nhận |
| 9 | 💡 Góp ý nhỏ | Chuông hết giờ phát với `AudioAttributes` usage `UNKNOWN` (`dumpsys audio`) → theo âm lượng nhạc; đặt `USAGE_ALARM` thì vẫn kêu khi người dùng tắt nhạc | Ghi nhận |
| 10 | 💡 Góp ý nhỏ | Để trống ô Thời gian nấu → lưu 0, thẻ và Chi tiết hiện **"0 phút"**; chip "Dưới 30 phút" cũng tính món này. Gợi ý: 0 thì ẩn dòng thời gian hoặc ghi "Chưa rõ" | Ghi nhận |
| 11 | 💡 Góp ý nhỏ | Món chỉ có tên (không nguyên liệu, không bước) vẫn lưu được; Chi tiết hiện khung nguyên liệu rỗng và tiêu đề "Các bước" trống. Không lỗi (Chế độ nấu có câu báo), có thể thêm câu "Chưa có nguyên liệu" cho đẹp | Ghi nhận |

---------------------------------------------------------------------------

## 4. ĐÁNH GIÁ TỪNG CHỨC NĂNG (điền khi test xong)

| Chức năng | Đánh giá | Nhận xét |
|-----------|----------|----------|
| Splash + màn chính | ✅ Tốt | Mở nhanh, đếm món đúng |
| Tìm kiếm (CN07) | ✅ Tốt | Không dấu, hoa/thường, theo nguyên liệu, cắt khoảng trắng – đều đúng |
| Chip lọc (CN08) | ✅ Tốt | Lọc đúng, kết hợp từ khoá kiểu VÀ; góp ý #2 |
| Yêu thích (CN09) | ✅ Tốt | Cập nhật ngay cả khi đang ở chip Yêu thích |
| Chi tiết + quy đổi (CN02) | ✅ Tốt (⚠️ #7) | Nhân đúng tỉ lệ, chặn 1–20, giữ khi xoay, tim đồng bộ với màn chính |
| Chế độ nấu | ✅ Tốt (⚠️ #8, #9) | Hẹn giờ đúng, giữ khi xoay, có chuông, thoát huỷ đồng hồ |
| Sửa món (CN04) | ✅ Tốt | Điền sẵn đúng, lưu giữ nguyên ngày tạo / yêu thích / cloudId (quan trọng cho đồng bộ) |
| Thêm món (CN03) | ✅ Tốt (⚠️ #4–#6, #10, #11) | Kiểm tra tên trống, khẩu phần 1–20; bỏ dòng trống; giữ dữ liệu khi xoay; lưu đúng thứ tự |
| Quét chữ (ML Kit) | ✅ Rất tốt | Đọc chữ tiếng Việt trên máy, tách đúng tên/thời gian/khẩu phần/nguyên liệu/bước + tự hẹn giờ; chịu được chữ OCR sai dấu; ảnh không chữ có câu báo rõ |
| Ảnh món | ✅ Rất tốt | Photo Picker không cần quyền bộ nhớ; xin quyền CAMERA đúng chuẩn (hỏi lần đầu → hộp thoại giải thích lần 2 → mở Camera); thu nhỏ ảnh; dọn file thừa đúng lúc (thoát không lưu, đổi ảnh, xoá ảnh) |
| Các chức năng còn lại | ⏳ | |

---------------------------------------------------------------------------

## PHỤ LỤC – Script hỗ trợ test bằng adb (Git Bash)

Lưu thành file trong thư mục tạm (không đưa vào repo), rồi `source` nó. Cần Python 3 (đọc XML màn hình, đọc sqlite).
Đổi `SP` thành thư mục tạm muốn dùng.

```bash
export MSYS_NO_PATHCONV=1            # chặn Git Bash đổi /sdcard thành C:/Program Files/Git/sdcard
export PYTHONIOENCODING=utf-8
A="$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe"
SP='<thư mục tạm>'; X="$SP/u.xml"
dump()   { "$A" shell uiautomator dump /sdcard/u.xml >/dev/null 2>&1; "$A" shell cat /sdcard/u.xml > "$X"; }
top()    { "$A" shell dumpsys activity activities | grep -m1 topResumedActivity; }   # Activity đang mở
ls_ui()  # in id | text | content-desc | bounds của mọi node (Python đọc $X)
center() # tâm của node theo id / text / content-desc, có chỉ số khi trùng
tap()    { dump; c=$(center "$1" "$2"); "$A" shell input tap $c; sleep 1.5; }
type_()  { "$A" shell input text "$1"; }      # chỉ ASCII, khoảng trắng = %s
hidekb() { kbshown && "$A" shell input keyevent 111; }   # ESC: KHÔNG dùng Back (xem lỗi #1)
kbshown(){ "$A" shell dumpsys input_method | grep -q "mInputShown=true"; }
clr()    { tap "$1"; "$A" shell input keyevent 123; for i in $(seq 1 25); do "$A" shell input keyevent 67; done; }
shot()   { "$A" exec-out screencap -p > "$SP/$1.png"; }
launch() { "$A" shell monkey -p com.sobep -c android.intent.category.LAUNCHER 1; }
pulldb() { for f in sobep.db sobep.db-wal sobep.db-shm; do "$A" exec-out run-as com.sobep cat databases/$f > "$SP/db/$f"; done; }
q()      { python -c "import sqlite3,sys;c=sqlite3.connect(sys.argv[1]);[print(r) for r in c.execute(sys.argv[2])]" "$SP/db/sobep.db" "$1"; }
```
Kinh nghiệm: không `am start` thẳng `LoginActivity` được (Activity không export) · vuốt ngang chip bắt đầu sát mép trái
bị hiểu là cử chỉ Back · trong form Thêm món, ô tên món và ô tên nguyên liệu **cùng id `editName`** → bấm theo
content-desc "Tên nguyên liệu".
