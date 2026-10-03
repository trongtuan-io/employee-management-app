# Employee Management App

Phần mềm nội bộ quản lý nhân viên — Đồ án môn Thiết kế phần mềm di động (nhóm 4 người).

Kiến trúc: **App Android (Java + Retrofit) → REST API (PHP + XAMPP) → MySQL**.
App không nối trực tiếp vào DB. Mọi dữ liệu đi qua API dưới dạng JSON.

## Tính năng theo vai trò

| Vai trò | Màn hình | Chức năng |
|---|---|---|
| Admin | Trang quản trị | Xem tổng quan (NV đang làm, đơn chờ duyệt, đi muộn), quản lý nhân viên, sửa mọi thông tin, khóa/mở TK mọi phòng ban |
| Sếp (manager) | Trang quản trị | Như admin nhưng chỉ khóa/mở NV thường cùng phòng mình |
| Nhân viên (staff) | Trang nhân viên | Xem hồ sơ, chấm công vào/ra, xin nghỉ phép, đổi mật khẩu |
| Chung | Đăng nhập, Quên mật khẩu | Login phân quyền; quên MK bằng mã 6 số qua Email hoặc SMS (Firebase, miễn phí) |

Quyền khóa/mở và khóa sửa mail/SĐT được chặn ở **server**, app không qua mặt được.

## Cấu trúc repo

```
database/  SQL full 8 bảng (roles, departments, positions, employees,
           attendances, leave_requests, salaries, password_resets)
api/       PHP REST API: auth.php (login, đổi/quên MK),
           employees.php (CRUD, upload avatar, khóa/mở),
           attendances.php (chấm công), leaves_salaries.php (phép, lương, dashboard)
android/   App Android Studio (Java + Retrofit + Glide + Firebase Auth)
```

## Chạy dự án trên máy mới

1. Cài Git, Android Studio, XAMPP. Clone repo.
2. **DB:** XAMPP Start Apache + MySQL → `http://localhost/phpmyadmin` → Nhập file `database/employee_management.sql`.
3. **API:** copy folder `api/` vào `htdocs/employee-api/` → test `http://localhost/employee-api/leaves_salaries.php?action=dashboard` phải ra JSON.
4. **App:** mở folder `android/` bằng Android Studio → bỏ `google-services.json` (Firebase console) vào `android/app/` → sửa `BASE_URL` trong `ApiClient.java` thành IP máy mình (`ipconfig getifaddr en0`; máy ảo dùng `10.0.2.2`) → Run.

Tài khoản demo (pass `123456`): `admin` (admin/IT), `sep_hr` (sếp/HR), `nv_hr` (NV/HR), `tuannt03` (NV/IT).

## Quy trình nhóm

Mỗi việc 1 nhánh (`ten-viec`) → push nhánh → Pull Request → review → merge vào `main`. Không push thẳng `main`. Chi tiết xem `HUONG-DAN-GITHUB-NHOM.md`.
