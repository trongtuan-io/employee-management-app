-- Migration 005: cung co bang password_resets cho viec luu ma xac nhan dang hash
-- va gioi han so lan nhap sai ma (chong brute-force ma 6 so).
-- - code: mo rong varchar(10) -> varchar(64) de chua hash sha256 (ma tho khong con luu thang).
-- - attempts: dem so lan nhap sai; sai 5 lan ma bi huy.
ALTER TABLE password_resets MODIFY code VARCHAR(64) NOT NULL;
SET @migration_ddl = IF(
  EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()
    AND TABLE_NAME='password_resets' AND COLUMN_NAME='attempts'),
  'DO 0', 'ALTER TABLE password_resets ADD COLUMN attempts TINYINT NOT NULL DEFAULT 0'
);
PREPARE migration_stmt FROM @migration_ddl;
EXECUTE migration_stmt;
DEALLOCATE PREPARE migration_stmt;
