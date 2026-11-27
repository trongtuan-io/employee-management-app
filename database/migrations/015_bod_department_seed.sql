-- 015_bod_department_seed.sql
-- Seed phong Ban Giam doc (BOD) + 3 chuc vu + 2 tai khoan giamdoc/thuky.
-- Truoc day du lieu nay chi duoc them thang vao DB may Tuan (25/11/2026) bang script mot lan,
-- chua he co trong repo -> cai moi tu main se khong co phong BOD va tai khoan giam doc,
-- tai khoan tao tay se roi ve role mac dinh (staff/NULL) nen app hien "nhan vien".
-- Chay sau migration 014. Idempotent: chay lai nhieu lan khong tao trung.
-- Mat khau ca 2 tai khoan: 123456 (hash bcrypt lay tu dump nhanh vu_ngoc_BOD, da kiem chung password_verify).

-- 1) Phong BOD
INSERT INTO departments (name, description)
SELECT 'BOD', 'Ban Giam doc' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM departments WHERE name = 'BOD');
SET @bod_id = (SELECT id FROM departments WHERE name = 'BOD' LIMIT 1);

-- 2) Ba chuc vu thuoc phong BOD (is_director yeu cau chuc vu thuoc dung phong + is_active = 1)
INSERT INTO positions (title, department_id, is_active)
SELECT 'Giám đốc', @bod_id, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM positions WHERE title = 'Giám đốc');
INSERT INTO positions (title, department_id, is_active)
SELECT 'Phó Giám đốc', @bod_id, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM positions WHERE title = 'Phó Giám đốc');
INSERT INTO positions (title, department_id, is_active)
SELECT 'Thư ký', @bod_id, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM positions WHERE title = 'Thư ký');
SET @pos_gd = (SELECT id FROM positions WHERE title = 'Giám đốc' LIMIT 1);
SET @pos_tk = (SELECT id FROM positions WHERE title = 'Thư ký' LIMIT 1);

-- 3) Tai khoan giamdoc: role_id = 2 (manager) + phong BOD + chuc vu Giam doc
--    -> du ca 3 dieu kien is_director() nen login tra can_approve_bod = true.
INSERT INTO employees (username, password_hash, full_name, email, base_salary, status, role_id, department_id, position_id, employment_status)
SELECT 'giamdoc', '$2y$10$tvS4Cpiv6TOsVgMKzz.rV.N6cpYuCh6rXuB7JRhqxNWefzdTAZUk.', 'Giam Doc', 'giamdoc@gmail.com', 0, 1, 2, @bod_id, @pos_gd, 'working' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM employees WHERE username = 'giamdoc');

-- 4) Tai khoan thuky: role_id = 3 (staff) + phong BOD + chuc vu Thu ky
INSERT INTO employees (username, password_hash, full_name, email, base_salary, status, role_id, department_id, position_id, employment_status)
SELECT 'thuky', '$2y$10$tvS4Cpiv6TOsVgMKzz.rV.N6cpYuCh6rXuB7JRhqxNWefzdTAZUk.', 'Thu Ky', 'thuky@gmail.com', 0, 1, 3, @bod_id, @pos_tk, 'working' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM employees WHERE username = 'thuky');
