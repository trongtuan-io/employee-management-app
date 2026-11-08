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
?>
