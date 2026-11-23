# Database — employee_management

Cơ sở dữ liệu MySQL/MariaDB cho đồ án **Quản lý nhân viên**: app Android (Java + Retrofit) gọi REST API bằng PHP (thư mục `api/`, chạy trên XAMPP), API kết nối MySQL qua PDO — Android không kết nối trực tiếp tới DB.

- File gốc: `employee_management.sql` (export từ XAMPP/MariaDB 10.4.28 ngày 2026-10-02) — gồm **8 bảng gốc**.
- Thư mục `migrations/`: các file nâng cấp chạy thêm sau khi import file gốc, bổ sung **6 bảng mới** và nhiều cột mới (phân quyền theo phòng, tính lương, thông báo, GPS, sinh trắc học).
- **Tổng cộng: 14 bảng.** Lược đồ CSDL bản vẽ nằm ở thư mục `docs/` ở gốc repo.

## Danh sách 14 bảng

| # | Bảng | Nội dung |
|---|------|----------|
| 1 | `roles` | Vai trò tài khoản: admin / manager / staff |
| 2 | `departments` | Phòng ban (7 phòng: IT, HR, ACC, SALES, MKT, OPS, CS) |
| 3 | `positions` | Chức vụ, mỗi chức vụ gắn với một phòng ban (`department_id`, migration 003/007) |
| 4 | `employees` | Nhân viên và tài khoản đăng nhập (thêm cột `hire_date`, chỉ mục fulltext ở migration 001) |
| 5 | `attendances` | Chấm công: giờ vào/ra, tọa độ GPS và cờ chấm công từ xa (010), phương thức chấm công `gps`/`face`/`fingerprint` và điểm tương đồng khuôn mặt (011) |
| 6 | `leave_requests` | Đơn xin nghỉ (thêm loại nghỉ `leave_type`: phép năm / ốm / việc riêng / không lương, migration 010) |
| 7 | `salaries` | Bảng lương theo tháng: ngày công, chốt lương (006); BHXH, thuế TNCN, phụ cấp, đã trả lương (008) |
| 8 | `password_resets` | Mã đặt lại mật khẩu — chỉ lưu mã đã băm (hash) và số lần nhập sai `attempts` (005) |
| 9 | `api_tokens` | Token đăng nhập của app — server chỉ lưu hash SHA-256 của token, không lưu token gốc (002) |
| 10 | `salary_audit_logs` | Lịch sử thay đổi trên bảng lương: ai sửa, sửa khoản nào, giá trị cũ/mới (008) |
| 11 | `salary_policies` | Chính sách phụ cấp áp dụng toàn công ty hoặc theo phòng ban (008) |
| 12 | `notifications` | Thông báo trong app cho từng nhân viên (009); các sự kiện quan trọng còn có bản sao gửi qua email |
| 13 | `attendance_explanations` | Đơn giải trình quên chấm công / đi muộn, chờ quản lý duyệt (010) |
| 14 | `employee_faces` | Vector đặc trưng khuôn mặt (embedding 128 chiều, FaceNet) để chấm công sinh trắc học — chỉ lưu vector số, **không lưu ảnh gốc**; so khớp bằng cosine, ngưỡng 0.70 (011) |

## Cách import (cài mới từ đầu)

1. Mở XAMPP > Start **Apache** + **MySQL** > vào `http://localhost/phpmyadmin`.
2. Tạo trước database tên `employee_management` (collation `utf8mb4_unicode_ci`).
   ⚠️ File `employee_management.sql` **không** có lệnh `CREATE DATABASE` — không tạo DB trước sẽ import lỗi.
3. Chọn DB vừa tạo > tab `Nhập` > chọn `employee_management.sql` > `Go`. Sau bước này DB có 8 bảng gốc.
4. Chạy lần lượt các file trong `migrations/` theo đúng thứ tự số (001 → 011; dãy số không có 004) — vẫn trong tab `Nhập` của phpMyAdmin, hoặc chạy bằng dòng lệnh MySQL. Xong bước này DB đủ 14 bảng.
5. Với bản tích hợp HR V5 và BOD, áp dụng tiếp 012 và 013 theo thứ tự trên schema TEST đã cách ly trước khi chạy API tích hợp.

Lưu ý khi nâng cấp DB đang dùng: **không import lại** `employee_management.sql` (file có `DROP TABLE`, sẽ mất dữ liệu hiện tại) — chỉ chạy những migration còn thiếu.

Một số bảng/cột do chính API tự tạo khi chạy lần đầu (qua các hàm `*_ensure_*` trong `api/`), nên thiếu một vài migration thì app vẫn chạy; bộ migration ở đây dùng để cài mới đối chiếu và khôi phục thủ công.

## Kết nối từ Backend (PHP API)

- File kết nối: `api/db.php` — PDO tới MySQL: host `localhost`, user `root`, pass trống, db `employee_management`, charset `utf8mb4`.
- Múi giờ được đồng bộ Việt Nam ở cả PHP (`Asia/Ho_Chi_Minh`) và phiên MySQL (`+07:00`) để giờ chấm công không bị lệch.
- App Android chỉ gọi API qua Retrofit, không nhúng thông tin DB.
- Hệ thống chạy cục bộ (offline) trên XAMPP qua mạng LAN: điện thoại và máy chạy XAMPP phải chung một Wi-Fi.
