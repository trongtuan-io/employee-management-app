-- Task 22 only. Run ONCE on existing employee_management DB; no changes to existing tables.
CREATE TABLE IF NOT EXISTS employee_import_batches (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    source_name VARCHAR(200) NOT NULL,
    imported_by INT NOT NULL,
    total_rows INT NOT NULL,
    status ENUM('active','rolled_back') NOT NULL DEFAULT 'active',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_synced_at TIMESTAMP NULL DEFAULT NULL,
    rolled_back_at TIMESTAMP NULL DEFAULT NULL,
    INDEX idx_import_actor (imported_by)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS employee_import_items (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    batch_id BIGINT UNSIGNED NOT NULL,
    `row_number` INT NOT NULL,
    employee_id INT NULL,
    last_hash CHAR(64) NOT NULL,
    UNIQUE KEY uq_batch_row (batch_id,`row_number`),
    UNIQUE KEY uq_batch_employee (batch_id,employee_id),
    INDEX idx_import_employee (employee_id),
    CONSTRAINT fk_import_item_batch FOREIGN KEY (batch_id) REFERENCES employee_import_batches(id) ON DELETE RESTRICT,
    CONSTRAINT fk_import_item_employee FOREIGN KEY (employee_id) REFERENCES employees(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
