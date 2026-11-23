-- 009: Trung tam thong bao trong app (2026-11-09)
-- Bang nay do API tu tao khi can (notify_ensure_table trong leaves_salaries.php);
-- file nay chi de doi chieu hoac cai moi bang tay.
CREATE TABLE IF NOT EXISTS notifications (
  id INT AUTO_INCREMENT PRIMARY KEY,
  user_id INT NOT NULL,
  type VARCHAR(30) NOT NULL,
  title VARCHAR(160) NOT NULL,
  body VARCHAR(400) NULL,
  ref_id INT NULL,
  is_read TINYINT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_user_read (user_id, is_read)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
