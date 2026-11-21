# API — employee-management (PHP + XAMPP)

REST API bằng PHP thuần (không framework), chạy trên XAMPP, kết nối MySQL `employee_management` qua PDO (file `db.php`, xem `../database/README.md`). App Android gọi API qua Retrofit; mọi request trả JSON dạng `{"success": true, "data": ...}` hoặc `{"success": false, "error": "..."}`.

## Cài đặt

1. Copy cả folder `api/` vào thư mục `htdocs/employee-api/` của XAMPP (Mac: `/Applications/XAMPP/xamppfiles/htdocs/employee-api/`; Windows: `C:\xampp\htdocs\employee-api\`).
2. File cấu hình chứa secret **không nằm trong repo** (đã gitignore): copy `mail_config.example.php` → `mail_config.php` và `sms_config.example.php` → `sms_config.php`, rồi điền thông tin thật ngay trên máy chạy server.
3. XAMPP > Start **Apache** + **MySQL**.
4. Test: mở `http://localhost/employee-api/attendances.php?action=today` — có header đăng nhập hợp lệ sẽ trả JSON dữ liệu, chưa đăng nhập sẽ trả lỗi xác thực dạng JSON.

## Xác thực & phân quyền

- Trừ `login` / `register` / các action quên mật khẩu, mọi request phải có header `Authorization: Bearer <token>`.
- Token do server cấp khi đăng nhập, hạn 7 ngày; server chỉ lưu **hash SHA-256** của token trong bảng `api_tokens`.
- Phân quyền do **server** quyết định từ token (vai trò × phòng ban), client không tự khai danh tính. Kiểm tra tập trung ở `auth_guard.php`.
- Toàn bộ giờ giấc theo múi giờ `Asia/Ho_Chi_Minh`, đồng bộ ở cả PHP và phiên MySQL.

## Các file endpoint

| File | Action (`?action=...`) | Chức năng |
|------|------------------------|-----------|
| `auth.php` | `login`, `logout`, `register`, `change-password`, `forgot-password`, `verify-code`, `reset-password` | Đăng nhập/đăng ký tài khoản; quên mật khẩu bằng mã 6 số gửi qua email (mã lưu dạng hash, sai 5 lần bị huỷ) |
| `employees.php` | `list`, `detail`, `create`, `update`, `delete`, `set-status`, `directory`, `upload-avatar`, `departments`, `positions` | Quản lý nhân viên theo phạm vi phòng; `directory` là danh bạ rút gọn phục vụ màn lương/duyệt đơn; `set-status` để khoá/mở tài khoản |
| `departments.php` | `list`, `create`, `update`, `delete`, `members` | Phòng ban và danh sách thành viên theo phạm vi quyền |
| `attendances.php` | `today`, `checkin`, `checkout`, `history`, `report`, `attendance-export`, `explanation-create`, `explanation-list`, `explanation-approve` | Chấm công vào/ra (kèm GPS, gắn cờ ngoài văn phòng, không chặn), báo cáo và xuất bảng công CSV, đơn giải trình quên chấm công |
| `leaves_salaries.php` | `dashboard`, `leave-create`, `leave-list`, `leave-approve`, `leave-balance`, `leave-calendar`, `salary-generate`, `salary-list`, `salary-adjust`, `salary-finalize`, `salary-unfinalize`, `salary-paid`, `salary-overview`, `salary-export`, `salary-history`, `salary-policies`, `salary-policy-save`, `salary-policy-delete`, `notify-list`, `notify-read` | Tổng quan, đơn nghỉ & quỹ phép 12 ngày/năm, lịch vắng, bảng lương (tính tự động, BHXH, thuế TNCN, chốt/đã trả, lịch sử, chính sách phụ cấp), thông báo trong app |
| `biometrics.php` | `face-enroll`, `face-status`, `face-remove` | Đăng ký/trạng thái/xoá khuôn mặt chấm công; việc chấm công bằng khuôn mặt/vân tay đi qua `checkin`/`checkout` của `attendances.php` với tham số `method` |
| `announcements.php` | `list`, `create` | Bảng tin công ty ở Trang chủ (Admin đăng) |
| `import_employees.php` | — (POST trực tiếp, chỉ Admin) | Import nhân viên hàng loạt |

## Các file dùng chung (không gọi trực tiếp)

- `db.php` — kết nối PDO, helper đọc body JSON / trả response, xử lý lỗi thống nhất.
- `auth_guard.php` — kiểm tra token, helper phân quyền theo phòng và thông báo dùng chung.
- `salary_calc.php` — công thức tính lương, BHXH 10,5%, thuế TNCN lũy tiến 7 bậc.
- `positions_logic.php` — logic chức vụ gắn phòng ban, tự seed phòng/chức vụ khi thiếu.
- `employee_validation.php` — kiểm tra dữ liệu nhân viên khi tạo/sửa.
- `biometrics_lib.php` — so khớp vector khuôn mặt bằng cosine, ngưỡng 0.70; server chỉ lưu embedding 128 chiều, không lưu ảnh.
- `mail_lib.php` + `lib/PHPMailer/` — gửi bản sao thông báo qua email (Gmail SMTP): đơn nghỉ, kết quả duyệt đơn, chốt/trả lương. Lỗi gửi mail không làm hỏng request chính.

## Quy tắc nghiệp vụ đáng chú ý ở phía server

- Quản lý **không tự duyệt** được đơn nghỉ/giải trình của chính mình — API trả **403**; đơn của quản lý do Admin hoặc quản lý khác cùng phòng duyệt.
- Tạo đơn phép năm vượt quỹ phép còn lại bị từ chối **422**.
- Dòng lương đã chốt giữ nguyên số, không tính lại; mở lại (`salary-unfinalize`) chỉ Admin.
- Chấm công xa văn phòng (mốc ĐH Sư phạm Hà Nội, bán kính 500m trong `attendances.php`) chỉ gắn cờ `remote`, không chặn.

## Test nhanh

- Đăng nhập: `POST auth.php?action=login {"username":"admin","password":"123456"}` → nhận `token` trong `data`.
- Gọi thử kèm token: `GET leaves_salaries.php?action=dashboard` với header `Authorization: Bearer <token>`.
- Android Emulator gọi về máy host qua `http://10.0.2.2/employee-api/`; điện thoại thật phải dùng IP LAN của máy chạy XAMPP và chung Wi-Fi (xem README ở gốc repo).
