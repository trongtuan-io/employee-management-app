<?php
require_once __DIR__ . "/../employee-api/db.php";
$a = $_GET["action"] ?? ""; $m = $_SERVER["REQUEST_METHOD"];

if ($a === "leave-create" && $m === "POST") {
  $b = body();
  if (empty($b["employee_id"]) || empty($b["start_date"]) || empty($b["end_date"])) err("Thieu employee_id/start_date/end_date");
  $pdo->prepare("INSERT INTO leave_requests(employee_id,start_date,end_date,reason,status) VALUES(?,?,?,?,'pending')")
    ->execute([$b["employee_id"], $b["start_date"], $b["end_date"], $b["reason"] ?? ""]);
  out(["id" => $pdo->lastInsertId(), "status" => "pending"]);
}
if ($a === "leave-list" && $m === "GET") {
  $emp = $_GET["employee_id"] ?? "";
  if ($emp !== "") { $stmt = $pdo->prepare("SELECT * FROM leave_requests WHERE employee_id=? ORDER BY id DESC"); $stmt->execute([$emp]); }
  else { $stmt = $pdo->query("SELECT l.*, e.full_name FROM leave_requests l JOIN employees e ON l.employee_id=e.id WHERE l.status='pending' ORDER BY l.id DESC"); }
  out($stmt->fetchAll(PDO::FETCH_ASSOC));
}
if ($a === "leave-approve" && $m === "POST") {
  $b = body();
  if (empty($b["id"]) || empty($b["status"])) err("Thieu id/status");
  $pdo->prepare("UPDATE leave_requests SET status=?, approved_by=? WHERE id=?")->execute([$b["status"], $b["approved_by"] ?? null, $b["id"]]);
  out(["id" => $b["id"], "status" => $b["status"]]);
}
if ($a === "salary-list" && $m === "GET") {
  $stmt = $pdo->prepare("SELECT s.*, e.full_name FROM salaries s JOIN employees e ON s.employee_id=e.id WHERE s.employee_id=? AND s.year=? ORDER BY s.month DESC");
  $stmt->execute([$_GET["employee_id"] ?? 0, $_GET["year"] ?? date("Y")]);
  out($stmt->fetchAll(PDO::FETCH_ASSOC));
}
if ($a === "salary-create" && $m === "POST") {
  $b = body();
  if (empty($b["employee_id"]) || empty($b["month"]) || empty($b["year"])) err("Thieu employee_id/month/year");
  $net = ($b["base_salary"] ?? 0) + ($b["allowance"] ?? 0) + ($b["overtime_pay"] ?? 0) + ($b["bonus"] ?? 0) - ($b["deduction"] ?? 0);
  $pdo->prepare("INSERT INTO salaries(employee_id,month,year,base_salary,allowance,overtime_pay,bonus,deduction,net_salary) VALUES(?,?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE base_salary=VALUES(base_salary),allowance=VALUES(allowance),overtime_pay=VALUES(overtime_pay),bonus=VALUES(bonus),deduction=VALUES(deduction),net_salary=VALUES(net_salary)")
    ->execute([$b["employee_id"], $b["month"], $b["year"], $b["base_salary"] ?? 0, $b["allowance"] ?? 0, $b["overtime_pay"] ?? 0, $b["bonus"] ?? 0, $b["deduction"] ?? 0, $net]);
  out(["net_salary" => $net]);
}
if ($a === "dashboard" && $m === "GET") {
  $total = $pdo->query("SELECT COUNT(*) c FROM employees WHERE status=1")->fetch()["c"];
  $pending = $pdo->query("SELECT COUNT(*) c FROM leave_requests WHERE status='pending'")->fetch()["c"];
  $todayLate = $pdo->query("SELECT COUNT(*) c FROM attendances WHERE work_date=CURDATE() AND status='late'")->fetch()["c"];
  out(["total_active" => (int)$total, "pending_leaves" => (int)$pending, "late_today" => (int)$todayLate]);
}
err("Unknown action");
?>
