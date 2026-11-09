-- 010: Quy phep (leave_type) + GPS cham cong + giai trinh quen cham cong (2026-11-09)
-- Cac cot/bang nay API TU TAO khi chay (leave_ensure_columns, att_ensure_columns,
-- expl_ensure_table); file nay chi de doi chieu/khoi phuc thu cong.
ALTER TABLE leave_requests
  ADD COLUMN leave_type VARCHAR(20) NOT NULL DEFAULT 'annual';

ALTER TABLE attendances
  ADD COLUMN check_in_lat DECIMAL(10,7) NULL,
  ADD COLUMN check_in_lng DECIMAL(10,7) NULL,
  ADD COLUMN check_in_remote TINYINT NULL,
  ADD COLUMN check_out_lat DECIMAL(10,7) NULL,
  ADD COLUMN check_out_lng DECIMAL(10,7) NULL,
  ADD COLUMN check_out_remote TINYINT NULL;

CREATE TABLE IF NOT EXISTS attendance_explanations (
  id INT AUTO_INCREMENT PRIMARY KEY,
  employee_id INT NOT NULL,
  work_date DATE NOT NULL,
  kind VARCHAR(10) NOT NULL,
  proposed_in TIME NULL,
  proposed_out TIME NULL,
  reason VARCHAR(300) NULL,
  status VARCHAR(12) NOT NULL DEFAULT 'pending',
  approved_by INT NULL,
  processed_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_emp_date (employee_id, work_date),
  INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
