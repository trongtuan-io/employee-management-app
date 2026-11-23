-- 007: Phong ban nghiep vu + phan quyen theo phong (ThietKe_PhanQuyen_TheoPhong)
-- - Them phong: ACC (Ke toan - Tai chinh), SALES, MKT, OPS, CS.
-- - Them chuc vu gan voi tung phong (positions.department_id, theo mau migration 003).
-- - Them tai khoan mau cho cac vai moi. Mat khau demo (chi dung cho do an):
--     ketoan_truong / ketoan123  (Ke toan truong - manager, phong ACC)
--     ketoan_vien   / ketoan123  (Ke toan vien - staff, phong ACC)
--     quang_hr      / nhansu123  (Truong phong Nhan su - manager, phong HR)
--     sep_sales     / sales123   (Truong phong Kinh doanh - manager, phong SALES)
-- API cung tu seed phong/chuc vu qua positions_ensure() nen chua chay file nay
-- app van khong sup; file nay dung de cai moi doi chieu va tao tai khoan mau.

-- Phu thuoc migration 003 (cot positions.department_id); tu bo sung neu chua co.
ALTER TABLE positions ADD COLUMN IF NOT EXISTS department_id INT NULL DEFAULT NULL;
ALTER TABLE positions ADD INDEX IF NOT EXISTS idx_positions_dept (department_id);

INSERT IGNORE INTO departments(name, description) VALUES
  ('ACC', 'Ke toan - Tai chinh'),
  ('SALES', 'Kinh doanh'),
  ('MKT', 'Marketing'),
  ('OPS', 'Hanh chinh - Van hanh'),
  ('CS', 'Cham soc khach hang');

INSERT IGNORE INTO positions(title, department_id) VALUES
  ('Trưởng phòng IT', (SELECT id FROM departments WHERE name = 'IT')),
  ('Trưởng phòng HR', (SELECT id FROM departments WHERE name = 'HR')),
  ('Kế toán trưởng', (SELECT id FROM departments WHERE name = 'ACC')),
  ('Kế toán viên', (SELECT id FROM departments WHERE name = 'ACC')),
  ('Kế toán lương', (SELECT id FROM departments WHERE name = 'ACC')),
  ('Trưởng phòng Kinh doanh', (SELECT id FROM departments WHERE name = 'SALES')),
  ('Nhân viên kinh doanh', (SELECT id FROM departments WHERE name = 'SALES')),
  ('Trưởng phòng Marketing', (SELECT id FROM departments WHERE name = 'MKT')),
  ('Nhân viên Marketing', (SELECT id FROM departments WHERE name = 'MKT')),
  ('Trưởng phòng Hành chính', (SELECT id FROM departments WHERE name = 'OPS')),
  ('Nhân viên Hành chính', (SELECT id FROM departments WHERE name = 'OPS')),
  ('Trưởng nhóm CSKH', (SELECT id FROM departments WHERE name = 'CS')),
  ('Nhân viên CSKH', (SELECT id FROM departments WHERE name = 'CS')),
  ('Nhân viên tuyển dụng', (SELECT id FROM departments WHERE name = 'HR'));

-- roles: 1=admin, 2=manager, 3=staff. status=1 (dang hoat dong).
INSERT IGNORE INTO employees(username, password_hash, full_name, phone, email, base_salary, status, role_id, department_id, position_id) VALUES
  ('ketoan_truong', '$2y$10$JMOovw0l95oYr4G6GWfZUe/p43fTaf.4zKYw57DlV5ye2.t4K4cDy', 'Ke Toan Truong', '0903333333', 'ketoantruong@cty.vn', 18000000, 1, 2,
    (SELECT id FROM departments WHERE name = 'ACC'),
    (SELECT id FROM positions WHERE title = 'Kế toán trưởng')),
  ('ketoan_vien', '$2y$10$JMOovw0l95oYr4G6GWfZUe/p43fTaf.4zKYw57DlV5ye2.t4K4cDy', 'Ke Toan Vien', '0904444444', 'ketoanvien@cty.vn', 12000000, 1, 3,
    (SELECT id FROM departments WHERE name = 'ACC'),
    (SELECT id FROM positions WHERE title = 'Kế toán viên')),
  ('quang_hr', '$2y$10$O7TwuGEmOSRL5R1iz7BopuBBCZVNSNil1BTxwI6SHutvVvoFy6zQy', 'Quang - Truong Phong Nhan Su', '0905555555', 'quanghr@cty.vn', 17000000, 1, 2,
    (SELECT id FROM departments WHERE name = 'HR'),
    (SELECT id FROM positions WHERE title = 'Trưởng phòng HR')),
  ('sep_sales', '$2y$10$kNZ/8qpKk5eJVBD3S9fK/.tAPXaQhDkaO0ibo2ELr0P3QdDkO1R6O', 'Truong Phong Kinh Doanh', '0906666666', 'sepsales@cty.vn', 17000000, 1, 2,
    (SELECT id FROM departments WHERE name = 'SALES'),
    (SELECT id FROM positions WHERE title = 'Trưởng phòng Kinh doanh'));
