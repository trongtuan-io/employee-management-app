-- 006: Cot phuc vu tinh luong tu dong theo ngay cong (api/salary_calc.php).
-- work_days: ngay cong tinh luong cua thang (check-in + nghi phep da duyet).
-- standard_days: ngay cong chuan cua thang (so ngay tru Chu nhat, hoac gia tri ghi de khi tinh).
-- finalized: 0 = ban nhap (con sua/tinh lai duoc), 1 = da chot (khoa chinh sua).
-- API tu bo sung cac cot nay neu chua chay migration (salary_ensure_columns),
-- file nay danh cho moi truong cai dat moi / doi chieu schema.
ALTER TABLE salaries
  ADD COLUMN work_days DECIMAL(5,2) NOT NULL DEFAULT 0,
  ADD COLUMN standard_days INT NOT NULL DEFAULT 0,
  ADD COLUMN finalized TINYINT NOT NULL DEFAULT 0;
