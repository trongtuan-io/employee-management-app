# Employee Management App

Phần mềm nội bộ quản lý nhân viên — Đồ án môn Thiết kế phần mềm di động (nhóm 4 người).

Kiến trúc: **App Android (Java + Retrofit) → REST API (PHP + XAMPP) → MySQL**.
App không nối trực tiếp vào DB. Mọi dữ liệu đi qua API dưới dạng JSON.

## Tính năng theo vai trò

| Vai trò | Màn hình | Chức năng |
|---|---|---|
| Admin | Trang quản trị | Xem tổng quan (NV đang làm, đơn chờ duyệt, đi muộn), quản lý nhân viên, tạo tài khoản NV, sửa mọi thông tin, khóa/mở TK mọi phòng ban, mở lại bảng lương đã chốt |
| Sếp (manager) | Trang quản trị | Quản lý NV thường cùng phòng mình, duyệt đơn nghỉ của phòng mình |
| Nhân viên (staff) | Trang nhân viên | Xem hồ sơ, chấm công vào/ra, xin nghỉ phép, xem lương của mình (tháng đã chốt), đổi mật khẩu |
| Chung | Đăng nhập, Quên mật khẩu | Login phân quyền; quên MK bằng mã 6 số qua Email |

**Phân quyền theo phòng ban (từ 09/11/2026)** — quyền = Vai trò × Phòng, server quyết định từ token:

| Phòng | Chức năng riêng |
|---|---|
| Nhân sự (HR) | Trưởng phòng HR: quản lý hồ sơ nhân viên **phòng mình**, đặt/đổi lương cơ bản cho nhân viên phòng mình. Tạo NV cho phòng khác và import hàng loạt do Admin phụ trách |
| Kế toán (ACC) | **Kế toán trưởng**: tính lương tự động + điều chỉnh + chốt cho **toàn công ty**, xem báo cáo chấm công để đối chiếu. NV Kế toán chỉ xem bảng lương của chính mình như mọi nhân viên |
| IT / Kinh doanh (SALES) / Marketing (MKT) / Hành chính (OPS) / CSKH (CS) | Trưởng phòng chỉ quản lý đội của phòng mình; không xem/tính lương của người khác |

Trang Quản lý nhân viên: **chỉ Admin xem được toàn công ty**; mọi trưởng phòng chỉ thấy nhân viên phòng mình; nhân viên (staff) không truy cập được trang này. Màn Bảng lương: danh sách các tháng trong năm, chạm vào một tháng để xem chi tiết (lương cơ bản gốc, ngày công, các khoản cộng/trừ, thực lĩnh). Nhân viên **chỉ xem được bảng lương của chính mình**; riêng Admin và Kế toán trưởng có khu quản lý (chọn nhân viên qua danh bạ riêng `employees.php?action=directory`, tính lương, sửa, chốt toàn công ty).

Duyệt nghỉ vẫn theo phòng của người xin nghỉ (Kế toán trưởng duyệt đơn của NV Kế toán, không duyệt đơn phòng khác). Nhân viên thường chỉ thấy bảng lương của mình **sau khi đã chốt**. Chi tiết: bản thiết kế phân quyền lưu trong hồ sơ đồ án của nhóm.

## Cấu trúc repo

```
database/  SQL full 8 bảng (roles, departments, positions, employees,
           attendances, leave_requests, salaries, password_resets)
api/       PHP REST API: auth.php (login, register, đổi/quên MK),
           employees.php (CRUD, upload avatar, khóa/mở),
           attendances.php (chấm công), leaves_salaries.php (phép, lương, dashboard)
app/       App Android Studio (Java + Retrofit + Glide) — bản chính, mở folder gốc repo để chạy
android/   Bản copy thứ hai của app — hai cây app/ và android/ được giữ ĐỒNG BỘ mọi thay đổi; máy demo của nhóm mở project ở thư mục android/
```

## Chạy dự án trên máy mới

1. Cài Git, Android Studio, XAMPP. Clone repo.
2. **DB:** XAMPP Start Apache + MySQL → `http://localhost/phpmyadmin` → Nhập file `database/employee_management.sql` (bản hiện tại đã gồm cột `hire_date`, bảng `api_tokens` tự tạo ở lần đăng nhập đầu tiên, và bảng `password_resets` đã nới cột mã + cột đếm lần nhập sai). Các file trong `database/migrations/` (001–005) CHỈ dùng khi nâng cấp một DB cũ đã tạo từ bản dump trước đây — chạy theo thứ tự và bỏ qua file nào đã áp dụng.
3. **API:** copy folder `api/` vào `htdocs/employee-api/` → test `http://localhost/employee-api/leaves_salaries.php?action=dashboard` phải ra JSON.
4. **App:** mở **folder gốc repo** bằng Android Studio (module `:app`) → thêm IP máy mình vào `local.properties`: `apiUrl=http://<IP-cua-ban>/employee-api/` (`ipconfig getifaddr en0`; máy ảo dùng `10.0.2.2`) → Sync → Run. Không sửa code `ApiClient.java`.

Tài khoản demo (pass `123456`): `admin` (admin/IT), `sep_hr` (sếp/HR), `nv_hr` (NV/HR), `tuannt03` (NV/IT).
Tài khoản demo theo phòng mới (migration 007): `ketoan_truong` / `ketoan123` (Kế toán trưởng), `ketoan_vien` / `ketoan123` (NV Kế toán), `quang_hr` / `nhansu123` (Trưởng phòng HR), `sep_sales` / `sales123` (Trưởng phòng Kinh doanh). DB cũ nâng cấp: chạy thêm `database/migrations/006_salary_auto_calc.sql` và `007_departments_payroll_scope.sql`.

## Bảo mật (đã sửa 07/11/2026, bổ sung 08/11/2026)

- Cập nhật 08/11/2026 (sau rà soát toàn diện + bộ test API tự động 93 trường hợp): admin không còn tự xoá được tài khoản của chính mình; tạo bảng lương chặn số âm và sếp phòng chỉ tạo lương cho nhân viên phòng mình; mã đặt lại mật khẩu lưu dạng hash sha256, nhập sai 5 lần bị huỷ, đặt lại/đổi mật khẩu xong sẽ thu hồi các phiên đăng nhập khác; đăng ký tài khoản bắt mật khẩu ≥ 6 ký tự và lương cơ bản hợp lệ; sửa file dump để tài khoản `admin` đăng nhập được bằng mật khẩu demo như tài liệu ghi.

- Đăng nhập cấp **token ngẫu nhiên thật** (server lưu hash trong bảng `api_tokens`, hạn 7 ngày); phân quyền lấy danh tính từ token — client không tự khai `requester_id` được nữa. Xem `api/auth_guard.php`.
- Tạo/xoá nhân viên, duyệt nghỉ phép: bắt buộc token admin/manager. (Chức năng tạo bảng lương đã gỡ bỏ ngày 08/11/2026 theo yêu cầu — bảng lương chỉ còn để xem.)
- Quên mật khẩu: chế độ debug trả mã trong response đã **tắt** (`DEBUG_SHOW_CODE` / `SMS_DEBUG_SHOW_CODE` = false). Muốn gửi mail thật phải điền SMTP thật trong `api/mail_config.php`; token SMS cũ trong `api/sms_config.php` đã bị lộ trên GitHub — phải thu hồi và thay token mới.
- Endpoint `reset-password-by-phone` (đường tắt qua Firebase cũ) đã **xoá**.

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
