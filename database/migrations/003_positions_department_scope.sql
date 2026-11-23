-- 003: Chuc vu gan voi phong ban + phan quyen theo phong (API tu ap dung qua
-- api/positions_logic.php -> positions_ensure(); file nay de chay tay neu muon).

ALTER TABLE positions ADD COLUMN department_id INT NULL DEFAULT NULL;
ALTER TABLE positions ADD INDEX idx_positions_dept (department_id);

-- Gan phong cho chuc vu seed san co
UPDATE positions SET department_id = (SELECT id FROM departments WHERE name = 'IT')
  WHERE title IN ('Developer', 'Tester') AND department_id IS NULL;
UPDATE positions SET department_id = (SELECT id FROM departments WHERE name = 'HR')
  WHERE title = 'HR Staff' AND department_id IS NULL;

-- Chuc vu chuan bo sung (department_id NULL = dung chung moi phong)
INSERT IGNORE INTO positions(title, department_id) VALUES
  ('Trưởng phòng IT', (SELECT id FROM departments WHERE name = 'IT')),
  ('Trưởng phòng HR', (SELECT id FROM departments WHERE name = 'HR')),
  ('Thực tập sinh', NULL),
  ('Quản trị hệ thống', NULL);

-- Backfill tai khoan mau (chi khi chua co chuc vu)
UPDATE employees SET position_id = (SELECT id FROM positions WHERE title = 'Quản trị hệ thống')
  WHERE username = 'admin' AND position_id IS NULL;
UPDATE employees SET position_id = (SELECT id FROM positions WHERE title = 'Developer')
  WHERE username = 'tuannt03' AND position_id IS NULL;
UPDATE employees SET position_id = (SELECT id FROM positions WHERE title = 'Trưởng phòng HR')
  WHERE username = 'sep_hr' AND position_id IS NULL;
UPDATE employees SET position_id = (SELECT id FROM positions WHERE title = 'HR Staff')
  WHERE username = 'nv_hr' AND position_id IS NULL;
