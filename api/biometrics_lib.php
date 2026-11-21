<?php
// Thu vien sinh trac hoc dung chung (2026-11-16): dang ky + doi khop khuon mat
// cho cham cong. App Android trich vector dac trung (embedding 128 chieu,
// FaceNet TFLite) ngay tren dien thoai roi gui vector len — server CHI luu
// vector so va so khop bang cosine similarity, khong nhan/khong luu anh goc.
// Van tay: Android khong cho app doc du lieu van tay tho; app dung
// BiometricPrompt xac thuc van tay da dang ky tren may roi moi goi API
// (attendances.php ghi method='fingerprint' khi client bao da xac thuc).

// Nguong khop khuon mat: cosine >= 0.70 moi coi la cung mot nguoi.
// FaceNet tren tap chuan (LFW) cho diem cung-nguoi thuong 0.75–0.95,
// khac-nguoi thuong < 0.55; 0.70 la muc can bang giua tien loi va an toan.
define("FACE_SIM_THRESHOLD", 0.70);
define("FACE_EMBEDDING_DIM", 128);

function bio_ensure_schema(PDO $pdo) {
  static $done = false;
  if ($done) return;
  $done = true;
  $pdo->exec("CREATE TABLE IF NOT EXISTS employee_faces (
    id INT AUTO_INCREMENT PRIMARY KEY,
    employee_id INT NOT NULL UNIQUE,
    embedding TEXT NOT NULL,
    model VARCHAR(50) NOT NULL DEFAULT 'facenet-128',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_employee_faces_emp FOREIGN KEY (employee_id)
      REFERENCES employees(id) ON DELETE CASCADE
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
  // Cot phuong thuc cham cong tren bang attendances (migration 011; tu tao
  // o day de DB chua chay migration van dung duoc).
  $stmt = $pdo->prepare("SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'attendances'");
  $stmt->execute();
  $have = array_column($stmt->fetchAll(PDO::FETCH_ASSOC), "COLUMN_NAME");
  $cols = [
    "check_in_method" => "VARCHAR(20) NOT NULL DEFAULT 'gps'",
    "check_out_method" => "VARCHAR(20) NULL",
    "check_in_face_score" => "DECIMAL(6,4) NULL",
    "check_out_face_score" => "DECIMAL(6,4) NULL",
  ];
  foreach ($cols as $name => $def) {
    if (!in_array($name, $have, true)) $pdo->exec("ALTER TABLE attendances ADD COLUMN `$name` $def");
  }
}

// Kiem tra + chuan hoa embedding tu client: bat buoc du FACE_EMBEDDING_DIM so
// thuc huu han; tra ve mang float da chuan hoa L2 (do dai = 1) hoac null.
function bio_validate_embedding($raw) {
  if (!is_array($raw) || count($raw) !== FACE_EMBEDDING_DIM) return null;
  $v = [];
  $norm = 0.0;
  foreach ($raw as $x) {
    if (!is_numeric($x)) return null;
    $f = (float)$x;
    if (!is_finite($f)) return null;
    $v[] = $f;
    $norm += $f * $f;
  }
  $norm = sqrt($norm);
  if ($norm < 1e-9) return null;
  return array_map(fn($f) => $f / $norm, $v);
}

function bio_cosine(array $a, array $b) {
  $dot = 0.0; $na = 0.0; $nb = 0.0;
  $n = min(count($a), count($b));
  for ($i = 0; $i < $n; $i++) {
    $dot += $a[$i] * $b[$i];
    $na += $a[$i] * $a[$i];
    $nb += $b[$i] * $b[$i];
  }
  if ($na < 1e-12 || $nb < 1e-12) return 0.0;
  return $dot / (sqrt($na) * sqrt($nb));
}

// Doi khop khuon mat cua 1 nhan vien: tra [status, score]
// status: 'ok' (khop) | 'mismatch' (khong khop) | 'not_enrolled' (chua dang ky)
function bio_verify_face(PDO $pdo, $employeeId, array $embedding) {
  bio_ensure_schema($pdo);
  $stmt = $pdo->prepare("SELECT embedding FROM employee_faces WHERE employee_id=? LIMIT 1");
  $stmt->execute([(int)$employeeId]);
  $row = $stmt->fetch(PDO::FETCH_ASSOC);
  if (!$row) return ["not_enrolled", null];
  $saved = json_decode($row["embedding"], true);
  if (!is_array($saved) || count($saved) !== FACE_EMBEDDING_DIM) return ["not_enrolled", null];
  $score = bio_cosine(array_map("floatval", $saved), $embedding);
  return [$score >= FACE_SIM_THRESHOLD ? "ok" : "mismatch", round($score, 4)];
}
