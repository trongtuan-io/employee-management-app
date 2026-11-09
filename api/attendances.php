<?php
require_once __DIR__ . "/db.php";
require_once __DIR__ . "/auth_guard.php";
require_once __DIR__ . "/salary_calc.php";
$a = $_GET["action"] ?? ""; $m = $_SERVER["REQUEST_METHOD"];

// ===== GPS cham cong (2026-11-09) =====
// Toa do van phong dung de doi chieu: DOI 2 hang so nay theo dia diem that
// cua cong ty. Cham cong xa hon OFFICE_RADIUS_M chi bi GAN CO "ngoai van
// phong" de quan ly doi chieu, KHONG chan cham cong (tranh dung viec khi GPS
// lech hoac nhan vien lam viec ngoai hien truong).
define("OFFICE_LAT", 21.0285000);
define("OFFICE_LNG", 105.8542000);
define("OFFICE_RADIUS_M", 300);

function att_ensure_columns(PDO $pdo) {
  static $done = false;
  if ($done) return;
  $done = true;
  $cols = [
    "check_in_lat" => "DECIMAL(10,7) NULL",
    "check_in_lng" => "DECIMAL(10,7) NULL",
    "check_in_remote" => "TINYINT NULL",
    "check_out_lat" => "DECIMAL(10,7) NULL",
    "check_out_lng" => "DECIMAL(10,7) NULL",
    "check_out_remote" => "TINYINT NULL",
  ];
  $stmt = $pdo->prepare("SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'attendances'");
  $stmt->execute();
  $have = array_column($stmt->fetchAll(PDO::FETCH_ASSOC), "COLUMN_NAME");
  foreach ($cols as $name => $def) {
    if (!in_array($name, $have, true)) $pdo->exec("ALTER TABLE attendances ADD COLUMN `$name` $def");
  }
}

function att_distance_m($lat1, $lng1, $lat2, $lng2) {
  $r = 6371000;
  $p1 = deg2rad((float)$lat1); $p2 = deg2rad((float)$lat2);
  $dp = deg2rad((float)$lat2 - (float)$lat1);
  $dl = deg2rad((float)$lng2 - (float)$lng1);
  $h = sin($dp / 2) * sin($dp / 2) + cos($p1) * cos($p2) * sin($dl / 2) * sin($dl / 2);
  return 2 * $r * asin(min(1, sqrt($h)));
}

// Tra ve [lat, lng, remote]: remote = null khi client khong gui toa do.
function att_gps_from_body($b) {
  $lat = isset($b["lat"]) && is_numeric($b["lat"]) ? (float)$b["lat"] : null;
  $lng = isset($b["lng"]) && is_numeric($b["lng"]) ? (float)$b["lng"] : null;
  if ($lat === null || $lng === null || $lat < -90 || $lat > 90 || $lng < -180 || $lng > 180) return [null, null, null];
  $remote = att_distance_m($lat, $lng, OFFICE_LAT, OFFICE_LNG) > OFFICE_RADIUS_M ? 1 : 0;
  return [$lat, $lng, $remote];
}

if ($a === "checkin" && $m === "POST") {
  $me = require_login($pdo);
  $b = body(); $emp = $b["employee_id"] ?? $me["id"]; // mac dinh la chinh nguoi dang nhap
  if ((int)$emp !== (int)$me["id"]) err("Chi duoc cham cong cho chinh minh", 403);
  att_ensure_columns($pdo);
  [$gLat, $gLng, $gRemote] = att_gps_from_body($b);
  $today = date("Y-m-d"); $now = date("Y-m-d H:i:s");
  $late = (strtotime(date("H:i")) > strtotime("08:15")) ? "late" : "present";
  try {
    if ($gLat === null) {
      $pdo->prepare("INSERT INTO attendances(employee_id,work_date,check_in_time,status) VALUES(?,?,?,?)")->execute([$emp, $today, $now, $late]);
    } else {
      $pdo->prepare("INSERT INTO attendances(employee_id,work_date,check_in_time,status,check_in_lat,check_in_lng,check_in_remote) VALUES(?,?,?,?,?,?,?)")
        ->execute([$emp, $today, $now, $late, $gLat, $gLng, $gRemote]);
    }
    out(["check_in" => $now, "status" => $late, "remote" => $gRemote]);
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
  att_ensure_columns($pdo);
  [$gLat, $gLng, $gRemote] = att_gps_from_body($b);
  // Chi check-out duoc khi da check-in va chua check-out; kiem tra so dong cap nhat thay vi bao success mu.
  if ($gLat === null) {
    $stmt = $pdo->prepare("UPDATE attendances SET check_out_time=? WHERE employee_id=? AND work_date=? AND check_out_time IS NULL");
    $stmt->execute([$now, $emp, $today]);
    if ($stmt->rowCount() === 0) {
      $stmt = $pdo->prepare("UPDATE attendances SET check_out_time=? WHERE employee_id=? AND check_out_time IS NULL AND work_date >= DATE_SUB(CURDATE(), INTERVAL 1 DAY) ORDER BY work_date DESC, id DESC LIMIT 1");
      $stmt->execute([$now, $emp]);
    }
  } else {
    $stmt = $pdo->prepare("UPDATE attendances SET check_out_time=?, check_out_lat=?, check_out_lng=?, check_out_remote=? WHERE employee_id=? AND work_date=? AND check_out_time IS NULL");
    $stmt->execute([$now, $gLat, $gLng, $gRemote, $emp, $today]);
    if ($stmt->rowCount() === 0) {
      $stmt = $pdo->prepare("UPDATE attendances SET check_out_time=?, check_out_lat=?, check_out_lng=?, check_out_remote=? WHERE employee_id=? AND check_out_time IS NULL AND work_date >= DATE_SUB(CURDATE(), INTERVAL 1 DAY) ORDER BY work_date DESC, id DESC LIMIT 1");
      $stmt->execute([$now, $gLat, $gLng, $gRemote, $emp]);
    }
  }
  if ($stmt->rowCount() === 0) err("Chua check-in hom nay hoac da check-out roi", 409);
  out(["check_out" => $now, "remote" => $gRemote]);
}
if ($a === "history" && $m === "GET") {
  $me = require_login($pdo);
  $emp = $_GET["employee_id"] ?? $me["id"];
  if ((int)$emp !== (int)$me["id"] && !in_array($me["role"], ["admin", "manager"], true)
      && !is_hr($me) && !is_accounting($me)) err("Khong co quyen xem lich su nguoi khac", 403);
  // Manager phong nghiep vu chi xem duoc lich su cham cong cua nhan vien phong minh.
  // Phong HR (tong hop cong) va Ke toan (doi chieu luong) xem duoc toan cong ty.
  if ($me["role"] === "manager" && !is_hr($me) && !is_accounting($me) && (int)$emp !== (int)$me["id"]) {
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
  // Bao cao cham cong: admin/HR/Ke toan xem tat ca (HR tong hop, Ke toan doi chieu
  // luong - chi XEM, khong sua cong); sep phong nghiep vu chi xem phong minh;
  // nhan vien thuong khong xem.
  $me = require_login($pdo);
  $isGlobal = $me["role"] === "admin" || is_hr($me) || is_accounting($me);
  if (!$isGlobal && $me["role"] !== "manager") err("Khong co quyen xem bao cao cham cong", 403);
  $date = $_GET["date"] ?? date("Y-m-d");
  if ($me["role"] === "manager" && !$isGlobal) {
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
  att_ensure_columns($pdo);
  $stmt = $pdo->prepare("SELECT check_in_time, check_out_time, status, check_in_remote, check_out_remote FROM attendances WHERE employee_id=? AND work_date=? LIMIT 1");
  $stmt->execute([(int)$me["id"], date("Y-m-d")]);
  $row = $stmt->fetch(PDO::FETCH_ASSOC);
  if (!$row) out(["checked_in" => false, "checked_out" => false, "check_in" => null, "check_out" => null, "status" => null,
                  "check_in_remote" => null, "check_out_remote" => null]);
  out(["checked_in" => true, "checked_out" => $row["check_out_time"] !== null,
       "check_in" => $row["check_in_time"], "check_out" => $row["check_out_time"], "status" => $row["status"],
       "check_in_remote" => $row["check_in_remote"] === null ? null : (int)$row["check_in_remote"],
       "check_out_remote" => $row["check_out_remote"] === null ? null : (int)$row["check_out_remote"]]);
}
// ===== Giai trinh quen cham cong (2026-11-09) =====
// Nhan vien tu tao don giai trinh cho ngay quen cham cong; sep phong/HR duyet
// nhu don nghi. Duyet xong thi ghi bo sung thang vao bang attendances.
function expl_ensure_table(PDO $pdo) {
  static $done = false;
  if ($done) return;
  $done = true;
  $pdo->exec("CREATE TABLE IF NOT EXISTS attendance_explanations (
    id INT AUTO_INCREMENT PRIMARY KEY,
    employee_id INT NOT NULL,
    work_date DATE NOT NULL,
    kind VARCHAR(10) NOT NULL,
    proposed_in TIME NULL,
    proposed_out TIME NULL,
    reason VARCHAR(300) NULL,
    status VARCHAR(12) NOT NULL DEFAULT 'pending',
    approved_by INT NULL,
    processed_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_emp_date (employee_id, work_date),
    INDEX idx_status (status)
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
}

function expl_kind_label($k) {
  switch ($k) {
    case "checkin": return "Quên chấm công vào";
    case "checkout": return "Quên chấm công ra";
    default: return "Quên cả vào và ra";
  }
}

if ($a === "explanation-create" && $m === "POST") {
  $me = require_login($pdo);
  expl_ensure_table($pdo);
  att_ensure_columns($pdo);
  $b = body();
  $wd = is_string($b["work_date"] ?? null) ? $b["work_date"] : "";
  $dchk = DateTimeImmutable::createFromFormat("!Y-m-d", $wd);
  if (!$dchk || $dchk->format("Y-m-d") !== $wd) err("Ngay giai trinh phai co dang YYYY-MM-DD hop le", 422);
  if ($wd > date("Y-m-d")) err("Khong giai trinh duoc cho ngay tuong lai", 422);
  $kind = is_string($b["kind"] ?? null) && in_array($b["kind"], ["checkin", "checkout", "both"], true) ? $b["kind"] : "";
  if ($kind === "") err("kind chi duoc la checkin/checkout/both", 422);
  $pIn = null; $pOut = null;
  $timeOk = function ($v) { return is_string($v) && preg_match("/^([01]\d|2[0-3]):[0-5]\d$/", $v) ? $v . ":00" : null; };
  if ($kind === "checkin" || $kind === "both") { $pIn = $timeOk($b["proposed_in"] ?? null); if ($pIn === null) err("Can gio vao de xuat dang HH:MM", 422); }
  if ($kind === "checkout" || $kind === "both") { $pOut = $timeOk($b["proposed_out"] ?? null); if ($pOut === null) err("Can gio ra de xuat dang HH:MM", 422); }
  if ($pIn !== null && $pOut !== null && $pOut <= $pIn) err("Gio ra phai sau gio vao", 422);
  // Chong trung: da co don giai trinh dang cho duyet cho ngay nay.
  $st = $pdo->prepare("SELECT COUNT(*) FROM attendance_explanations WHERE employee_id=? AND work_date=? AND status='pending'");
  $st->execute([(int)$me["id"], $wd]);
  if ((int)$st->fetchColumn() > 0) err("Da co don giai trinh dang cho duyet cho ngay nay", 409);
  // Phan da co cham cong that thi khong giai trinh lai.
  $st = $pdo->prepare("SELECT check_in_time, check_out_time FROM attendances WHERE employee_id=? AND work_date=? LIMIT 1");
  $st->execute([(int)$me["id"], $wd]);
  $att = $st->fetch(PDO::FETCH_ASSOC);
  if ($att) {
    if (($kind === "checkin" || $kind === "both") && !empty($att["check_in_time"])) err("Ngay nay da co gio cham cong vao", 422);
    if (($kind === "checkout" || $kind === "both") && !empty($att["check_out_time"])) err("Ngay nay da co gio cham cong ra", 422);
  } elseif ($kind === "checkout") {
    err("Ngay nay chua co gio cham cong vao nen khong giai trinh rieng gio ra duoc — chon 'Quên cả vào và ra'", 422);
  }
  $reason = is_string($b["reason"] ?? null) ? mb_substr(trim($b["reason"]), 0, 300) : "";
  $pdo->prepare("INSERT INTO attendance_explanations(employee_id,work_date,kind,proposed_in,proposed_out,reason,status) VALUES(?,?,?,?,?,?,'pending')")
    ->execute([(int)$me["id"], $wd, $kind, $pIn, $pOut, $reason]);
  $newId = (int)$pdo->lastInsertId();
  $nBody = $me["full_name"] . " gửi giải trình " . expl_kind_label($kind) . " cho ngày " . $wd
    . ($pIn ? ", giờ vào đề xuất " . substr($pIn, 0, 5) : "") . ($pOut ? ", giờ ra đề xuất " . substr($pOut, 0, 5) : "")
    . ($reason !== "" ? ". Lý do: " . $reason : "");
  notify_approvers($pdo, (int)$me["id"], "expl_new", "Đơn giải trình chấm công mới", $nBody, $newId);
  out(["id" => $newId, "status" => "pending"]);
}
if ($a === "explanation-list" && $m === "GET") {
  $me = require_login($pdo);
  expl_ensure_table($pdo);
  $emp = $_GET["employee_id"] ?? "";
  if ($emp === "" && $me["role"] === "staff") $emp = (string)(int)$me["id"];
  if ($emp !== "") {
    if ((int)$emp !== (int)$me["id"] && !in_array($me["role"], ["admin", "manager"], true) && !is_hr($me)) {
      err("Khong co quyen xem giai trinh cua nguoi khac", 403);
    }
    if ($me["role"] === "manager" && !is_hr($me) && (int)$emp !== (int)$me["id"]) {
      $st = $pdo->prepare("SELECT department_id FROM employees WHERE id=?");
      $st->execute([(int)$emp]);
      $tgt = $st->fetch(PDO::FETCH_ASSOC);
      if (!$tgt || (int)$tgt["department_id"] !== (int)$me["department_id"]) err("Chi xem duoc nhan vien phong minh", 403);
    }
    $stmt = $pdo->prepare("SELECT * FROM attendance_explanations WHERE employee_id=? ORDER BY id DESC");
    $stmt->execute([(int)$emp]);
  } else {
    if (!in_array($me["role"], ["admin", "manager"], true) && !is_hr($me)) err("Khong co quyen xem hang cho duyet", 403);
    $cols = "x.*, e.full_name, d.name AS dept_name, ap.full_name AS approver_name";
    $joins = "FROM attendance_explanations x JOIN employees e ON e.id=x.employee_id LEFT JOIN departments d ON d.id=e.department_id LEFT JOIN employees ap ON ap.id=x.approved_by";
    $order = "ORDER BY (x.status='pending') DESC, x.id DESC";
    if ($me["role"] === "manager" && !is_hr($me)) {
      $stmt = $pdo->prepare("SELECT $cols $joins WHERE e.department_id=? $order");
      $stmt->execute([(int)$me["department_id"]]);
    } else {
      $stmt = $pdo->query("SELECT $cols $joins $order");
    }
  }
  out($stmt->fetchAll(PDO::FETCH_ASSOC));
}
if ($a === "explanation-approve" && $m === "POST") {
  $me = require_role($pdo, ["admin", "manager"]);
  expl_ensure_table($pdo);
  att_ensure_columns($pdo);
  $b = body();
  if (empty($b["id"]) || empty($b["status"])) err("Thieu id/status");
  if (!in_array($b["status"], ["approved", "rejected"], true)) err("status chi duoc la approved/rejected");
  $stmt = $pdo->prepare("SELECT x.*, e.department_id FROM attendance_explanations x JOIN employees e ON e.id=x.employee_id WHERE x.id=? LIMIT 1");
  $stmt->execute([(int)$b["id"]]);
  $x = $stmt->fetch(PDO::FETCH_ASSOC);
  if (!$x) err("Khong tim thay don giai trinh", 404);
  if ($x["status"] !== "pending") err("Don nay da duoc xu ly roi", 409);
  if ($me["role"] === "manager" && (int)$x["department_id"] !== (int)$me["department_id"]) {
    err("Chi duyet duoc giai trinh cua nhan vien phong ban minh", 403);
  }
  $upd = $pdo->prepare("UPDATE attendance_explanations SET status=?, approved_by=?, processed_at=NOW() WHERE id=? AND status='pending'");
  $upd->execute([$b["status"], (int)$me["id"], (int)$b["id"]]);
  if ($upd->rowCount() === 0) err("Don nay da duoc xu ly roi", 409);
  $applied = false;
  if ($b["status"] === "approved") {
    // Ap gio de xuat vao bang cham cong: chua co dong thi tao moi, co roi thi
    // chi dien phan dang thieu (khong ghi de du lieu cham cong that).
    $empId = (int)$x["employee_id"]; $wd = $x["work_date"];
    $st = $pdo->prepare("SELECT id, check_in_time, check_out_time FROM attendances WHERE employee_id=? AND work_date=? LIMIT 1");
    $st->execute([$empId, $wd]);
    $att = $st->fetch(PDO::FETCH_ASSOC);
    $inDt = $x["proposed_in"] ? $wd . " " . $x["proposed_in"] : null;
    $outDt = $x["proposed_out"] ? $wd . " " . $x["proposed_out"] : null;
    if (!$att) {
      $status = ($x["proposed_in"] && strtotime($x["proposed_in"]) > strtotime("08:15:00")) ? "late" : "present";
      if ($inDt !== null) {
        $pdo->prepare("INSERT INTO attendances(employee_id,work_date,check_in_time,check_out_time,status) VALUES(?,?,?,?,?)")
          ->execute([$empId, $wd, $inDt, $outDt, $status]);
        $applied = true;
      }
    } else {
      if ($inDt !== null && empty($att["check_in_time"])) {
        $status = strtotime($x["proposed_in"]) > strtotime("08:15:00") ? "late" : "present";
        $pdo->prepare("UPDATE attendances SET check_in_time=?, status=? WHERE id=?")->execute([$inDt, $status, (int)$att["id"]]);
        $applied = true;
      }
      if ($outDt !== null && empty($att["check_out_time"])) {
        $pdo->prepare("UPDATE attendances SET check_out_time=? WHERE id=?")->execute([$outDt, (int)$att["id"]]);
        $applied = true;
      }
    }
  }
  $ok = $b["status"] === "approved";
  notify_push($pdo, (int)$x["employee_id"], $ok ? "expl_approved" : "expl_rejected",
    $ok ? "Giải trình chấm công đã được duyệt" : "Giải trình chấm công bị từ chối",
    "Giải trình " . expl_kind_label($x["kind"]) . " ngày " . $x["work_date"] . " đã được " . $me["full_name"]
      . ($ok ? " duyệt" . ($applied ? " và đã bổ sung vào bảng công." : ".") : " từ chối."),
    (int)$b["id"]);
  out(["id" => (int)$b["id"], "status" => $b["status"], "applied" => $applied]);
}
// Xuat bang cong 1 thang ra CSV (mo Excel duoc): moi nhan vien 1 dong tong hop.
// Pham vi nhu report: admin/HR/Ke toan toan cong ty, manager chi phong minh.
if ($a === "attendance-export" && $m === "GET") {
  $me = require_login($pdo);
  att_ensure_columns($pdo);
  $isGlobal = $me["role"] === "admin" || is_hr($me) || is_accounting($me);
  if (!$isGlobal && $me["role"] !== "manager") err("Khong co quyen xuat bang cong", 403);
  $month = isset($_GET["month"]) && is_numeric($_GET["month"]) ? (int)$_GET["month"] : (int)date("n");
  $year = isset($_GET["year"]) && is_numeric($_GET["year"]) ? (int)$_GET["year"] : (int)date("Y");
  if ($month < 1 || $month > 12 || $year < 2000 || $year > 2100) err("Thang/nam khong hop le", 422);
  $ms = sprintf("%04d-%02d-01", $year, $month);
  $me2 = date("Y-m-t", strtotime($ms));
  if ($isGlobal) {
    $emps = $pdo->query("SELECT e.id, e.full_name, d.name AS dept_name FROM employees e LEFT JOIN departments d ON d.id=e.department_id WHERE e.status=1 ORDER BY d.name, e.full_name")->fetchAll(PDO::FETCH_ASSOC);
  } else {
    $st = $pdo->prepare("SELECT e.id, e.full_name, d.name AS dept_name FROM employees e LEFT JOIN departments d ON d.id=e.department_id WHERE e.status=1 AND e.department_id=? ORDER BY e.full_name");
    $st->execute([(int)$me["department_id"]]);
    $emps = $st->fetchAll(PDO::FETCH_ASSOC);
  }
  $attStmt = $pdo->prepare("SELECT COUNT(*) FROM attendances WHERE employee_id=? AND work_date BETWEEN ? AND ? AND check_in_time IS NOT NULL");
  $lateStmt = $pdo->prepare("SELECT COUNT(*) FROM attendances WHERE employee_id=? AND work_date BETWEEN ? AND ? AND status='late'");
  $lvStmt = $pdo->prepare("SELECT start_date, end_date, leave_type FROM leave_requests WHERE employee_id=? AND status='approved' AND start_date<=? AND end_date>=?");
  $num = fn($v) => number_format((float)$v, 0, ",", "");
  $lines = [];
  $lines[] = implode(";", ["STT", "Ho ten", "Phong ban", "Ngay co mat", "Di muon (lan)", "Nghi phep co luong (ngay)", "Nghi khong luong (ngay)", "Tong ngay cong"]);
  $i = 0; $tPresent = 0; $tLate = 0; $tPaid = 0; $tUnpaid = 0; $tWork = 0;
  foreach ($emps as $e) {
    $i++;
    $attStmt->execute([(int)$e["id"], $ms, $me2]); $present = (int)$attStmt->fetchColumn();
    $lateStmt->execute([(int)$e["id"], $ms, $me2]); $late = (int)$lateStmt->fetchColumn();
    $lvStmt->execute([(int)$e["id"], $me2, $ms]);
    $paidLv = 0; $unpaidLv = 0;
    foreach ($lvStmt->fetchAll(PDO::FETCH_ASSOC) as $lv) {
      $d = salary_leave_workdays($lv["start_date"], $lv["end_date"], $ms, $me2);
      if ($lv["leave_type"] === "unpaid") $unpaidLv += $d; else $paidLv += $d;
    }
    $work = $present + $paidLv;
    $lines[] = implode(";", [$i, $e["full_name"], $e["dept_name"] ?? "", $present, $late, $paidLv, $unpaidLv, $work]);
    $tPresent += $present; $tLate += $late; $tPaid += $paidLv; $tUnpaid += $unpaidLv; $tWork += $work;
  }
  $lines[] = implode(";", ["", "TONG", "", $tPresent, $tLate, $tPaid, $tUnpaid, $tWork]);
  header("Content-Type: text/csv; charset=UTF-8");
  header("Content-Disposition: attachment; filename=\"bang_cong_" . sprintf("%02d_%d", $month, $year) . ".csv\"");
  echo "\xEF\xBB\xBF" . implode("\r\n", $lines);
  exit;
}
err("Unknown attendance action");
?>
