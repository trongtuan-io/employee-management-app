<?php
require_once __DIR__ . "/db.php";
require_once __DIR__ . "/auth_guard.php";
require_once __DIR__ . "/salary_calc.php";
$a = $_GET["action"] ?? ""; $m = $_SERVER["REQUEST_METHOD"];

// Cot processed_at: thoi diem don duoc duyet/tu choi — phuc vu truy vet o tab
// Cho duyet (don xu ly xong van con luu lai lam chung cu, khong bien mat).
// Tu bo sung cot neu DB chua co (nhu cach salary_ensure_columns lam).
function leave_ensure_columns(PDO $pdo) {
  static $done = false;
  if ($done) return;
  $done = true;
  $stmt = $pdo->prepare("SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'leave_requests'");
  $stmt->execute();
  $have = array_column($stmt->fetchAll(PDO::FETCH_ASSOC), "COLUMN_NAME");
  if (!in_array("processed_at", $have, true)) {
    $pdo->exec("ALTER TABLE leave_requests ADD COLUMN processed_at DATETIME NULL");
  }
}

if ($a === "leave-create" && $m === "POST") {
  $me = require_login($pdo);
  $b = body();
  if (empty($b["employee_id"])) $b["employee_id"] = $me["id"];
  if ((int)$b["employee_id"] !== (int)$me["id"] && !in_array($me["role"], ["admin", "manager"], true)) err("Chi duoc tao don nghi cho chinh minh", 403);
  if (empty($b["employee_id"]) || empty($b["start_date"]) || empty($b["end_date"])) err("Thieu employee_id/start_date/end_date");
  // Sep phong tao don ho thi chi cho nhan vien phong minh.
  // Truong phong HR tao don ho duoc cho moi phong (viec cua Nhan su).
  if ($me["role"] === "manager" && !is_hr_manager($me) && (int)$b["employee_id"] !== (int)$me["id"]) {
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
    if ((int)$emp !== (int)$me["id"] && !in_array($me["role"], ["admin", "manager"], true)
        && !is_hr($me)) err("Khong co quyen xem don cua nguoi khac", 403);
  } else {
    if (!in_array($me["role"], ["admin", "manager"], true) && !is_hr($me)) err("Khong co quyen xem danh sach don cho duyet", 403);
  }
  if ($emp !== "") {
    // Sep phong nghiep vu xem don theo tung nhan vien thi cung chi trong phong minh.
    // Phong HR xem duoc don cua moi phong (theo doi/tong hop, duyet van theo phong).
    if ($me["role"] === "manager" && !is_hr($me) && (int)$emp !== (int)$me["id"]) {
      $st = $pdo->prepare("SELECT department_id FROM employees WHERE id=?");
      $st->execute([(int)$emp]);
      $tgt = $st->fetch(PDO::FETCH_ASSOC);
      if (!$tgt || (int)$tgt["department_id"] !== (int)$me["department_id"]) err("Chi xem duoc don cua nhan vien phong minh", 403);
    }
    $stmt = $pdo->prepare("SELECT * FROM leave_requests WHERE employee_id=? ORDER BY id DESC"); $stmt->execute([$emp]);
  }
  else {
    // Khu vuc duyet phep: admin thay tat ca, phong HR thay tat ca (chi de theo
    // doi - duyet thi van chi don phong HR), sep phong nghiep vu chi thay phong
    // minh. Tra CA don da xu ly (approved/rejected) kem nguoi duyet + thoi diem
    // xu ly de truy vet: don duyet xong KHONG bien mat khoi danh sach nua.
    // Don dang cho duyet xep len dau.
    leave_ensure_columns($pdo);
    $cols = "l.*, e.full_name, d.name AS dept_name, a.full_name AS approver_name";
    $joins = "FROM leave_requests l JOIN employees e ON l.employee_id=e.id LEFT JOIN departments d ON d.id=e.department_id LEFT JOIN employees a ON a.id=l.approved_by";
    $order = "ORDER BY (l.status='pending') DESC, l.id DESC";
    if ($me["role"] === "manager" && !is_hr($me)) {
      $stmt = $pdo->prepare("SELECT $cols $joins WHERE e.department_id=? $order");
      $stmt->execute([(int)$me["department_id"]]);
    } else {
      $stmt = $pdo->query("SELECT $cols $joins $order");
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
  // Ghi luon thoi diem xu ly (processed_at) lam chung cu truy vet.
  leave_ensure_columns($pdo);
  $upd = $pdo->prepare("UPDATE leave_requests SET status=?, approved_by=?, processed_at=NOW() WHERE id=? AND status='pending'");
  $upd->execute([$b["status"], $me["id"], (int)$b["id"]]);
  if ($upd->rowCount() === 0) err("Don nay da duoc xu ly roi", 409);
  out(["id" => (int)$b["id"], "status" => $b["status"]]);
}
if ($a === "salary-list" && $m === "GET") {
  $me = require_login($pdo);
  employee_salary_ensure_columns($pdo);
  // Khong truyen employee_id thi mac dinh xem cua chinh minh.
  $empId = (int)($_GET["employee_id"] ?? $me["id"]);
  // Xem luong NGUOI KHAC: chi Admin + phong Ke toan (de soan nhap). Moi nhan
  // vien/truong phong con lai chi xem duoc bang luong CUA CHINH MINH.
  if ($empId !== (int)$me["id"] && !can_view_others_salary($me)) err("Khong co quyen xem luong nguoi khac", 403);
  salary_ensure_columns($pdo);
  // Xem luong CUA MINH: nguoi khong thuoc nhom nghiep vu luong chi thay thang
  // DA CHOT. Ban nhap (finalized=0) chi Admin/phong Ke toan thay.
  $onlyFinalized = ($empId === (int)$me["id"] && !can_view_others_salary($me));
  // full_base_salary = luong co ban GOC trong ho so (cot base_salary cua bang
  // salaries la luong DA NHAN theo ngay cong) — man chi tiet can ca hai so.
  $sql = "SELECT s.*, e.full_name, e.base_salary AS full_base_salary, e.insurance_salary, e.dependents FROM salaries s JOIN employees e ON s.employee_id=e.id WHERE s.employee_id=? AND s.year=?";
  if ($onlyFinalized) $sql .= " AND s.finalized=1";
  $sql .= " ORDER BY s.month DESC";
  $stmt = $pdo->prepare($sql);
  $stmt->execute([$empId, $_GET["year"] ?? date("Y")]);
  out($stmt->fetchAll(PDO::FETCH_ASSOC));
}
// Bang luong TONG HOP toan cong ty theo thang: moi nhan vien dang hoat dong
// mot dong (kem dong luong cua thang neu da tinh) — Ke toan mo ra xem het
// mot luot, khoi chon tung nguoi. Chi Admin + phong Ke toan (nhom soan nhap).
if ($a === "salary-overview" && $m === "GET") {
  $me = require_payroll($pdo, false);
  salary_ensure_columns($pdo);
  $month = (int)($_GET["month"] ?? date("n")); $year = (int)($_GET["year"] ?? date("Y"));
  if ($month < 1 || $month > 12 || $year < 2000 || $year > 2100) err("Thang/nam khong hop le", 422);
  $stmt = $pdo->prepare("SELECT e.id AS employee_id, e.full_name, d.name AS dept_name, e.base_salary AS full_base_salary, s.id AS salary_id, s.base_salary, s.allowance, s.overtime_pay, s.bonus, s.deduction, s.insurance_base, s.insurance_amount, s.tax_amount, s.net_salary, s.work_days, s.standard_days, s.finalized, s.paid, e.insurance_salary, e.dependents FROM employees e LEFT JOIN departments d ON d.id=e.department_id LEFT JOIN salaries s ON s.employee_id=e.id AND s.month=? AND s.year=? WHERE e.status=1 ORDER BY d.name, e.full_name");
  $stmt->execute([$month, $year]);
  out($stmt->fetchAll(PDO::FETCH_ASSOC));
}
// Tinh luong TU DONG theo ngay cong cho ca thang: Admin + phong Ke toan
// (soan nhap), pham vi TOAN CONG TY.
// Sinh bang luong NHAP: dong da CHOT (finalized=1) khong bi ghi de; phu cap/tang ca/
// thuong/khau tru cua dong cu duoc GIU NGUYEN, chi tinh lai phan luong theo cong va thuc linh.
if ($a === "salary-generate" && $m === "POST") {
  $me = require_payroll($pdo, false);
  salary_ensure_columns($pdo);
  employee_salary_ensure_columns($pdo);
  $b = body();
  $month = (int)($b["month"] ?? date("n")); $year = (int)($b["year"] ?? date("Y"));
  if ($month < 1 || $month > 12 || $year < 2000 || $year > 2100) err("Thang/nam khong hop le", 422);
  $std = isset($b["standard_days"]) && is_numeric($b["standard_days"]) ? (int)$b["standard_days"] : salary_standard_days($year, $month);
  if ($std <= 0 || $std > 31) err("Ngay cong chuan khong hop le", 422);
  $monthStart = sprintf("%04d-%02d-01", $year, $month);
  $monthEnd = date("Y-m-t", strtotime($monthStart));
  $emps = $pdo->query("SELECT id, full_name, base_salary, department_id, insurance_salary, dependents FROM employees WHERE status=1 ORDER BY id");
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
    $insBase = salary_insurance_base($e["base_salary"], $e["insurance_salary"]);
    $findStmt->execute([$empId, $month, $year]);
    $old = $findStmt->fetch(PDO::FETCH_ASSOC);
    if ($old && (int)$old["finalized"] === 1) { $skipped++; continue; }
    if ($old) {
      [$ins, $tax, $net] = salary_full_calc($earned, $old["allowance"], $old["overtime_pay"], $old["bonus"], $old["deduction"], $insBase, $paidDays, $e["dependents"]);
      $pdo->prepare("UPDATE salaries SET base_salary=?, work_days=?, standard_days=?, insurance_base=?, insurance_amount=?, tax_amount=?, net_salary=? WHERE id=?")
        ->execute([$earned, $paidDays, $std, $insBase, $ins, $tax, $net, (int)$old["id"]]);
      if (abs((float)$old["base_salary"] - $earned) >= 1) {
        salary_audit($pdo, (int)$old["id"], $empId, (int)$me["id"], "base_salary", (float)$old["base_salary"], $earned);
      }
    } else {
      // Dong moi: phu cap khoi tao = tong phu cap CHINH SACH dang ap dung.
      $allowance = salary_policy_allowance($pdo, $e["department_id"]);
      [$ins, $tax, $net] = salary_full_calc($earned, $allowance, 0, 0, 0, $insBase, $paidDays, $e["dependents"]);
      $pdo->prepare("INSERT INTO salaries(employee_id,month,year,base_salary,allowance,overtime_pay,bonus,deduction,insurance_base,insurance_amount,tax_amount,net_salary,work_days,standard_days,finalized) VALUES(?,?,?, ?,?,0,0,0, ?,?,?, ?,?,?,0)")
        ->execute([$empId, $month, $year, $earned, $allowance, $insBase, $ins, $tax, $net, $paidDays, $std]);
      salary_audit($pdo, (int)$pdo->lastInsertId(), $empId, (int)$me["id"], "created", null, $net);
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
  $me = require_payroll($pdo, false);
  salary_ensure_columns($pdo);
  employee_salary_ensure_columns($pdo);
  $b = body();
  if (empty($b["id"]) || !is_numeric($b["id"])) err("Thieu id bang luong");
  $stmt = $pdo->prepare("SELECT s.*, e.department_id, e.base_salary AS full_base_salary, e.insurance_salary, e.dependents FROM salaries s JOIN employees e ON s.employee_id=e.id WHERE s.id=?");
  $stmt->execute([(int)$b["id"]]);
  $row = $stmt->fetch(PDO::FETCH_ASSOC);
  if (!$row) err("Khong tim thay bang luong", 404);
  if ((int)$row["finalized"] === 1) err("Bang luong da chot, khong sua duoc", 409);
  $changes = [];
  $fields = ["allowance", "overtime_pay", "bonus", "deduction"];
  foreach ($fields as $f) {
    if (isset($b[$f])) {
      if (!is_numeric($b[$f]) || (float)$b[$f] < 0) err("So tien $f khong hop le", 422);
      if (abs((float)$row[$f] - (float)$b[$f]) >= 0.5) $changes[] = [$f, (float)$row[$f], (float)$b[$f]];
      $row[$f] = (float)$b[$f];
    }
  }
  // Du lieu goc cho tinh BH/thue nam o ho so nhan vien: cho sua ngay tai day,
  // luu lai vao ho so de cac thang sau dung tiep.
  $insSalary = $row["insurance_salary"] !== null ? (float)$row["insurance_salary"] : null;
  if (array_key_exists("insurance_salary", $b)) {
    $newIns = ($b["insurance_salary"] === null || $b["insurance_salary"] === "") ? null : (float)$b["insurance_salary"];
    if ($newIns !== null && $newIns < 0) err("Luong dong BH khong hop le", 422);
    if ($newIns !== $insSalary) $changes[] = ["insurance_salary", $insSalary, $newIns];
    $insSalary = $newIns;
  }
  $dependents = (int)$row["dependents"];
  if (isset($b["dependents"])) {
    if (!is_numeric($b["dependents"]) || (int)$b["dependents"] < 0 || (int)$b["dependents"] > 20) err("So nguoi phu thuoc khong hop le", 422);
    if ((int)$b["dependents"] !== $dependents) $changes[] = ["dependents", $dependents, (int)$b["dependents"]];
    $dependents = (int)$b["dependents"];
  }
  if ($changes && (array_filter($changes, fn($c) => $c[0] === "insurance_salary" || $c[0] === "dependents"))) {
    $pdo->prepare("UPDATE employees SET insurance_salary=?, dependents=? WHERE id=?")
      ->execute([$insSalary, $dependents, (int)$row["employee_id"]]);
  }
  $insBase = salary_insurance_base($row["full_base_salary"], $insSalary);
  [$ins, $tax, $net] = salary_full_calc($row["base_salary"], $row["allowance"], $row["overtime_pay"], $row["bonus"], $row["deduction"], $insBase, $row["work_days"], $dependents);
  if (abs((float)$row["insurance_amount"] - $ins) >= 1) $changes[] = ["insurance_amount", (float)$row["insurance_amount"], $ins];
  if (abs((float)$row["tax_amount"] - $tax) >= 1) $changes[] = ["tax_amount", (float)$row["tax_amount"], $tax];
  $pdo->prepare("UPDATE salaries SET allowance=?, overtime_pay=?, bonus=?, deduction=?, insurance_base=?, insurance_amount=?, tax_amount=?, net_salary=? WHERE id=?")
    ->execute([$row["allowance"], $row["overtime_pay"], $row["bonus"], $row["deduction"], $insBase, $ins, $tax, $net, (int)$row["id"]]);
  foreach ($changes as [$f, $oldV, $newV]) {
    salary_audit($pdo, (int)$row["id"], (int)$row["employee_id"], (int)$me["id"], $f, $oldV, $newV);
  }
  out(["id" => (int)$row["id"], "insurance_amount" => $ins, "tax_amount" => $tax, "net_salary" => $net]);
}
// Chot bang luong: theo tung dong (id) hoac ca thang (month+year).
// CHI Admin + Ke toan TRUONG (manager phong ACC), pham vi toan cong ty.
if ($a === "salary-finalize" && $m === "POST") {
  $me = require_payroll($pdo, true);
  salary_ensure_columns($pdo);
  $b = body();
  if (!empty($b["id"]) && is_numeric($b["id"])) {
    $stmt = $pdo->prepare("SELECT s.employee_id, s.finalized FROM salaries s WHERE s.id=?");
    $stmt->execute([(int)$b["id"]]);
    $row = $stmt->fetch(PDO::FETCH_ASSOC);
    if (!$row) err("Khong tim thay bang luong", 404);
    $upd = $pdo->prepare("UPDATE salaries SET finalized=1 WHERE id=?");
    $upd->execute([(int)$b["id"]]);
    if ((int)$row["finalized"] === 0) salary_audit($pdo, (int)$b["id"], (int)$row["employee_id"], (int)$me["id"], "finalized", 0, 1);
    out(["id" => (int)$b["id"], "finalized" => 1]);
  }
  $month = (int)($b["month"] ?? 0); $year = (int)($b["year"] ?? 0);
  if ($month < 1 || $month > 12 || $year < 2000 || $year > 2100) err("Can id hoac month/year hop le", 422);
  $sel = $pdo->prepare("SELECT id, employee_id FROM salaries WHERE month=? AND year=? AND finalized=0");
  $sel->execute([$month, $year]);
  $pendingRows = $sel->fetchAll(PDO::FETCH_ASSOC);
  $upd = $pdo->prepare("UPDATE salaries SET finalized=1 WHERE month=? AND year=?");
  $upd->execute([$month, $year]);
  foreach ($pendingRows as $pr) salary_audit($pdo, (int)$pr["id"], (int)$pr["employee_id"], (int)$me["id"], "finalized", 0, 1);
  out(["month" => $month, "year" => $year, "finalized_count" => $upd->rowCount()]);
}
// Mo lai bang luong da chot (sai sot sau khi chot): CHI admin.
if ($a === "salary-unfinalize" && $m === "POST") {
  $me = require_role($pdo, ["admin"]);
  salary_ensure_columns($pdo);
  $b = body();
  if (empty($b["id"]) || !is_numeric($b["id"])) err("Thieu id bang luong");
  $sel = $pdo->prepare("SELECT employee_id FROM salaries WHERE id=?");
  $sel->execute([(int)$b["id"]]);
  $empOfSalary = (int)($sel->fetchColumn() ?: 0);
  $upd = $pdo->prepare("UPDATE salaries SET finalized=0 WHERE id=? AND finalized=1");
  $upd->execute([(int)$b["id"]]);
  if ($upd->rowCount() === 0) err("Khong tim thay bang luong da chot nay", 404);
  if ($empOfSalary > 0) salary_audit($pdo, (int)$b["id"], $empOfSalary, (int)$me["id"], "finalized", 1, 0);
  out(["id" => (int)$b["id"], "finalized" => 0]);
}
// Danh dau DA TRA LUONG cho 1 dong da chot (buoc cuoi cua quy trinh luong).
// Chi Admin + Ke toan truong; dong chua chot thi khong tra duoc.
if ($a === "salary-paid" && $m === "POST") {
  $me = require_payroll($pdo, true);
  salary_ensure_columns($pdo);
  $b = body();
  if (empty($b["id"]) || !is_numeric($b["id"])) err("Thieu id bang luong");
  $paid = isset($b["paid"]) ? (int)$b["paid"] : 1;
  if ($paid !== 0 && $paid !== 1) err("paid chi nhan 0 hoac 1", 422);
  $stmt = $pdo->prepare("SELECT employee_id, finalized, paid FROM salaries WHERE id=?");
  $stmt->execute([(int)$b["id"]]);
  $row = $stmt->fetch(PDO::FETCH_ASSOC);
  if (!$row) err("Khong tim thay bang luong", 404);
  if ($paid === 1 && (int)$row["finalized"] !== 1) err("Bang luong chua chot, chua tra luong duoc", 422);
  $pdo->prepare("UPDATE salaries SET paid=?, paid_at=? WHERE id=?")
    ->execute([$paid, $paid === 1 ? date("Y-m-d H:i:s") : null, (int)$b["id"]]);
  if ((int)$row["paid"] !== $paid) salary_audit($pdo, (int)$b["id"], (int)$row["employee_id"], (int)$me["id"], "paid", (int)$row["paid"], $paid);
  out(["id" => (int)$b["id"], "paid" => $paid]);
}
// Lich su thay doi cua 1 dong bang luong (ai sua khoan nao, tu bao nhieu -> bao nhieu).
// Nguoi xem: nhom nghiep vu luong, hoac chinh chu nhan cua dong luong.
if ($a === "salary-history" && $m === "GET") {
  $me = require_login($pdo);
  salary_ensure_columns($pdo);
  salary_ensure_audit_table($pdo);
  $sid = (int)($_GET["salary_id"] ?? 0);
  if ($sid <= 0) err("Thieu salary_id");
  $stmt = $pdo->prepare("SELECT employee_id FROM salaries WHERE id=?");
  $stmt->execute([$sid]);
  $empId = $stmt->fetchColumn();
  if ($empId === false) err("Khong tim thay bang luong", 404);
  if ((int)$empId !== (int)$me["id"] && !can_view_others_salary($me)) err("Khong co quyen xem lich su nay", 403);
  $stmt = $pdo->prepare("SELECT l.*, a.full_name AS actor_name FROM salary_audit_logs l JOIN employees a ON a.id=l.actor_id WHERE l.salary_id=? ORDER BY l.id DESC");
  $stmt->execute([$sid]);
  out($stmt->fetchAll(PDO::FETCH_ASSOC));
}
// Xuat bang luong 1 thang ra CSV (mo bang Excel duoc, co dau tieng Viet).
if ($a === "salary-export" && $m === "GET") {
  $me = require_payroll($pdo, false);
  salary_ensure_columns($pdo);
  $month = (int)($_GET["month"] ?? date("n")); $year = (int)($_GET["year"] ?? date("Y"));
  if ($month < 1 || $month > 12 || $year < 2000 || $year > 2100) err("Thang/nam khong hop le", 422);
  $stmt = $pdo->prepare("SELECT e.full_name, d.name AS dept_name, e.base_salary AS full_base_salary, s.* FROM salaries s JOIN employees e ON e.id=s.employee_id LEFT JOIN departments d ON d.id=e.department_id WHERE s.month=? AND s.year=? ORDER BY d.name, e.full_name");
  $stmt->execute([$month, $year]);
  $rows = $stmt->fetchAll(PDO::FETCH_ASSOC);
  $num = fn($v) => number_format((float)$v, 0, ",", "");
  $lines = [];
  $lines[] = implode(";", ["STT", "Ho ten", "Phong ban", "Luong co ban", "Ngay cong", "Cong chuan", "Luong theo cong", "Phu cap", "Tang ca", "Thuong", "Khau tru khac", "Bao hiem (10.5%)", "Thue TNCN", "Thuc linh", "Trang thai"]);
  $i = 0; $totNet = 0; $totIns = 0; $totTax = 0;
  foreach ($rows as $r) {
    $i++;
    $status = (int)$r["paid"] === 1 ? "Da tra luong" : ((int)$r["finalized"] === 1 ? "Da chot" : "Ban nhap");
    $lines[] = implode(";", [$i, $r["full_name"], $r["dept_name"] ?? "", $num($r["full_base_salary"]),
      rtrim(rtrim(number_format((float)$r["work_days"], 2, ".", ""), "0"), "."), (int)$r["standard_days"],
      $num($r["base_salary"]), $num($r["allowance"]), $num($r["overtime_pay"]), $num($r["bonus"]),
      $num($r["deduction"]), $num($r["insurance_amount"]), $num($r["tax_amount"]), $num($r["net_salary"]), $status]);
    $totNet += (float)$r["net_salary"]; $totIns += (float)$r["insurance_amount"]; $totTax += (float)$r["tax_amount"];
  }
  $lines[] = implode(";", ["", "TONG", "", "", "", "", "", "", "", "", "", $num($totIns), $num($totTax), $num($totNet), ""]);
  header("Content-Type: text/csv; charset=UTF-8");
  header("Content-Disposition: attachment; filename=\"bang_luong_" . sprintf("%02d_%d", $month, $year) . ".csv\"");
  echo "\xEF\xBB\xBF" . implode("\r\n", $lines);
  exit;
}
// Danh sach phu cap co dinh theo chinh sach.
if ($a === "salary-policies" && $m === "GET") {
  $me = require_payroll($pdo, false);
  salary_ensure_policies_table($pdo);
  $rows = $pdo->query("SELECT p.*, d.name AS dept_name FROM salary_policies p LEFT JOIN departments d ON d.id=p.department_id ORDER BY p.department_id, p.name")->fetchAll(PDO::FETCH_ASSOC);
  out($rows);
}
// Them/sua 1 muc phu cap chinh sach (Admin + Ke toan truong).
if ($a === "salary-policy-save" && $m === "POST") {
  $me = require_payroll($pdo, true);
  salary_ensure_policies_table($pdo);
  $b = body();
  $name = trim((string)($b["name"] ?? ""));
  if ($name === "") err("Thieu ten khoan phu cap", 422);
  if (!isset($b["amount"]) || !is_numeric($b["amount"]) || (float)$b["amount"] < 0) err("So tien khong hop le", 422);
  $deptId = null;
  if (isset($b["department_id"]) && $b["department_id"] !== "" && $b["department_id"] !== null) {
    $chk = $pdo->prepare("SELECT id FROM departments WHERE id=?");
    $chk->execute([(int)$b["department_id"]]);
    if (!$chk->fetch()) err("Phong ban khong ton tai", 422);
    $deptId = (int)$b["department_id"];
  }
  $active = isset($b["active"]) ? (int)$b["active"] : 1;
  if (!empty($b["id"]) && is_numeric($b["id"])) {
    $pdo->prepare("UPDATE salary_policies SET name=?, amount=?, department_id=?, active=? WHERE id=?")
      ->execute([$name, (float)$b["amount"], $deptId, $active, (int)$b["id"]]);
    out(["id" => (int)$b["id"], "updated" => 1]);
  }
  $pdo->prepare("INSERT INTO salary_policies(name, amount, department_id, active) VALUES(?,?,?,?)")
    ->execute([$name, (float)$b["amount"], $deptId, $active]);
  out(["id" => (int)$pdo->lastInsertId(), "created" => 1]);
}
// Xoa 1 muc phu cap chinh sach (Admin + Ke toan truong).
if ($a === "salary-policy-delete" && $m === "POST") {
  $me = require_payroll($pdo, true);
  salary_ensure_policies_table($pdo);
  $b = body();
  if (empty($b["id"]) || !is_numeric($b["id"])) err("Thieu id phu cap");
  $del = $pdo->prepare("DELETE FROM salary_policies WHERE id=?");
  $del->execute([(int)$b["id"]]);
  if ($del->rowCount() === 0) err("Khong tim thay khoan phu cap", 404);
  out(["id" => (int)$b["id"], "deleted" => 1]);
}
if ($a === "dashboard" && $m === "GET") {
  $me = require_login($pdo);
  // Chi admin xem so lieu TOAN cong ty. Moi manager/staff (ke ca HR va Ke toan)
  // chi thay so lieu phong ban cua minh — cung quy tac siet danh sach nhan vien.
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
