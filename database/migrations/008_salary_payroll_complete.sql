-- 008: Hoan thien ke toan luong (BHXH + thue TNCN, da tra luong, lich su
-- thay doi, phu cap chinh sach). API tu bo sung cac cot/bang nay khi chay
-- (salary_ensure_columns / employee_salary_ensure_columns /
-- salary_ensure_audit_table / salary_ensure_policies_table trong
-- api/salary_calc.php) — file nay chi de doi chieu hoac cai moi thu cong.

ALTER TABLE salaries
  ADD COLUMN insurance_base DECIMAL(15,2) NOT NULL DEFAULT 0,
  ADD COLUMN insurance_amount DECIMAL(15,2) NOT NULL DEFAULT 0,
  ADD COLUMN tax_amount DECIMAL(15,2) NOT NULL DEFAULT 0,
  ADD COLUMN paid TINYINT NOT NULL DEFAULT 0,
  ADD COLUMN paid_at DATETIME NULL;

ALTER TABLE employees
  ADD COLUMN insurance_salary DECIMAL(15,2) NULL,
  ADD COLUMN dependents INT NOT NULL DEFAULT 0;

CREATE TABLE IF NOT EXISTS salary_audit_logs (
  id INT AUTO_INCREMENT PRIMARY KEY,
  salary_id INT NOT NULL,
  employee_id INT NOT NULL,
  actor_id INT NOT NULL,
  field VARCHAR(40) NOT NULL,
  old_value VARCHAR(60) NULL,
  new_value VARCHAR(60) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_salary (salary_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS salary_policies (
  id INT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(120) NOT NULL,
  amount DECIMAL(15,2) NOT NULL DEFAULT 0,
  department_id INT NULL,
  active TINYINT NOT NULL DEFAULT 1,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
