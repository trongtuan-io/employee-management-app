# Database - employee_management

- File: `employee_management.sql` (export từ XAMPP/MariaDB 10.4.28 ngày 2026-10-02)
- Gồm 7 bảng: `roles`, `departments`, `positions`, `employees`, `attendances`, `leave_requests`, `salaries`

## Cách import
1. Mở XAMPP > Start MySQL + Apache > vào `http://localhost/phpmyadmin`
2. Tạo DB `employee_management` (utf8mb4_unicode_ci) hoặc import trực tiếp (file đã có CREATE DATABASE).
3. Tab `Nhập` > chọn `employee_management.sql` > `Go`.

## Kết nối từ Backend (không dùng JDBC từ Android)
- Backend REST (Node/Spring) kết nối MySQL: host `localhost`, user `root`, pass trống, db `employee_management`.
- App Android chỉ gọi API qua Retrofit, không nhúng thông tin DB.
