<?php
// Tinh luong tu dong theo ngay cong (dung boi leaves_salaries.php):
// - Ngay cong chuan cua thang = so ngay trong thang tru ngay Chu nhat
//   (tuan lam viec 6 ngay, pho bien o VN); nguoi goi co the ghi de qua standard_days.
// - Ngay cong tinh luong = ngay co check-in + ngay nghi PHEP da duyet (nghi phep
//   huong nguyen luong), toi da bang ngay cong chuan. Ngay vang khong cham cong
//   va khong co don duyet = nghi khong phep -> tu mat cong, khong can tru tay.
// - Luong theo cong = luong co ban * ngay cong tinh luong / ngay cong chuan.
// - Thuc linh = luong theo cong + phu cap + tang ca + thuong - khau tru.
// Cac ham "thuan" (khong cham DB) nam o day de test logic doc lap duoc.

function salary_ensure_columns(PDO $pdo) {
  static $done = false;
  if ($done) return;
  $done = true;
  // Tu bo sung cot neu chua chay migration 006 (nhu cach api_tokens tu tao bang).
  $cols = [
    "work_days" => "DECIMAL(5,2) NOT NULL DEFAULT 0",
    "standard_days" => "INT NOT NULL DEFAULT 0",
    "finalized" => "TINYINT NOT NULL DEFAULT 0",
    "insurance_base" => "DECIMAL(15,2) NOT NULL DEFAULT 0",
    "insurance_amount" => "DECIMAL(15,2) NOT NULL DEFAULT 0",
    "tax_amount" => "DECIMAL(15,2) NOT NULL DEFAULT 0",
    "paid" => "TINYINT NOT NULL DEFAULT 0",
    "paid_at" => "DATETIME NULL",
  ];
  $stmt = $pdo->prepare("SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'salaries'");
  $stmt->execute();
  $have = array_column($stmt->fetchAll(PDO::FETCH_ASSOC), "COLUMN_NAME");
  foreach ($cols as $name => $def) {
    if (!in_array($name, $have, true)) {
      $pdo->exec("ALTER TABLE salaries ADD COLUMN `$name` $def");
    }
  }
}

// So ngay lam viec chuan trong thang: tong so ngay tru Chu nhat.
function salary_standard_days($year, $month) {
  $first = new DateTimeImmutable(sprintf("%04d-%02d-01", (int)$year, (int)$month));
  $days = (int)$first->format("t");
  $count = 0;
  for ($d = 1; $d <= $days; $d++) {
    if ((int)$first->setDate((int)$year, (int)$month, $d)->format("w") !== 0) $count++;
  }
  return $count;
}

// So ngay lam viec (tru Chu nhat) cua khoang nghi [start,end] nam trong thang
// [monthStart, monthEnd]; dung de cong don nghi phep da duyet vao ngay cong.
function salary_leave_workdays($start, $end, $monthStart, $monthEnd) {
  $s = new DateTimeImmutable(max($start, $monthStart));
  $e = new DateTimeImmutable(min($end, $monthEnd));
  if ($e < $s) return 0;
  $count = 0;
  for ($d = $s; $d <= $e; $d = $d->modify("+1 day")) {
    if ((int)$d->format("w") !== 0) $count++;
  }
  return $count;
}

// Luong theo cong, lam tron ve dong (luong VN khong le xu).
function salary_earned_base($baseSalary, $paidDays, $standardDays) {
  if ($standardDays <= 0) return 0.0;
  return round(((float)$baseSalary) * ((float)$paidDays) / ((float)$standardDays));
}

function salary_net($earnedBase, $allowance, $overtimePay, $bonus, $deduction) {
  return round((float)$earnedBase + (float)$allowance + (float)$overtimePay + (float)$bonus - (float)$deduction);
}

// ===== BHXH + thue TNCN (bo sung 2026-11-09) =====
// Luong dong BH: uu tien muc rieng trong ho so (insurance_salary), khong co thi
// dung luong co ban; tran = 20 lan luong co so 2.340.000 (tu 01/07/2024).
define("SALARY_INSURANCE_CAP", 46800000);
define("SALARY_INSURANCE_RATE", 0.105); // NLĐ: BHXH 8% + BHYT 1.5% + BHTN 1%
define("SALARY_PIT_PERSONAL", 11000000); // Giam tru ban than / thang
define("SALARY_PIT_DEPENDENT", 4400000); // Giam tru moi nguoi phu thuoc / thang

// Cot bo sung trong ho so nhan vien: luong dong BH rieng + so nguoi phu thuoc.
function employee_salary_ensure_columns(PDO $pdo) {
  static $done = false;
  if ($done) return;
  $done = true;
  $stmt = $pdo->prepare("SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'employees'");
  $stmt->execute();
  $have = array_column($stmt->fetchAll(PDO::FETCH_ASSOC), "COLUMN_NAME");
  if (!in_array("insurance_salary", $have, true)) $pdo->exec("ALTER TABLE employees ADD COLUMN insurance_salary DECIMAL(15,2) NULL");
  if (!in_array("dependents", $have, true)) $pdo->exec("ALTER TABLE employees ADD COLUMN dependents INT NOT NULL DEFAULT 0");
}

function salary_insurance_base($fullBase, $insuranceSalary) {
  $base = ($insuranceSalary !== null && (float)$insuranceSalary > 0) ? (float)$insuranceSalary : (float)$fullBase;
  return min($base, SALARY_INSURANCE_CAP);
}

// Thang khong co ngay cong nao (nghi khong luong ca thang) thi khong khau tru BH.
function salary_insurance_amount($insuranceBase, $workDays) {
  if ((float)$workDays <= 0) return 0.0;
  return round((float)$insuranceBase * SALARY_INSURANCE_RATE);
}

// Thue TNCN bieu luy tien tung phan (tinh nhanh theo cong thuc rut gon chuan).
function salary_pit($taxable) {
  $t = (float)$taxable;
  if ($t <= 0) return 0.0;
  if ($t <= 5000000) return round($t * 0.05);
  if ($t <= 10000000) return round($t * 0.10 - 250000);
  if ($t <= 18000000) return round($t * 0.15 - 750000);
  if ($t <= 32000000) return round($t * 0.20 - 1650000);
  if ($t <= 52000000) return round($t * 0.25 - 3250000);
  if ($t <= 80000000) return round($t * 0.30 - 5850000);
  return round($t * 0.35 - 9850000);
}

// Tinh tron goi 1 dong luong: tra ve [insurance_amount, tax_amount, net_salary].
// gross = luong theo cong + phu cap + tang ca + thuong.
// thue tinh tren gross - BH - giam tru (ban than + nguoi phu thuoc).
function salary_full_calc($earned, $allowance, $overtime, $bonus, $deduction, $insuranceBase, $workDays, $dependents) {
  $gross = (float)$earned + (float)$allowance + (float)$overtime + (float)$bonus;
  $ins = salary_insurance_amount($insuranceBase, $workDays);
  $taxable = max(0.0, $gross - $ins - SALARY_PIT_PERSONAL - SALARY_PIT_DEPENDENT * max(0, (int)$dependents));
  $tax = salary_pit($taxable);
  $net = round($gross - (float)$deduction - $ins - $tax);
  return [$ins, $tax, $net];
}

// ===== Lich su thay doi bang luong (truy vet) =====
function salary_ensure_audit_table(PDO $pdo) {
  static $done = false;
  if ($done) return;
  $done = true;
  $pdo->exec("CREATE TABLE IF NOT EXISTS salary_audit_logs (
    id INT AUTO_INCREMENT PRIMARY KEY,
    salary_id INT NOT NULL,
    employee_id INT NOT NULL,
    actor_id INT NOT NULL,
    field VARCHAR(40) NOT NULL,
    old_value VARCHAR(60) NULL,
    new_value VARCHAR(60) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_salary (salary_id)
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
}

function salary_audit(PDO $pdo, $salaryId, $employeeId, $actorId, $field, $old, $new) {
  salary_ensure_audit_table($pdo);
  $pdo->prepare("INSERT INTO salary_audit_logs(salary_id, employee_id, actor_id, field, old_value, new_value) VALUES(?,?,?,?,?,?)")
    ->execute([(int)$salaryId, (int)$employeeId, (int)$actorId, $field,
      $old === null ? null : (string)$old, $new === null ? null : (string)$new]);
}

// ===== Phu cap co dinh theo chinh sach =====
function salary_ensure_policies_table(PDO $pdo) {
  static $done = false;
  if ($done) return;
  $done = true;
  $pdo->exec("CREATE TABLE IF NOT EXISTS salary_policies (
    id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    amount DECIMAL(15,2) NOT NULL DEFAULT 0,
    department_id INT NULL,
    active TINYINT NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
}

// Tong phu cap chinh sach ap dung cho 1 nhan vien (toan cong ty + phong cua ho).
function salary_policy_allowance(PDO $pdo, $departmentId) {
  salary_ensure_policies_table($pdo);
  $stmt = $pdo->prepare("SELECT COALESCE(SUM(amount),0) FROM salary_policies WHERE active=1 AND (department_id IS NULL OR department_id=?)");
  $stmt->execute([(int)$departmentId]);
  return (float)$stmt->fetchColumn();
}
?>
