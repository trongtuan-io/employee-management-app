# API - employee-management (PHP + XAMPP)

Chạy trên XAMPP, kết nối MySQL `employee_management` (xem `../database/`).

## Cài đặt
1. Copy cả folder `api/` vào `/Applications/XAMPP/xamppfiles/htdocs/employee-api/`
2. XAMPP Manager > Start Apache + MySQL
3. Test: `http://localhost/employee-api/leaves_salaries.php?action=dashboard`

## Chia việc 4 người
- `auth.php` (Người 1): `?action=login | register | change-password`
- `employees.php` (Người 2): `?action=list | detail | create | update | delete | departments | positions`
- `attendances.php` (Người 3): `?action=checkin | checkout | history | report`
- `leaves_salaries.php` (Người 4): `?action=leave-create | leave-list | leave-approve | salary-list | salary-create | dashboard`

## Test nhanh (đã verify 2026-10-02)
- Đăng ký: `POST auth.php?action=register {"username":"admin","password":"123456","full_name":"Admin","email":"admin@cty.vn"}`
- Login: `POST auth.php?action=login {"username":"admin","password":"123456"}`
- Android Emulator: `BASE_URL = "http://10.0.2.2/employee-api/"`
