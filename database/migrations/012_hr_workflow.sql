-- HR module v1: additive migration. TEST COPY ONLY; never run on production without backup/review.
-- Requires existing employees/departments/positions/roles and migration 003.
-- MariaDB/MySQL 8 compatible. Status of employee account (employees.status) unchanged.
ALTER TABLE employees
  ADD COLUMN birth_date DATE NULL,
  ADD COLUMN gender VARCHAR(12) NULL,
  ADD COLUMN address VARCHAR(500) NULL,
  ADD COLUMN direct_manager_id INT NULL,
  ADD COLUMN employment_status VARCHAR(16) NOT NULL DEFAULT 'working',
  ADD COLUMN termination_effective_date DATE NULL,
  ADD INDEX idx_emp_direct_manager (direct_manager_id),
  ADD CONSTRAINT fk_hr_manager FOREIGN KEY (direct_manager_id) REFERENCES employees(id) ON DELETE SET NULL;

ALTER TABLE positions ADD COLUMN is_active TINYINT(1) NOT NULL DEFAULT 1;

-- Shared approval inbox: BOD team should agree on this schema before adding its approval endpoint.
-- HR can write draft/pending only. No automatic apply, no self-approval.
CREATE TABLE hr_proposals (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  proposal_type VARCHAR(30) NOT NULL,
  employee_id INT NULL,
  proposed_by INT NOT NULL,
  before_data JSON NULL,
  proposed_data JSON NOT NULL,
  -- Unique reservations for draft/pending recruitment. Clear on BOD rejection/approval.
  reservation_username VARCHAR(50) NULL,
  reservation_email VARCHAR(100) NULL,
  UNIQUE KEY uq_hr_candidate_username (reservation_username),
  UNIQUE KEY uq_hr_candidate_email (reservation_email),
  reason VARCHAR(1000) NOT NULL,
  effective_date DATE NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'draft',
  reviewed_by INT NULL,
  reviewed_at DATETIME NULL,
  review_note TEXT NULL,
  applied_at DATETIME NULL,
  version INT NOT NULL DEFAULT 1,
  import_batch_id BIGINT UNSIGNED NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_hrp_status (status, proposal_type, created_at),
  KEY idx_hrp_employee (employee_id, created_at),
  KEY idx_hrp_actor (proposed_by, created_at),
  CONSTRAINT fk_hrp_employee FOREIGN KEY (employee_id) REFERENCES employees(id) ON DELETE RESTRICT,
  CONSTRAINT fk_hrp_creator FOREIGN KEY (proposed_by) REFERENCES employees(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE hr_import_batches (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  source_name VARCHAR(255) NOT NULL,
  submitted_by INT NOT NULL,
  rows_total INT NOT NULL,
  rows_proposed INT NOT NULL DEFAULT 0,
  rows_updated INT NOT NULL DEFAULT 0,
  rows_refreshed INT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_hr_import_actor (submitted_by, created_at),
  CONSTRAINT fk_hrib_actor FOREIGN KEY (submitted_by) REFERENCES employees(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE hr_documents (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  employee_id INT NOT NULL,
  document_type VARCHAR(30) NOT NULL,
  original_filename VARCHAR(255) NOT NULL,
  storage_key CHAR(48) NOT NULL UNIQUE,
  mime_type VARCHAR(80) NOT NULL,
  byte_size INT UNSIGNED NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'pending',
  review_note VARCHAR(500) NULL,
  reviewed_by INT NULL,
  reviewed_at DATETIME NULL,
  uploaded_by INT NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_hrdocs_employee (employee_id, document_type),
  CONSTRAINT fk_hrdocs_emp FOREIGN KEY (employee_id) REFERENCES employees(id) ON DELETE RESTRICT,
  CONSTRAINT fk_hrdocs_reviewer FOREIGN KEY (reviewed_by) REFERENCES employees(id) ON DELETE SET NULL,
  CONSTRAINT fk_hrdocs_uploader FOREIGN KEY (uploaded_by) REFERENCES employees(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE hr_history (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  employee_id INT NULL,
  actor_id INT NOT NULL,
  action VARCHAR(60) NOT NULL,
  before_data JSON NULL,
  after_data JSON NULL,
  proposal_id BIGINT UNSIGNED NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_hrhistory_emp (employee_id, created_at),
  KEY idx_hrhistory_proposal (proposal_id),
  CONSTRAINT fk_hrhist_emp FOREIGN KEY (employee_id) REFERENCES employees(id) ON DELETE RESTRICT,
  CONSTRAINT fk_hrhist_actor FOREIGN KEY (actor_id) REFERENCES employees(id) ON DELETE RESTRICT,
  CONSTRAINT fk_hrhist_proposal FOREIGN KEY (proposal_id) REFERENCES hr_proposals(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
