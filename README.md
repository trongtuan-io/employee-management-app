# Employee Management App

Phần mềm nội bộ quản lý nhân viên — Đồ án môn Thiết kế phần mềm di động (nhóm 4 người).

Kiến trúc: **App Android (Java + Retrofit) → REST API (PHP + XAMPP) → MySQL**.
App không nối trực tiếp vào DB. Mọi dữ liệu đi qua API dưới dạng JSON.

## Tính năng theo vai trò

| Vai trò | Màn hình | Chức năng |
|---|---|---|
| Admin | Trang quản trị | Xem tổng quan (NV đang làm, đơn chờ duyệt, đi muộn), quản lý nhân viên, tạo tài khoản NV, sửa mọi thông tin, khóa/mở TK mọi phòng ban |
| Sếp (manager) | Trang quản trị | Như admin nhưng chỉ khóa/mở NV thường cùng phòng mình |
| Nhân viên (staff) | Trang nhân viên | Xem hồ sơ, chấm công vào/ra, xin nghỉ phép, đổi mật khẩu |
| Chung | Đăng nhập, Quên mật khẩu | Login phân quyền; quên MK bằng mã 6 số qua Email |

Quyền khóa/mở và khóa sửa mail/SĐT được chặn ở **server**, app không qua mặt được.

## Cấu trúc repo

```
database/  SQL full 8 bảng (roles, departments, positions, employees,
           attendances, leave_requests, salaries, password_resets)
api/       PHP REST API: auth.php (login, register, đổi/quên MK),
           employees.php (CRUD, upload avatar, khóa/mở),
           attendances.php (chấm công), leaves_salaries.php (phép, lương, dashboard)
app/       App Android Studio (Java + Retrofit + Glide) — bản chính, mở folder gốc repo để chạy
android/   Bản copy của nhóm (IP cứng trong code, không dùng local.properties)
```

## Chạy dự án trên máy mới

1. Cài Git, Android Studio, XAMPP. Clone repo.
2. **DB:** XAMPP Start Apache + MySQL → `http://localhost/phpmyadmin` → Nhập file `database/employee_management.sql`.
3. **API:** copy folder `api/` vào `htdocs/employee-api/` → test `http://localhost/employee-api/leaves_salaries.php?action=dashboard` phải ra JSON.
4. **App:** mở **folder gốc repo** bằng Android Studio (module `:app`) → thêm IP máy mình vào `local.properties`: `apiUrl=http://<IP-cua-ban>/employee-api/` (`ipconfig getifaddr en0`; máy ảo dùng `10.0.2.2`) → Sync → Run. Không sửa code `ApiClient.java`.

Tài khoản demo (pass `123456`): `admin` (admin/IT), `sep_hr` (sếp/HR), `nv_hr` (NV/HR), `tuannt03` (NV/IT).

## Quy trình nhóm

Mỗi việc 1 nhánh (`ten-viec`) → push nhánh → Pull Request → review → merge vào `main`. Không push thẳng `main`. Chi tiết xem `HUONG-DAN-GITHUB-NHOM.md`.

## ⚠️ Lưu ý khi chạy trên máy ảo (Emulator)

Máy ảo Android **KHÔNG** kết nối được tới `localhost` hay `127.0.0.1` của máy tính. Bắt buộc dùng **IP LAN (Wi-Fi)** thực tế.

Khi Wi-Fi đổi mạng hoặc cấp lại IP sẽ lỗi **"failed to connect" / "Lỗi kết nối"** → lấy lại IP và cập nhật app.

**B1 - Lấy IP (Mac):** mở Terminal, chạy `ipconfig getifaddr en0` → ghi dãy `192.168.x.x`. (Windows: mở cmd, gõ `ipconfig`, tìm `IPv4 Address` mục Wi-Fi). Điện thoại phải bắt **cùng Wi-Fi** với laptop.

**B2 - Cập nhật app:** thêm/sửa 1 dòng trong `local.properties` (file này gitignore, mỗi máy tự giữ), ví dụ `apiUrl=http://192.168.11.3/employee-api/` → Sync Gradle.

**B3 - Run lại** app trong Android Studio.

## Ghi chú: đã gỡ Firebase

Quên mật khẩu chỉ dùng Email (server gửi mã 6 số). Kênh SMS Firebase đã gỡ nên clone về build chạy ngay, không cần `google-services.json`.
