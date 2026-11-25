-- Requires 009, 012 and 013. Apply to the isolated HR test database after backing it up.
SET time_zone = '+07:00';

ALTER TABLE employees ADD COLUMN hr_documents_due_at DATETIME NULL,
  ADD INDEX idx_hr_documents_due (hr_documents_due_at);
ALTER TABLE hr_import_batches ADD COLUMN rows_created INT NOT NULL DEFAULT 0;
ALTER TABLE hr_documents MODIFY employee_id INT NULL,
  ADD COLUMN proposal_id BIGINT UNSIGNED NULL,
  ADD INDEX idx_hrdocs_candidate (proposal_id, document_type, id),
  ADD CONSTRAINT fk_hrdocs_candidate FOREIGN KEY (proposal_id) REFERENCES hr_proposals(id) ON DELETE RESTRICT,
  ADD CONSTRAINT chk_hrdocs_owner CHECK (employee_id IS NOT NULL OR proposal_id IS NOT NULL);

-- Position overrides can add requirements (degree, certificate, insurance, etc.) or waive defaults.
CREATE TABLE hr_position_document_requirements (
  position_id INT NOT NULL,
  document_type VARCHAR(30) NOT NULL,
  is_required TINYINT(1) NOT NULL DEFAULT 1,
  PRIMARY KEY (position_id, document_type),
  FOREIGN KEY (position_id) REFERENCES positions(id) ON DELETE CASCADE,
  CHECK (document_type IN ('portrait','cccd_front','cccd_back','insurance','degree','certificate','contract','other')),
  CHECK (is_required IN (0,1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE VIEW hr_required_employee_documents AS
SELECT e.id AS employee_id, defaults.document_type
FROM employees e
CROSS JOIN (SELECT 'portrait' AS document_type UNION ALL SELECT 'cccd_front' UNION ALL SELECT 'cccd_back') defaults
WHERE NOT EXISTS (SELECT 1 FROM hr_position_document_requirements r
  WHERE r.position_id=e.position_id AND r.document_type=defaults.document_type)
UNION ALL
SELECT e.id, r.document_type FROM employees e
JOIN hr_position_document_requirements r ON r.position_id=e.position_id AND r.is_required=1;

-- NULL keeps the behavior of all existing notification types. Each reminder is unique per recipient and employee.
ALTER TABLE notifications ADD COLUMN hr_document_reminder VARCHAR(30)
  GENERATED ALWAYS AS (CASE WHEN type IN ('hr_docs_due_soon','hr_docs_overdue') THEN type ELSE NULL END) VIRTUAL,
  ADD UNIQUE KEY uq_hr_document_reminder (user_id,ref_id,hr_document_reminder);

CREATE EVENT hr_document_deadline_reminders ON SCHEDULE EVERY 1 HOUR
DO
  INSERT IGNORE INTO notifications(user_id,type,title,body,ref_id)
  SELECT recipient.id,
    IF(e.hr_documents_due_at <= NOW(),'hr_docs_overdue','hr_docs_due_soon'),
    IF(e.hr_documents_due_at <= NOW(),'Hồ sơ nhân sự quá hạn','Hồ sơ nhân sự sắp đến hạn'),
    IF(recipient.id=e.id, CONCAT('Vui lòng hoàn thiện giấy tờ. Hạn nộp ',e.hr_documents_due_at),
      CONCAT('Kiểm tra hồ sơ của ',e.username,'. Hạn nộp ',e.hr_documents_due_at)), e.id
  FROM employees e
  JOIN employees recipient ON recipient.status=1 AND (recipient.id=e.id OR EXISTS (
    SELECT 1 FROM roles r JOIN departments d ON d.id=recipient.department_id
    WHERE r.id=recipient.role_id AND LOWER(r.name) IN ('manager','staff') AND d.name='HR'))
  WHERE e.status=1 AND e.hr_documents_due_at <= NOW() + INTERVAL 3 DAY
    AND EXISTS (SELECT 1 FROM hr_required_employee_documents req
      WHERE req.employee_id=e.id AND COALESCE((SELECT doc.status FROM hr_documents doc
        WHERE doc.employee_id=e.id AND doc.document_type=req.document_type ORDER BY doc.id DESC LIMIT 1),'missing')
        IN ('missing','needs_update'));
