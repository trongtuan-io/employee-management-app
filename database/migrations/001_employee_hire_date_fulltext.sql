-- Apply ONCE after importing database/employee_management.sql.
-- Existing employee rows are preserved; hire_date initially NULL.
ALTER TABLE employees ADD COLUMN hire_date DATE NULL AFTER position_id;
ALTER TABLE employees ADD FULLTEXT INDEX idx_employee_fulltext (full_name, username, email);
