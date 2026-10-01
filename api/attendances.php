<?php
require_once __DIR__ . "/../employee-api/db.php";
$a = $_GET["action"] ?? ""; $m = $_SERVER["REQUEST_METHOD"];

if ($a === "checkin" && $m === "POST") {
  $b = body(); $emp = $b["employee_id"] ?? 0;
  if (!$emp) err("Thieu employee_id");
  $today = date("Y-m-d"); $now = date("Y-m-d H:i:s");
  $late = (strtotime(date("H:i")) > strtotime("08:15")) ? "late" : "present";
  try {
    $pdo->prepare("INSERT INTO attendances(employee_id,work_date,check_in_time,status) VALUES(?,?,?,?)")->execute([$emp, $today, $now, $late]);
    out(["check_in" => $now, "status" => $late]);
  } catch (Exception $e) { err("Hom nay da check-in roi", 409); }
}
if ($a === "checkout" && $m === "POST") {
  $b = body(); $emp = $b["employee_id"] ?? 0; $today = date("Y-m-d"); $now = date("Y-m-d H:i:s");
  if (!$emp) err("Thieu employee_id");
  $pdo->prepare("UPDATE attendances SET check_out_time=? WHERE employee_id=? AND work_date=?")->execute([$now, $emp, $today]);
  out(["check_out" => $now]);
}
if ($a === "history" && $m === "GET") {
  $emp = $_GET["employee_id"] ?? 0; $month = $_GET["month"] ?? date("m"); $year = $_GET["year"] ?? date("Y");
  $stmt = $pdo->prepare("SELECT * FROM attendances WHERE employee_id=? AND MONTH(work_date)=? AND YEAR(work_date)=? ORDER BY work_date DESC");
  $stmt->execute([$emp, $month, $year]);
  out($stmt->fetchAll(PDO::FETCH_ASSOC));
}
if ($a === "report" && $m === "GET") {
  $date = $_GET["date"] ?? date("Y-m-d");
  $stmt = $pdo->prepare("SELECT a.*, e.full_name FROM attendances a JOIN employees e ON a.employee_id=e.id WHERE a.work_date=? ORDER BY a.id DESC");
  $stmt->execute([$date]);
  out($stmt->fetchAll(PDO::FETCH_ASSOC));
}
err("Unknown attendance action");
?>
