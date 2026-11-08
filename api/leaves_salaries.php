<?php
require_once __DIR__ . "/db.php";
require_once __DIR__ . "/auth_guard.php";
require_once __DIR__ . "/salary_calc.php";
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
// Tinh luong TU DONG theo ngay cong cho ca thang (admin: toan cong ty, manager: phong minh).
// Sinh bang luong NHAP: dong da CHOT (finalized=1) khong bi ghi de; phu cap/tang ca/
// thuong/khau tru cua dong cu duoc GIU NGUYEN, chi tinh lai phan luong theo cong va thuc linh.
if ($a === "salary-generate" && $m === "POST") {
  $me = require_role($pdo, ["admin", "manager"]);
  salary_ensure_columns($pdo);
  $b = body();
  $month = (int)($b["month"] ?? date("n")); $year = (int)($b["year"] ?? date("Y"));
  if ($month < 1 || $month > 12 || $year < 2000 || $year > 2100) err("Thang/nam khong hop le", 422);
  $std = isset($b["standard_days"]) && is_numeric($b["standard_days"]) ? (int)$b["standard_days"] : salary_standard_days($year, $month);
  if ($std <= 0 || $std > 31) err("Ngay cong chuan khong hop le", 422);
  $monthStart = sprintf("%04d-%02d-01", $year, $month);
  $monthEnd = date("Y-m-t", strtotime($monthStart));
  if ($me["role"] === "manager") {
    $emps = $pdo->prepare("SELECT id, full_name, base_salary FROM employees WHERE status=1 AND department_id=? ORDER BY id");
    $emps->execute([(int)$me["department_id"]]);
  } else {
    $emps = $pdo->query("SELECT id, full_name, base_salary FROM employees WHERE status=1 ORDER BY id");
  }
  $attStmt = $pdo->prepare("SELECT COUNT(*) FROM attendances WHERE employee_id=? AND work_date BETWEEN ? AND ? AND check_in_time IS NOT NULL");
  $leaveStmt = $pdo->prepare("SELECT start_date, end_date FROM leave_requests WHERE employee_id=? AND status='approved' AND start_date<=? AND end_date>=?");
  $findStmt = $pdo->prepare("SELECT * FROM salaries WHERE employee_id=? AND month=? AND year=?");
  $items = []; $generated = 0; $skipped = 0;
  foreach ($emps->fetchAll(PDO::FETCH_ASSOC) as $e) {
    $empId = (int)$e["id"];
    $attStmt->execute([$empId, $monthStart, $monthEnd]);
    $attDays = (int)$attStmt->fetchColumn();
    $leaveStmt->execute([$empId, $monthEnd, $monthStart]);
    $leaveDays = 0;
    foreach ($leaveStmt->fetchAll(PDO::FETCH_ASSOC) as $lv) {
      $leaveDays += salary_leave_workdays($lv["start_date"], $lv["end_date"], $monthStart, $monthEnd);
    }
    $paidDays = min($std, $attDays + $leaveDays);
    $earned = salary_earned_base($e["base_salary"], $paidDays, $std);
    $findStmt->execute([$empId, $month, $year]);
    $old = $findStmt->fetch(PDO::FETCH_ASSOC);
    if ($old && (int)$old["finalized"] === 1) { $skipped++; continue; }
    if ($old) {
      $net = salary_net($earned, $old["allowance"], $old["overtime_pay"], $old["bonus"], $old["deduction"]);
      $pdo->prepare("UPDATE salaries SET base_salary=?, work_days=?, standard_days=?, net_salary=? WHERE id=?")
        ->execute([$earned, $paidDays, $std, $net, (int)$old["id"]]);
    } else {
      $net = $earned;
      $pdo->prepare("INSERT INTO salaries(employee_id,month,year,base_salary,allowance,overtime_pay,bonus,deduction,net_salary,work_days,standard_days,finalized) VALUES(?,?,?, ?,0,0,0,0, ?,?,?,0)")
        ->execute([$empId, $month, $year, $earned, $net, $paidDays, $std]);
    }
    $generated++;
    $items[] = ["employee_id" => $empId, "full_name" => $e["full_name"], "attendance_days" => $attDays,
      "leave_days" => $leaveDays, "work_days" => $paidDays, "standard_days" => $std,
      "base_salary" => $earned, "net_salary" => $net];
  }
  out(["month" => $month, "year" => $year, "standard_days" => $std,
    "generated" => $generated, "skipped_finalized" => $skipped, "items" => $items]);
}
// Quan ly sua cac khoan cong/tru cua 1 dong bang luong NHAP (thuong, phu cap, tang ca, khau tru).
if ($a === "salary-adjust" && $m === "POST") {
  $me = require_role($pdo, ["admin", "manager"]);
  salary_ensure_columns($pdo);
  $b = body();
  if (empty($b["id"]) || !is_numeric($b["id"])) err("Thieu id bang luong");
  $stmt = $pdo->prepare("SELECT s.*, e.department_id FROM salaries s JOIN employees e ON s.employee_id=e.id WHERE s.id=?");
  $stmt->execute([(int)$b["id"]]);
  $row = $stmt->fetch(PDO::FETCH_ASSOC);
  if (!$row) err("Khong tim thay bang luong", 404);
  if ($me["role"] === "manager" && (int)$row["department_id"] !== (int)$me["department_id"]) err("Chi sua duoc bang luong nhan vien phong minh", 403);
  if ((int)$row["finalized"] === 1) err("Bang luong da chot, khong sua duoc", 409);
  $fields = ["allowance", "overtime_pay", "bonus", "deduction"];
  foreach ($fields as $f) {
    if (isset($b[$f])) {
      if (!is_numeric($b[$f]) || (float)$b[$f] < 0) err("So tien $f khong hop le", 422);
      $row[$f] = (float)$b[$f];
    }
  }
  $net = salary_net($row["base_salary"], $row["allowance"], $row["overtime_pay"], $row["bonus"], $row["deduction"]);
  $pdo->prepare("UPDATE salaries SET allowance=?, overtime_pay=?, bonus=?, deduction=?, net_salary=? WHERE id=?")
    ->execute([$row["allowance"], $row["overtime_pay"], $row["bonus"], $row["deduction"], $net, (int)$row["id"]]);
  out(["id" => (int)$row["id"], "net_salary" => $net]);
}
// Chot bang luong: theo tung dong (id) hoac ca thang (month+year, trong pham vi vai tro).
if ($a === "salary-finalize" && $m === "POST") {
  $me = require_role($pdo, ["admin", "manager"]);
  salary_ensure_columns($pdo);
  $b = body();
  if (!empty($b["id"]) && is_numeric($b["id"])) {
    $stmt = $pdo->prepare("SELECT e.department_id FROM salaries s JOIN employees e ON s.employee_id=e.id WHERE s.id=?");
    $stmt->execute([(int)$b["id"]]);
    $row = $stmt->fetch(PDO::FETCH_ASSOC);
    if (!$row) err("Khong tim thay bang luong", 404);
    if ($me["role"] === "manager" && (int)$row["department_id"] !== (int)$me["department_id"]) err("Chi chot duoc bang luong nhan vien phong minh", 403);
    $upd = $pdo->prepare("UPDATE salaries SET finalized=1 WHERE id=?");
    $upd->execute([(int)$b["id"]]);
    out(["id" => (int)$b["id"], "finalized" => 1]);
  }
  $month = (int)($b["month"] ?? 0); $year = (int)($b["year"] ?? 0);
  if ($month < 1 || $month > 12 || $year < 2000 || $year > 2100) err("Can id hoac month/year hop le", 422);
  if ($me["role"] === "manager") {
    $upd = $pdo->prepare("UPDATE salaries s JOIN employees e ON s.employee_id=e.id SET s.finalized=1 WHERE s.month=? AND s.year=? AND e.department_id=?");
    $upd->execute([$month, $year, (int)$me["department_id"]]);
  } else {
    $upd = $pdo->prepare("UPDATE salaries SET finalized=1 WHERE month=? AND year=?");
    $upd->execute([$month, $year]);
  }
  out(["month" => $month, "year" => $year, "finalized_count" => $upd->rowCount()]);
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
