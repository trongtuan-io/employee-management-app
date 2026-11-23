-- Apply ONCE after importing database/employee_management.sql.
-- Bang luu token dang nhap (server chi luu hash sha256 cua token, khong luu token goc).
-- Luu y: api/auth_guard.php cung tu tao bang nay neu chua co, migration nay de DB duoc khai bao ro rang.
CREATE TABLE IF NOT EXISTS `api_tokens` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `employee_id` int(11) NOT NULL,
  `token_hash` char(64) NOT NULL,
  `expires_at` datetime NOT NULL,
  `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_api_tokens_hash` (`token_hash`),
  KEY `idx_api_tokens_employee` (`employee_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
