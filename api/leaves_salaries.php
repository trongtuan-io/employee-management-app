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
  $pdo->prepare("INSERT INTO leave_requests(employee_id,start_date,end_date,reason,status) VALUES(?,?,?,?,'pending')")
    ->execute([$b["employee_id"], $b["start_date"], $b["end_date"], $b["reason"] ?? ""]);
  out(["id" => $pdo->lastInsertId(), "status" => "pending"]);
}
if ($a === "leave-list" && $m === "GET") {
  $me = require_login($pdo);
  $emp = $_GET["employee_id"] ?? "";
  if ($emp !== "") {
    if ((int)$emp !== (int)$me["id"] && !in_array($me["role"], ["admin", "manager"], true)) err("Khong co quyen xem don cua nguoi khac", 403);
  } else {
    if (!in_array($me["role"], ["admin", "manager"], true)) err("Khong co quyen xem danh sach don cho duyet", 403);
  }
  if ($emp !== "") { $stmt = $pdo->prepare("SELECT * FROM leave_requests WHERE employee_id=? ORDER BY id DESC"); $stmt->execute([$emp]); }
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
  $pdo->prepare("UPDATE leave_requests SET status=?, approved_by=? WHERE id=?")->execute([$b["status"], $me["id"], (int)$b["id"]]);
  out(["id" => (int)$b["id"], "status" => $b["status"]]);
}
if ($a === "salary-list" && $m === "GET") {
  $me = require_login($pdo);
  if ((int)($_GET["employee_id"] ?? 0) !== (int)$me["id"] && !in_array($me["role"], ["admin", "manager"], true)) err("Khong co quyen xem luong nguoi khac", 403);
  $stmt = $pdo->prepare("SELECT s.*, e.full_name FROM salaries s JOIN employees e ON s.employee_id=e.id WHERE s.employee_id=? AND s.year=? ORDER BY s.month DESC");
  $stmt->execute([$_GET["employee_id"] ?? 0, $_GET["year"] ?? date("Y")]);
  out($stmt->fetchAll(PDO::FETCH_ASSOC));
}
if ($a === "salary-create" && $m === "POST") {
  require_role($pdo, ["admin", "manager"]);
  $b = body();
  if (empty($b["employee_id"]) || empty($b["month"]) || empty($b["year"])) err("Thieu employee_id/month/year");
  $net = ($b["base_salary"] ?? 0) + ($b["allowance"] ?? 0) + ($b["overtime_pay"] ?? 0) + ($b["bonus"] ?? 0) - ($b["deduction"] ?? 0);
  $pdo->prepare("INSERT INTO salaries(employee_id,month,year,base_salary,allowance,overtime_pay,bonus,deduction,net_salary) VALUES(?,?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE base_salary=VALUES(base_salary),allowance=VALUES(allowance),overtime_pay=VALUES(overtime_pay),bonus=VALUES(bonus),deduction=VALUES(deduction),net_salary=VALUES(net_salary)")
    ->execute([$b["employee_id"], $b["month"], $b["year"], $b["base_salary"] ?? 0, $b["allowance"] ?? 0, $b["overtime_pay"] ?? 0, $b["bonus"] ?? 0, $b["deduction"] ?? 0, $net]);
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
