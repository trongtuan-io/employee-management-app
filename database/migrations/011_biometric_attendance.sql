-- Migration 011: Cham cong sinh trac hoc — khuon mat + van tay (2026-11-16)
-- 1) Bang employee_faces: luu vector dac trung khuon mat (embedding 128 chieu
--    do app Android trich bang FaceNet TFLite ngay tren dien thoai).
--    CHI luu vector so, KHONG luu anh goc — nhe, rieng tu, so khop bang cosine.
-- 2) attendances: them phuong thuc cham cong (gps/face/fingerprint) + diem
--    tuong dong khuon mat de doi chieu/truy vet.

CREATE TABLE IF NOT EXISTS employee_faces (
  id INT AUTO_INCREMENT PRIMARY KEY,
  employee_id INT NOT NULL UNIQUE,
  embedding TEXT NOT NULL,               -- JSON: mang 128 so thuc da chuan hoa L2
  model VARCHAR(50) NOT NULL DEFAULT 'facenet-128',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_employee_faces_emp FOREIGN KEY (employee_id)
    REFERENCES employees(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE attendances
  ADD COLUMN check_in_method VARCHAR(20) NOT NULL DEFAULT 'gps' AFTER check_in_remote,
  ADD COLUMN check_out_method VARCHAR(20) NULL AFTER check_out_remote,
  ADD COLUMN check_in_face_score DECIMAL(6,4) NULL AFTER check_in_method,
  ADD COLUMN check_out_face_score DECIMAL(6,4) NULL AFTER check_out_method;
