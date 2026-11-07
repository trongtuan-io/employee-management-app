<?php
require_once __DIR__ . "/db.php";
require_once __DIR__ . "/auth_guard.php";
$a = $_GET["action"] ?? ""; $m = $_SERVER["REQUEST_METHOD"];

if ($a === "leave-create" && $m === "POST") {
  $me = require_login($pdo);
  $b = body();
  if (empty($b["employee_id"])) $b["employee_id"] = $me["id"];
  if ((int)$b["employee_id"] !== (int)$me["id"] && !in_array($me["role"], ["admin", "manager"], true)) err("Chi duoc tao don nghi cho chinh minh", 403);
  if (empty($b["employee_id"]) || empty($b["start_date"]) || empty($b["end_date"])) err("Thieu employee_id/start_date/end_date");
  // Sep phong tao don ho thi chi cho nhan vien phong minh.
  if ($me["role"] === "manager" && (int)$b["employee_id"] !== (int)$me["id"]) {
    $st = $pdo->prepare("SELECT department_id FROM employees WHERE id=?");
    $st->execute([(int)$b["employee_id"]]);
    $tgt = $st->fetch(PDO::FETCH_ASSOC);
    if (!$tgt) err("Khong tim thay nhan vien", 404);
    if ((int)$tgt["department_id"] !== (int)$me["department_id"]) err("Chi duoc tao don cho nhan vien phong minh", 403);
  }
  // Validate ngay o tang ung dung (truoc day dua vao CHECK cua DB -> loi 500 kho hieu).
  $sd = is_string($b["start_date"]) ? DateTimeImmutable::createFromFormat("!Y-m-d", $b["start_date"]) : false;
  $ed = is_string($b["end_date"]) ? DateTimeImmutable::createFromFormat("!Y-m-d", $b["end_date"]) : false;
  if (!$sd || $sd->format("Y-m-d") !== $b["start_date"] || !$ed || $ed->format("Y-m-d") !== $b["end_date"]) err("Ngay nghi phai co dang YYYY-MM-DD hop le", 422);
  if ($ed < $sd) err("Ngay ket thuc khong duoc truoc ngay bat dau", 422);
  // Chong gui trung: cung nhan vien + cung khoang ngay ma con don dang cho duyet.
  $st = $pdo->prepare("SELECT COUNT(*) FROM leave_requests WHERE employee_id=? AND start_date=? AND end_date=? AND status='pending'");
  $st->execute([(int)$b["employee_id"], $b["start_date"], $b["end_date"]]);
  if ((int)$st->fetchColumn() > 0) err("Da co don nghi trung khoang ngay nay dang cho duyet", 409);
  $pdo->prepare("INSERT INTO leave_requests(employee_id,start_date,end_date,reason,status) VALUES(?,?,?,?,'pending')")
    ->execute([$b["employee_id"], $b["start_date"], $b["end_date"], is_string($b["reason"] ?? null) ? $b["reason"] : ""]);
  out(["id" => $pdo->lastInsertId(), "status" => "pending"]);
}
if ($a === "leave-list" && $m === "GET") {
  $me = require_login($pdo);
  $emp = $_GET["employee_id"] ?? "";
  // Nhan vien khong truyen employee_id: mac dinh xem don cua chinh minh (khong phai hang cho duyet).
  if ($emp === "" && $me["role"] === "staff") $emp = (string)(int)$me["id"];
  if ($emp !== "") {
    if ((int)$emp !== (int)$me["id"] && !in_array($me["role"], ["admin", "manager"], true)) err("Khong co quyen xem don cua nguoi khac", 403);
  } else {
    if (!in_array($me["role"], ["admin", "manager"], true)) err("Khong co quyen xem danh sach don cho duyet", 403);
  }
  if ($emp !== "") {
    // Sep phong xem don theo tung nhan vien thi cung chi trong phong minh.
    if ($me["role"] === "manager" && (int)$emp !== (int)$me["id"]) {
      $st = $pdo->prepare("SELECT department_id FROM employees WHERE id=?");
      $st->execute([(int)$emp]);
      $tgt = $st->fetch(PDO::FETCH_ASSOC);
      if (!$tgt || (int)$tgt["department_id"] !== (int)$me["department_id"]) err("Chi xem duoc don cua nhan vien phong minh", 403);
    }
    $stmt = $pdo->prepare("SELECT * FROM leave_requests WHERE employee_id=? ORDER BY id DESC"); $stmt->execute([$emp]);
  }
  else {
    // Hang cho duyet: admin thay tat ca, sep phong chi thay don cua phong minh.
    if ($me["role"] === "manager") {
      $stmt = $pdo->prepare("SELECT l.*, e.full_name FROM leave_requests l JOIN employees e ON l.employee_id=e.id WHERE l.status='pending' AND e.department_id=? ORDER BY l.id DESC");
      $stmt->execute([(int)$me["department_id"]]);
    } else {
      $stmt = $pdo->query("SELECT l.*, e.full_name FROM leave_requests l JOIN employees e ON l.employee_id=e.id WHERE l.status='pending' ORDER BY l.id DESC");
    }
  }
  out($stmt->fetchAll(PDO::FETCH_ASSOC));
}
if ($a === "leave-approve" && $m === "POST") {
  $me = require_role($pdo, ["admin", "manager"]); // approved_by lay tu token, client khong tu khai duoc
  $b = body();
  if (empty($b["id"]) || empty($b["status"])) err("Thieu id/status");
  if (!in_array($b["status"], ["approved", "rejected"], true)) err("status chi duoc la approved/rejected");
  $stmt = $pdo->prepare("SELECT l.status, e.department_id FROM leave_requests l JOIN employees e ON l.employee_id = e.id WHERE l.id = ? LIMIT 1");
  $stmt->execute([(int)$b["id"]]);
  $leave = $stmt->fetch(PDO::FETCH_ASSOC);
  if (!$leave) err("Khong tim thay don nghi", 404);
  if ($leave["status"] !== "pending") err("Don nay da duoc xu ly roi", 409);
  // Sep phong chi duyet duoc don cua nhan vien phong minh.
  if ($me["role"] === "manager" && (int)$leave["department_id"] !== (int)$me["department_id"]) {
    err("Chi duyet duoc don nghi cua nhan vien phong ban minh", 403);
  }
  // Cap nhat kem dieu kien status='pending' de 2 nguoi duyet cung luc khong ghi de nhau.
  $upd = $pdo->prepare("UPDATE leave_requests SET status=?, approved_by=? WHERE id=? AND status='pending'");
  $upd->execute([$b["status"], $me["id"], (int)$b["id"]]);
  if ($upd->rowCount() === 0) err("Don nay da duoc xu ly roi", 409);
  out(["id" => (int)$b["id"], "status" => $b["status"]]);
}
if ($a === "salary-list" && $m === "GET") {
  $me = require_login($pdo);
  // Khong truyen employee_id thi mac dinh xem cua chinh minh.
  $empId = (int)($_GET["employee_id"] ?? $me["id"]);
  if ($empId !== (int)$me["id"] && !in_array($me["role"], ["admin", "manager"], true)) err("Khong co quyen xem luong nguoi khac", 403);
  // Sep phong chi xem luong nhan vien phong minh.
  if ($me["role"] === "manager" && $empId !== (int)$me["id"]) {
    $st = $pdo->prepare("SELECT department_id FROM employees WHERE id=?");
    $st->execute([$empId]);
    $tgt = $st->fetch(PDO::FETCH_ASSOC);
    if (!$tgt || (int)$tgt["department_id"] !== (int)$me["department_id"]) err("Chi xem duoc luong nhan vien phong minh", 403);
  }
  $stmt = $pdo->prepare("SELECT s.*, e.full_name FROM salaries s JOIN employees e ON s.employee_id=e.id WHERE s.employee_id=? AND s.year=? ORDER BY s.month DESC");
  $stmt->execute([$empId, $_GET["year"] ?? date("Y")]);
  out($stmt->fetchAll(PDO::FETCH_ASSOC));
}
if ($a === "salary-create" && $m === "POST") {
  $me = require_role($pdo, ["admin", "manager"]);
  $b = body();
  if (empty($b["employee_id"]) || empty($b["month"]) || empty($b["year"])) err("Thieu employee_id/month/year");
  $month = (int)$b["month"]; $year = (int)$b["year"];
  if ($month < 1 || $month > 12) err("Thang phai tu 1 den 12", 422);
  if ($year < 2000 || $year > 2100) err("Nam khong hop le", 422);
  // Moi khoan tien phai la so >= 0 (truoc day nhap so am van luu, ra luong thuc linh am).
  $money = [];
  foreach (["base_salary", "allowance", "overtime_pay", "bonus", "deduction"] as $k) {
    $v = $b[$k] ?? 0;
    if (!is_scalar($v) || !is_numeric($v) || (float)$v < 0) err("Khoan $k phai la so khong am", 422);
    $money[$k] = (float)$v;
  }
  // Sep phong chi duoc tao luong cho nhan vien phong minh (nhat quan phan quyen theo phong).
  if ($me["role"] === "manager") {
    $st = $pdo->prepare("SELECT department_id FROM employees WHERE id=?");
    $st->execute([(int)$b["employee_id"]]);
    $tgt = $st->fetch(PDO::FETCH_ASSOC);
    if (!$tgt) err("Khong tim thay nhan vien", 404);
    if ((int)$tgt["department_id"] !== (int)$me["department_id"]) err("Chi duoc tao luong cho nhan vien phong minh", 403);
  }
  $net = $money["base_salary"] + $money["allowance"] + $money["overtime_pay"] + $money["bonus"] - $money["deduction"];
  $pdo->prepare("INSERT INTO salaries(employee_id,month,year,base_salary,allowance,overtime_pay,bonus,deduction,net_salary) VALUES(?,?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE base_salary=VALUES(base_salary),allowance=VALUES(allowance),overtime_pay=VALUES(overtime_pay),bonus=VALUES(bonus),deduction=VALUES(deduction),net_salary=VALUES(net_salary)")
    ->execute([(int)$b["employee_id"], $month, $year, $money["base_salary"], $money["allowance"], $money["overtime_pay"], $money["bonus"], $money["deduction"], $net]);
  out(["net_salary" => $net]);
}
if ($a === "dashboard" && $m === "GET") {
  $me = require_login($pdo);
  if ($me["role"] === "admin") {
    $total = $pdo->query("SELECT COUNT(*) c FROM employees WHERE status=1")->fetch()["c"];
    $pending = $pdo->query("SELECT COUNT(*) c FROM leave_requests WHERE status='pending'")->fetch()["c"];
    $todayLate = $pdo->query("SELECT COUNT(*) c FROM attendances WHERE work_date=CURDATE() AND status='late'")->fetch()["c"];
  } else {
    // Manager/staff: so lieu chi trong phong ban cua minh.
    $dept = (int)$me["department_id"];
    $s = $pdo->prepare("SELECT COUNT(*) c FROM employees WHERE status=1 AND department_id=?"); $s->execute([$dept]);
    $total = $s->fetch()["c"];
    $s = $pdo->prepare("SELECT COUNT(*) c FROM leave_requests l JOIN employees e ON l.employee_id=e.id WHERE l.status='pending' AND e.department_id=?"); $s->execute([$dept]);
    $pending = $s->fetch()["c"];
    $s = $pdo->prepare("SELECT COUNT(*) c FROM attendances a JOIN employees e ON a.employee_id=e.id WHERE a.work_date=CURDATE() AND a.status='late' AND e.department_id=?"); $s->execute([$dept]);
    $todayLate = $s->fetch()["c"];
  }
  out(["total_active" => (int)$total, "pending_leaves" => (int)$pending, "late_today" => (int)$todayLate]);
}
err("Unknown action");
?>
