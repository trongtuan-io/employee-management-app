# Employee Management App

Phần mềm nội bộ **quản lý nhân viên** — đồ án môn *Thiết kế phần mềm di động* (nhóm 4 người).

## Kiến trúc & công nghệ

```
App Android (Java + Retrofit + Glide)  →  REST API (PHP, chạy trên XAMPP)  →  MySQL
```

- App **không** nối trực tiếp vào database; mọi dữ liệu đi qua API dưới dạng JSON.
- Đăng nhập cấp **token Bearer** (server lưu hash SHA-256 trong bảng `api_tokens`, hạn 7 ngày). Mọi quyết định phân quyền đều do **server** suy ra từ token — client không tự khai danh tính.
- Toàn bộ giờ giấc hệ thống (chấm công, hạn token, thời điểm xử lý đơn…) theo múi giờ **Asia/Ho_Chi_Minh**.

## Tính năng

### 1. Tài khoản & phân quyền theo phòng ban

Quyền = **Vai trò × Phòng ban** (3 vai trò × 7 phòng: IT, HR, ACC, SALES, MKT, OPS, CS).

| Vai trò | Chức năng chính |
|---|---|
| **Admin** | Xem tổng quan toàn công ty; quản lý nhân viên mọi phòng; tạo/khoá/mở tài khoản; import hàng loạt; chốt/mở lại bảng lương; quản lý phụ cấp chính sách |
| **Trưởng phòng (manager)** | Quản lý nhân viên **phòng mình** (xem/sửa/tạo/khoá); duyệt đơn nghỉ + đơn giải trình của phòng mình; xem tổng quan số liệu phòng mình |
| **Nhân viên (staff)** | Xem hồ sơ; chấm công; xin nghỉ; giải trình quên chấm công; xem lương của mình (sau khi đã chốt); đổi mật khẩu |

Quy tắc theo phòng:

- **Trang Quản lý nhân viên**: chỉ Admin thấy toàn công ty; trưởng phòng chỉ thấy phòng mình; staff không truy cập được trang này.
- **Nhân sự (HR)**: trưởng phòng HR đổi lương cơ bản cho nhân viên phòng mình; nhân viên HR được xem hàng chờ duyệt phép/giải trình để theo dõi. Tạo NV cho phòng khác và import hàng loạt do Admin phụ trách.
- **Kế toán (ACC)**: cả phòng Kế toán **soạn nháp** — tính lương tự động + điều chỉnh các khoản cho **toàn công ty**, xem báo cáo chấm công để đối chiếu, xuất Excel; **chốt và đánh dấu đã trả lương chỉ Kế toán trưởng** (và Admin).

### 2. Chấm công & GPS

- Chấm công vào/ra ngay ở Trang chủ; vào sau **08:15** tính là đi muộn. Tổng quan của quản lý hiện: NV đang làm, đơn chờ duyệt, số người đi muộn hôm nay (theo phạm vi phòng).
- App xin quyền vị trí và gửi kèm `lat/lng` khi chấm công (không có vị trí vẫn chấm được). Server đối chiếu với toạ độ văn phòng (hằng `OFFICE_LAT` / `OFFICE_LNG` / `OFFICE_RADIUS_M` trong `api/attendances.php`): xa hơn bán kính chỉ **gắn cờ "Ngoài văn phòng"** để quản lý đối chiếu, **không chặn** chấm công.
- Mốc hiện tại: **Trường Đại học Sư phạm Hà Nội, 136 Xuân Thủy, Cầu Giấy** — `21.0378, 105.7830`, bán kính **500m**. Đổi địa điểm chỉ cần sửa 3 hằng số này.

### 3. Xin nghỉ & quỹ phép năm

- Màn hình theo mẫu cổng nghỉ phép: 2 tab **Đơn của tôi / Chờ duyệt**, thẻ thống kê bấm để lọc, lọc theo khoảng ngày, tạo đơn bằng DatePicker.
- Mỗi đơn có **loại nghỉ**: `annual` phép năm · `sick` nghỉ ốm · `personal` việc riêng · `unpaid` nghỉ không lương.
- **Quỹ phép: 12 ngày phép năm/năm** (đếm ngày làm việc, trừ Chủ nhật). Màn Xin nghỉ hiện số dư của mình; tạo đơn phép năm vượt số dư bị server từ chối (HTTP 422) kèm thông báo còn bao nhiêu ngày. Endpoint `leave-balance` trả `{quota, used, pending, remaining}`.
- Đơn đã duyệt/từ chối **không biến mất**: vẫn ở tab Chờ duyệt kèm tên người xử lý + thời điểm (`processed_at`) để truy vết.
- Đơn nghỉ không lương **không** được tính ngày công khi tính lương.
- **Chặn tự duyệt đơn**: quản lý không tự duyệt được đơn nghỉ/giải trình của chính mình (API trả 403) — đơn của quản lý chỉ Admin hoặc quản lý khác cùng phòng duyệt được. Quy tắc này áp dụng cho cả đơn giải trình ở mục 4.

### 4. Giải trình quên chấm công

- Nhân viên tạo đơn giải trình (ngày, quên vào/ra/cả hai, giờ đề xuất, lý do) từ nút ở thẻ chấm công Trang chủ.
- Duyệt theo kỷ luật phòng như đơn nghỉ (Admin + trưởng phòng của người nộp; HR xem theo dõi). Khi được duyệt, hệ thống **bổ sung giờ đề xuất vào bảng chấm công** — chỉ điền phần đang thiếu, không ghi đè dữ liệu chấm thật.

### 5. Trung tâm thông báo

Bảng `notifications` do API tự tạo; mỗi thông báo thuộc về đúng 1 người nhận. Sự kiện tự sinh:

| Sự kiện | Người nhận |
|---|---|
| Có đơn nghỉ mới | Admin + trưởng phòng của người nộp + toàn bộ phòng HR |
| Đơn nghỉ được duyệt / bị từ chối | Người nộp (kèm tên người xử lý) |
| Có đơn giải trình mới / được duyệt / bị từ chối | Như đơn nghỉ / người nộp |
| Bảng lương được chốt · đã trả · mở lại | Nhân viên chủ dòng lương (kèm số thực lĩnh) |

Android: chuông 🔔 ở Trang chủ kèm badge số chưa đọc; màn Thông báo chạm vào là đánh dấu đã đọc và mở màn liên quan (Xin nghỉ / Giải trình / Bảng lương).

Mỗi thông báo trong app còn có **bản sao gửi qua email** (Gmail SMTP qua PHPMailer, `api/mail_lib.php`, dùng chung `mail_config.php` với tính năng quên mật khẩu): đơn nghỉ mới gửi cho nhóm người cần duyệt, kết quả duyệt/từ chối chỉ gửi người nộp, thông báo lương chỉ gửi đúng chủ dòng lương. Lỗi gửi mail không làm hỏng request chính; cấu hình mail còn để placeholder thì hệ thống bỏ qua im lặng.

### 6. Bảng lương

- **Tính lương tự động** từ chấm công: ngày công chuẩn = số ngày trong tháng trừ Chủ nhật; ngày công thực tế = ngày đã chấm công + ngày nghỉ phép **có lương** đã duyệt (trừ phần trùng). Lương theo công = lương cơ bản × công / chuẩn.
- **BHXH tự động**: 10,5% lương đóng bảo hiểm (`employees.insurance_salary`, để trống = lương cơ bản, trần 20 × lương cơ sở). Tháng 0 ngày công không khấu trừ; **nghỉ không hưởng lương ≥ 14 ngày làm việc trong tháng được miễn đóng BH cả tháng** theo Luật BHXH.
- **Thuế TNCN tự động**: lũy tiến 7 bậc sau giảm trừ 11 triệu + 4,4 triệu × số người phụ thuộc (`employees.dependents`). Thực lĩnh = tổng thu nhập − khấu trừ khác − BHXH − thuế. Dòng lương đã chốt giữ nguyên số, không tính lại.
- **Phụ cấp theo chính sách** (`salary_policies`): khoản cố định toàn công ty hoặc theo phòng, bật/tắt, do Admin + Kế toán trưởng quản lý; tự cộng vào phụ cấp khi tính lương tạo dòng mới.
- **Quy trình**: Kế toán soạn nháp (tính + sửa các khoản trong dialog có nhãn rõ ràng từng ô) → Kế toán trưởng chốt → đánh dấu **đã trả lương**. Admin có thể mở lại dòng đã chốt.
- **Màn hình**: danh sách các tháng trong năm (badge Đã chốt / Bản nháp / Đã trả lương) → chạm vào xem chi tiết; **bảng lương tổng hợp toàn công ty** theo tháng (mỗi nhân viên 1 dòng, nhóm theo phòng kèm tổng từng phòng, so sánh tháng trước, chạm dòng mở chi tiết); **lịch sử thay đổi** từng dòng lương (`salary_audit_logs`); **xuất Excel (CSV)** bảng lương và bảng công về thư mục Tải xuống.
- Nhân viên thường chỉ xem được lương **của chính mình** và chỉ sau khi đã chốt.

### 7. Lịch vắng & báo cáo

- **Lịch vắng**: lưới tháng tô màu ngày có người vắng (đơn đã duyệt) + danh sách chi tiết; phạm vi: Admin/HR/Kế toán xem toàn công ty, còn lại xem phòng mình.
- **Xuất bảng công tháng** (CSV): ngày có mặt, đi muộn, nghỉ phép có lương, nghỉ không lương, tổng ngày công của từng nhân viên.
- Bảng tin công ty (thông báo nội bộ) ở Trang chủ do Admin đăng (`announcements.php`).

### 8. Chấm công sinh trắc học

- **Khuôn mặt**: app dùng ML Kit phát hiện/cắt khuôn mặt, trích vector đặc trưng (embedding) 128 chiều bằng FaceNet (TFLite) ngay trên điện thoại. Server chỉ lưu vector trong bảng `employee_faces` — **không lưu ảnh gốc** — và so khớp bằng cosine, ngưỡng **0.70** (đúng người đo thực tế đạt 0.9983). Mỗi nhân viên đăng ký khuôn mặt 1 lần ở mục trên Trang chủ; chấm công vào/ra bằng khuôn mặt qua `attendances.php` kèm `method=face` và điểm tương đồng để truy vết.
- **Vân tay**: dùng `BiometricPrompt` của Android (hệ thống tự xác thực, app không đọc được dữ liệu vân tay thô), chấm công với `method=fingerprint`.
- API: `biometrics.php` (`face-enroll` / `face-status` / `face-remove`) + `biometrics_lib.php` (hàm cosine, ngưỡng khớp). Kiểm thử API sinh trắc học đạt 13/13 trường hợp.

## Cấu trúc repo

```
api/            PHP REST API
  auth.php            đăng nhập, đăng ký, đổi mật khẩu, quên MK (mã 6 số qua email)
  auth_guard.php      kiểm tra token, helper phân quyền + thông báo dùng chung
  employees.php       CRUD nhân viên, avatar, khoá/mở TK, danh bạ (directory)
  import_employees.php  import nhân viên hàng loạt (Admin)
  departments.php     phòng ban + thành viên (theo phạm vi quyền)
  attendances.php     chấm công + GPS, giải trình, báo cáo, xuất bảng công
  leaves_salaries.php đơn nghỉ + quỹ phép, lịch vắng, bảng lương, dashboard, thông báo
  salary_calc.php     công thức tính lương, BHXH, thuế TNCN
  announcements.php   bảng tin công ty
  biometrics.php      đăng ký/trạng thái/xoá khuôn mặt chấm công sinh trắc học
  biometrics_lib.php  so khớp cosine vector khuôn mặt, ngưỡng 0.70
  mail_lib.php        gửi bản sao thông báo qua email (PHPMailer + Gmail SMTP)
  db.php              kết nối PDO MySQL, múi giờ Asia/Ho_Chi_Minh dùng chung
database/       employee_management.sql (8 bảng gốc) + migrations/ (đủ 14 bảng) — xem database/README.md
android/        App Android — project HIỆN TẠI, mở thư mục này bằng Android Studio để chạy
app/            App Android — bản cũ ở gốc repo, KHÔNG còn cập nhật (chỉ giữ tham khảo)
```

## Chạy dự án trên máy mới

1. Cài Git, Android Studio, XAMPP. Clone repo.
2. **Database**: XAMPP Start Apache + MySQL → mở `http://localhost/phpmyadmin` → tạo trước DB `employee_management` (file dump **không** có lệnh CREATE DATABASE) rồi nhập file `database/employee_management.sql` (8 bảng gốc), sau đó chạy **lần lượt mọi file** trong `database/migrations/` theo thứ tự số: 001 ngày vào làm/fulltext, 002 token, 002 (file thứ hai) import hàng loạt, 003 chức vụ theo phòng, 005 bảo mật reset, 006 tính lương tự động, 007 phân quyền theo phòng, 008 hoàn thiện lương, 009 thông báo, 010 quỹ phép/GPS/giải trình, 011 chấm công sinh trắc học, 012 quy trình HR, 013 tuyển dụng, 014 onboarding, **015 seed Ban Giám đốc (BOD)** — dãy số không có 004. ⚠️ Bỏ qua migration nào là thiếu bảng/thiếu dữ liệu tương ứng; riêng **015 tạo phòng BOD + chức vụ Giám đốc + tài khoản `giamdoc`** — không chạy 015 thì hệ thống **không có tài khoản giám đốc** (tài khoản tạo tay sẽ mặc định là nhân viên). Nếu nâng cấp một DB cũ, chỉ chạy những migration **chưa** áp dụng và không import lại file dump. Chi tiết từng bảng xem `database/README.md`.
3. **API**: copy thư mục `api/` vào `htdocs/employee-api/`. File cấu hình mail/SMS **không nằm trong repo** — copy `api/mail_config.example.php` → `api/mail_config.php` (và tương tự `sms_config`) rồi điền thông tin thật ngay trên máy chạy server. Test: mở `http://localhost/employee-api/attendances.php?action=today` phải trả JSON.
4. **App**: mở thư mục **`android/`** bằng Android Studio (đây là project chạy được hiện tại; cây `app/` ở gốc repo là bản cũ, không còn cập nhật) → thêm IP máy vào file `android/local.properties` (file này đã gitignore, mỗi máy tự giữ):
   ```
   apiUrl=http://<IP-LAN-cua-may>/employee-api/
   ```
   → Sync → Run. Không sửa `ApiClient.java`.

### ⚠️ Lưu ý IP khi chạy máy ảo / điện thoại thật

App **không** kết nối được tới `localhost` của máy tính. Bắt buộc dùng **IP LAN (Wi-Fi)** và điện thoại/máy ảo phải cùng mạng với laptop.

- Lấy IP: Mac — Terminal `ipconfig getifaddr en0`; Windows — cmd `ipconfig`, xem `IPv4 Address` của Wi-Fi.
- Đổi Wi-Fi hoặc bị cấp IP mới sẽ gặp lỗi "failed to connect" → lấy lại IP, sửa `local.properties`, Run lại.

## Tài khoản demo

| Tài khoản | Mật khẩu | Vai trò |
|---|---|---|
| `admin` | `123456` | Admin (IT) |
| `tuannt03` | `123456` | Trưởng phòng IT |
| `sep_hr` | `123456` | Trưởng phòng HR |
| `nv_hr` | `123456` | Nhân viên HR |
| `ketoan_truong` | `ketoan123` | Kế toán trưởng (ACC) |
| `ketoan_vien` | `ketoan123` | Nhân viên Kế toán (ACC) |
| `quang_hr` | `nhansu123` | Trưởng phòng HR |
| `sep_sales` | `sales123` | Trưởng phòng Kinh doanh (SALES) |
| `giamdoc` | `123456` | Giám đốc — Ban Giám đốc (BOD), có quyền duyệt BOD *(cần migration 015)* |
| `thuky` | `123456` | Thư ký — Ban Giám đốc (BOD) *(cần migration 015)* |

## API chính (tham khảo nhanh)

Mọi request (trừ login/register/quên mật khẩu) cần header `Authorization: Bearer <token>`. Response: `{"success": true, "data": ...}` hoặc `{"success": false, "error": "..."}`.

| Endpoint | Chức năng |
|---|---|
| `auth.php?action=login` / `register` / `change-password` / `forgot-*` | Xác thực tài khoản |
| `employees.php?action=list/detail/create/update/delete/set-status/directory/upload-avatar` | Quản lý nhân viên |
| `departments.php?action=list/create/update/delete/members` | Phòng ban & thành viên |
| `biometrics.php?action=face-enroll/face-status/face-remove` | Chấm công sinh trắc học (khuôn mặt) |
| `attendances.php?action=today/checkin/checkout/history/report/attendance-export` | Chấm công & báo cáo |
| `attendances.php?action=explanation-create/list/approve` | Giải trình quên chấm công |
| `leaves_salaries.php?action=leave-create/list/approve/balance/calendar` | Nghỉ phép & quỹ phép |
| `leaves_salaries.php?action=salary-generate/adjust/finalize/unfinalize/paid/overview/export/history/policies` | Bảng lương |
| `leaves_salaries.php?action=notify-list/notify-read` | Thông báo |
| `leaves_salaries.php?action=dashboard` | Số liệu tổng quan theo phạm vi |

## Bảo mật

- Token đăng nhập ngẫu nhiên, server chỉ lưu **hash SHA-256**, hạn 7 ngày; đổi/đặt lại mật khẩu sẽ thu hồi các phiên đăng nhập khác.
- Mã đặt lại mật khẩu lưu dạng hash, nhập sai 5 lần bị huỷ; chế độ debug trả mã trong response đã **tắt**.
- File cấu hình chứa secret (`mail_config.php`, `sms_config.php`) **không được commit** (đã khai `.gitignore`, chỉ giữ file `.example.php` làm mẫu). *Lưu ý: các bản config cũ từng xuất hiện trong lịch sử Git trước đây — khi dùng thật phải thu hồi/đổi mới thông tin đăng nhập đó.*
- Endpoint đặt lại mật khẩu qua số điện thoại (đường tắt cũ) đã xoá; quên mật khẩu chỉ qua email.

## Ghi chú khác

- **Đã gỡ Firebase**: clone về build chạy ngay, không cần `google-services.json`.
- **Quy trình nhóm**: mỗi việc 1 nhánh (`ten-viec`) → push nhánh → Pull Request → review → merge vào `main`. Chi tiết xem `HUONG-DAN-GITHUB-NHOM.md`.
