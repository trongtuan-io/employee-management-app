<?php
require_once __DIR__ . "/db.php";
require_once __DIR__ . "/auth_guard.php";
$a = $_GET["action"] ?? ""; $m = $_SERVER["REQUEST_METHOD"];

if ($a === "checkin" && $m === "POST") {
  $me = require_login($pdo);
  $b = body(); $emp = $b["employee_id"] ?? $me["id"]; // mac dinh la chinh nguoi dang nhap
  if ((int)$emp !== (int)$me["id"]) err("Chi duoc cham cong cho chinh minh", 403);
  $today = date("Y-m-d"); $now = date("Y-m-d H:i:s");
  $late = (strtotime(date("H:i")) > strtotime("08:15")) ? "late" : "present";
  try {
    $pdo->prepare("INSERT INTO attendances(employee_id,work_date,check_in_time,status) VALUES(?,?,?,?)")->execute([$emp, $today, $now, $late]);
    out(["check_in" => $now, "status" => $late]);
  } catch (PDOException $e) {
    // Chi loi trung khoa (da check-in) moi la 409; loi DB khac phai lo ra dung ban chat.
    if ($e->getCode() === "23000") err("Hom nay da check-in roi", 409);
    error_log("checkin failed: " . $e->getMessage());
    err("Khong check-in duoc, vui long thu lai", 500);
  }
}
if ($a === "checkout" && $m === "POST") {
  $me = require_login($pdo);
  $b = body(); $emp = $b["employee_id"] ?? $me["id"]; $today = date("Y-m-d"); $now = date("Y-m-d H:i:s");
  if ((int)$emp !== (int)$me["id"]) err("Chi duoc cham cong cho chinh minh", 403);
  // Chi check-out duoc khi da check-in va chua check-out; kiem tra so dong cap nhat thay vi bao success mu.
  $stmt = $pdo->prepare("UPDATE attendances SET check_out_time=? WHERE employee_id=? AND work_date=? AND check_out_time IS NULL");
  $stmt->execute([$now, $emp, $today]);
  if ($stmt->rowCount() === 0) {
    // Quen checkout hom qua: dong not phien dang mo cua HOM QUA (khong dong ho phien cu hon).
    $stmt = $pdo->prepare("UPDATE attendances SET check_out_time=? WHERE employee_id=? AND check_out_time IS NULL AND work_date >= DATE_SUB(CURDATE(), INTERVAL 1 DAY) ORDER BY work_date DESC, id DESC LIMIT 1");
    $stmt->execute([$now, $emp]);
    if ($stmt->rowCount() === 0) err("Chua check-in hom nay hoac da check-out roi", 409);
  }
  out(["check_out" => $now]);
}
if ($a === "history" && $m === "GET") {
  $me = require_login($pdo);
  $emp = $_GET["employee_id"] ?? $me["id"];
  if ((int)$emp !== (int)$me["id"] && !in_array($me["role"], ["admin", "manager"], true)) err("Khong co quyen xem lich su nguoi khac", 403);
  // Manager chi xem duoc lich su cham cong cua nhan vien phong minh.
  if ($me["role"] === "manager" && (int)$emp !== (int)$me["id"]) {
    $chk = $pdo->prepare("SELECT department_id FROM employees WHERE id=? LIMIT 1");
    $chk->execute([(int)$emp]);
    $t = $chk->fetch(PDO::FETCH_ASSOC);
    if (!$t || (int)$t["department_id"] !== (int)$me["department_id"]) err("Chi xem duoc nhan vien phong ban minh", 403);
  }
  $month = $_GET["month"] ?? date("m"); $year = $_GET["year"] ?? date("Y");
  $stmt = $pdo->prepare("SELECT * FROM attendances WHERE employee_id=? AND MONTH(work_date)=? AND YEAR(work_date)=? ORDER BY work_date DESC");
  $stmt->execute([$emp, $month, $year]);
  out($stmt->fetchAll(PDO::FETCH_ASSOC));
}
if ($a === "report" && $m === "GET") {
  $me = require_role($pdo, ["admin", "manager"]);
  $date = $_GET["date"] ?? date("Y-m-d");
  // Bao cao cham cong: admin xem tat ca, sep phong chi xem phong minh.
  if ($me["role"] === "manager") {
    $stmt = $pdo->prepare("SELECT a.*, e.full_name FROM attendances a JOIN employees e ON a.employee_id=e.id WHERE a.work_date=? AND e.department_id=? ORDER BY a.id DESC");
    $stmt->execute([$date, (int)$me["department_id"]]);
  } else {
    $stmt = $pdo->prepare("SELECT a.*, e.full_name FROM attendances a JOIN employees e ON a.employee_id=e.id WHERE a.work_date=? ORDER BY a.id DESC");
    $stmt->execute([$date]);
  }
  out($stmt->fetchAll(PDO::FETCH_ASSOC));
}
if ($a === "today" && $m === "GET") {
  // Trang thai cham cong HOM NAY cua chinh nguoi goi (cho the cham cong o Trang chu).
  $me = require_login($pdo);
  $stmt = $pdo->prepare("SELECT check_in_time, check_out_time, status FROM attendances WHERE employee_id=? AND work_date=? LIMIT 1");
  $stmt->execute([(int)$me["id"], date("Y-m-d")]);
  $row = $stmt->fetch(PDO::FETCH_ASSOC);
  if (!$row) out(["checked_in" => false, "checked_out" => false, "check_in" => null, "check_out" => null, "status" => null]);
  out(["checked_in" => true, "checked_out" => $row["check_out_time"] !== null,
       "check_in" => $row["check_in_time"], "check_out" => $row["check_out_time"], "status" => $row["status"]]);
}
err("Unknown attendance action");
?>
