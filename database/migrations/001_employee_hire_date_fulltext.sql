-- Apply ONCE after importing database/employee_management.sql.
-- Existing employee rows are preserved; hire_date initially NULL.
SET @migration_ddl = IF(
  EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()
    AND TABLE_NAME='employees' AND COLUMN_NAME='hire_date'),
  'DO 0', 'ALTER TABLE employees ADD COLUMN hire_date DATE NULL AFTER position_id'
);
PREPARE migration_stmt FROM @migration_ddl;
EXECUTE migration_stmt;
DEALLOCATE PREPARE migration_stmt;
SET @migration_ddl = IF(
  EXISTS(SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE()
    AND TABLE_NAME='employees' AND INDEX_NAME='idx_employee_fulltext'),
  'DO 0', 'ALTER TABLE employees ADD FULLTEXT INDEX idx_employee_fulltext (full_name, username, email)'
);
PREPARE migration_stmt FROM @migration_ddl;
EXECUTE migration_stmt;
DEALLOCATE PREPARE migration_stmt;
