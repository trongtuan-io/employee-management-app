-- HR internal review: Staff HR -> Manager HR -> BOD -> HR onboarding.
-- Run ONLY on the isolated employee_management_hr_test copy, AFTER migration 012.
-- Do not run 013_hr_recruitment_two_levels.sql from the discarded alternative.
-- Existing pending BOD proposals remain untouched; workflow_version=1 is legacy.
ALTER TABLE hr_proposals
  ADD COLUMN workflow_version TINYINT UNSIGNED NOT NULL DEFAULT 1 AFTER version,
  ADD COLUMN hr_reviewed_by INT NULL AFTER workflow_version,
  ADD COLUMN hr_reviewed_at DATETIME NULL AFTER hr_reviewed_by,
  ADD COLUMN hr_review_note VARCHAR(500) NULL AFTER hr_reviewed_at,
  ADD INDEX idx_hrp_internal_review (proposal_type, status, workflow_version),
  ADD CONSTRAINT fk_hrp_internal_reviewer FOREIGN KEY (hr_reviewed_by)
    REFERENCES employees(id) ON DELETE SET NULL;
