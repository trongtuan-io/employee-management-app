<?php
// Executive BOD Statistics & Dashboard API
require_once __DIR__ . "/db.php";
require_once __DIR__ . "/auth_guard.php";
require_once __DIR__ . "/salary_calc.php";

$a = $_GET["action"] ?? "dashboard";
$m = $_SERVER["REQUEST_METHOD"];

$me = require_login($pdo);
if ($me["role"] !== "admin" && !is_executive($me)) {
  err("Chỉ Ban Giám đốc và Admin mới có quyền truy cập phân hệ BOD", 403);
}

// 1. KPI Tổng quan cho Dashboard BOD
if ($a === "dashboard" && $m === "GET") {
  $curMonth = (int)date("n");
  $curYear = (int)date("Y");

  // Tổng số nhân viên đang làm việc
  $stEmp = $pdo->query("SELECT COUNT(*) FROM employees WHERE status=1");
  $totalEmployees = (int)$stEmp->fetchColumn();

  // Số đề xuất đang chờ BOD duyệt
  $stProp = $pdo->query("SELECT COUNT(*) FROM hr_proposals WHERE status='pending'");
  $pendingProposals = (int)$stProp->fetchColumn();

  // Tổng quỹ lương tháng hiện tại
  salary_ensure_columns($pdo);
  $stSal = $pdo->prepare("SELECT COALESCE(SUM(net_salary), 0) FROM salaries WHERE month=? AND year=?");
  $stSal->execute([$curMonth, $curYear]);
  $totalSalaryFund = (float)$stSal->fetchColumn();

  // Tỷ lệ chuyên cần trong tháng (% lượt đi đúng giờ trên tổng lượt chấm công)
  $stAtt = $pdo->prepare("SELECT
      COALESCE(SUM(CASE WHEN status='present' THEN 1 ELSE 0 END), 0) AS on_time,
      COUNT(*) AS total_records
    FROM attendances
    WHERE MONTH(work_date)=? AND YEAR(work_date)=?");
  $stAtt->execute([$curMonth, $curYear]);
  $attData = $stAtt->fetch(PDO::FETCH_ASSOC);
  $onTime = (int)($attData["on_time"] ?? 0);
  $totalAtt = (int)($attData["total_records"] ?? 0);
  $attendanceRate = $totalAtt > 0 ? round(($onTime / $totalAtt) * 100, 1) : 100.0;

  out([
    "total_employees" => $totalEmployees,
    "pending_proposals" => $pendingProposals,
    "total_salary_fund" => $totalSalaryFund,
    "attendance_rate" => $attendanceRate,
    "month" => $curMonth,
    "year" => $curYear
  ]);
}

// 2. Thống kê nhân sự theo phòng ban & trạng thái
if ($a === "employee-statistics" && $m === "GET") {
  // Nhân sự theo phòng ban
  $byDept = $pdo->query("SELECT d.name AS dept_name, COUNT(e.id) AS emp_count
                         FROM departments d
                         LEFT JOIN employees e ON e.department_id=d.id AND e.status=1
                         GROUP BY d.id, d.name
                         ORDER BY d.name ASC")->fetchAll(PDO::FETCH_ASSOC);

  // Cơ cấu trạng thái làm việc (Đang làm việc, Đang thử việc, Đã nghỉ việc)
  $stWorking = $pdo->query("SELECT COUNT(*) FROM employees WHERE status=1 AND (employment_status IS NULL OR employment_status<>'probation')")->fetchColumn();
  $stProbation = $pdo->query("SELECT COUNT(*) FROM employees WHERE status=1 AND employment_status='probation'")->fetchColumn();
  $stInactive = $pdo->query("SELECT COUNT(*) FROM employees WHERE status=0")->fetchColumn();

  out([
    "by_department" => $byDept,
    "workforce_structure" => [
      "working" => (int)$stWorking,
      "probation" => (int)$stProbation,
      "inactive" => (int)$stInactive
    ]
  ]);
}

// 3. Thống kê quỹ lương 6 tháng gần nhất
if ($a === "salary-statistics" && $m === "GET") {
  salary_ensure_columns($pdo);
  $list = [];
  $now = new DateTimeImmutable("first day of this month");

  for ($i = 5; $i >= 0; $i--) {
    $dt = $now->modify("-$i month");
    $mNum = (int)$dt->format("n");
    $yNum = (int)$dt->format("Y");
    $mLabel = "T" . $mNum . "/" . substr((string)$yNum, 2);

    $st = $pdo->prepare("SELECT COALESCE(SUM(net_salary), 0) FROM salaries WHERE month=? AND year=?");
    $st->execute([$mNum, $yNum]);
    $fund = (float)$st->fetchColumn();

    $list[] = [
      "month" => $mNum,
      "year" => $yNum,
      "label" => $mLabel,
      "total_fund" => $fund
    ];
  }

  out($list);
}

// 4. Thống kê chuyên cần trong tháng
if ($a === "attendance-statistics" && $m === "GET") {
  $month = (int)($_GET["month"] ?? date("n"));
  $year = (int)($_GET["year"] ?? date("Y"));

  $st = $pdo->prepare("SELECT
      COALESCE(SUM(CASE WHEN status='present' THEN 1 ELSE 0 END), 0) AS on_time,
      COALESCE(SUM(CASE WHEN status='late' THEN 1 ELSE 0 END), 0) AS late
    FROM attendances
    WHERE MONTH(work_date)=? AND YEAR(work_date)=?");
  $st->execute([$month, $year]);
  $data = $st->fetch(PDO::FETCH_ASSOC);

  out([
    "month" => $month,
    "year" => $year,
    "on_time" => (int)($data["on_time"] ?? 0),
    "late" => (int)($data["late"] ?? 0)
  ]);
}

// 5. Thống kê nghỉ phép theo phòng ban
if ($a === "leave-statistics" && $m === "GET") {
  $month = (int)($_GET["month"] ?? date("n"));
  $year = (int)($_GET["year"] ?? date("Y"));

  $st = $pdo->prepare("SELECT d.name AS dept_name, l.status, COUNT(l.id) AS count
                       FROM leave_requests l
                       JOIN employees e ON e.id=l.employee_id
                       JOIN departments d ON d.id=e.department_id
                       WHERE MONTH(l.created_at)=? AND YEAR(l.created_at)=?
                       GROUP BY d.id, d.name, l.status
                       ORDER BY d.name ASC");
  $st->execute([$month, $year]);
  $rows = $st->fetchAll(PDO::FETCH_ASSOC);

  $depts = [];
  foreach ($rows as $r) {
    $d = $r["dept_name"];
    if (!isset($depts[$d])) {
      $depts[$d] = ["dept_name" => $d, "approved" => 0, "pending" => 0, "rejected" => 0];
    }
    $st = $r["status"];
    if (isset($depts[$d][$st])) {
      $depts[$d][$st] += (int)$r["count"];
    }
  }

  out(array_values($depts));
}

// 6. Thống kê biến động nhân sự 6 tháng gần nhất (Tuyển mới vs Nghỉ việc)
if ($a === "employee-movements" && $m === "GET") {
  $movements = [];
  $now = new DateTimeImmutable("first day of this month");

  for ($i = 5; $i >= 0; $i--) {
    $dt = $now->modify("-$i month");
    $mNum = (int)$dt->format("n");
    $yNum = (int)$dt->format("Y");
    $mLabel = "T" . $mNum . "/" . substr((string)$yNum, 2);

    // Tuyển mới
    $stHires = $pdo->prepare("SELECT COUNT(*) FROM employees WHERE MONTH(created_at)=? AND YEAR(created_at)=?");
    $stHires->execute([$mNum, $yNum]);
    $hires = (int)$stHires->fetchColumn();

    // Nghỉ việc
    $stTerms = $pdo->prepare("SELECT COUNT(*) FROM hr_proposals WHERE proposal_type='termination' AND status='approved' AND MONTH(reviewed_at)=? AND YEAR(reviewed_at)=?");
    $stTerms->execute([$mNum, $yNum]);
    $terms = (int)$stTerms->fetchColumn();

    $movements[] = [
      "month" => $mNum,
      "year" => $yNum,
      "label" => $mLabel,
      "hires" => $hires,
      "terminations" => $terms
    ];
  }

  out($movements);
}

// 7. Báo cáo tổng hợp tháng đầy đủ dữ liệu
if ($a === "monthly-report" && $m === "GET") {
  $month = (int)($_GET["month"] ?? date("n"));
  $year = (int)($_GET["year"] ?? date("Y"));
  $deptId = isset($_GET["dept_id"]) && is_numeric($_GET["dept_id"]) ? (int)$_GET["dept_id"] : null;

  // 1. Tổng quan
  $stEmp = $pdo->query("SELECT COUNT(*) FROM employees WHERE status=1");
  $totalActive = (int)$stEmp->fetchColumn();

  // 2. Lương
  salary_ensure_columns($pdo);
  $sqlSal = "SELECT COALESCE(SUM(s.base_salary),0) AS base, COALESCE(SUM(s.allowance),0) AS allowance, COALESCE(SUM(s.bonus),0) AS bonus, COALESCE(SUM(s.deduction),0) AS deduction, COALESCE(SUM(s.net_salary),0) AS net FROM salaries s JOIN employees e ON e.id=s.employee_id WHERE s.month=? AND s.year=?";
  $paramsSal = [$month, $year];
  if ($deptId) { $sqlSal .= " AND e.department_id=?"; $paramsSal[] = $deptId; }
  $stSal = $pdo->prepare($sqlSal);
  $stSal->execute($paramsSal);
  $salSummary = $stSal->fetch(PDO::FETCH_ASSOC);

  // 3. Chấm công
  $sqlAtt = "SELECT COALESCE(SUM(CASE WHEN a.status='present' THEN 1 ELSE 0 END),0) AS on_time, COALESCE(SUM(CASE WHEN a.status='late' THEN 1 ELSE 0 END),0) AS late, COUNT(*) AS total FROM attendances a JOIN employees e ON e.id=a.employee_id WHERE MONTH(a.work_date)=? AND YEAR(a.work_date)=?";
  $paramsAtt = [$month, $year];
  if ($deptId) { $sqlAtt .= " AND e.department_id=?"; $paramsAtt[] = $deptId; }
  $stAtt = $pdo->prepare($sqlAtt);
  $stAtt->execute($paramsAtt);
  $attSummary = $stAtt->fetch(PDO::FETCH_ASSOC);

  out([
    "month" => $month,
    "year" => $year,
    "total_active_employees" => $totalActive,
    "salary_summary" => [
      "total_base" => (float)($salSummary["base"] ?? 0),
      "total_allowance" => (float)($salSummary["allowance"] ?? 0),
      "total_bonus" => (float)($salSummary["bonus"] ?? 0),
      "total_deduction" => (float)($salSummary["deduction"] ?? 0),
      "total_net" => (float)($salSummary["net"] ?? 0)
    ],
    "attendance_summary" => [
      "on_time" => (int)($attSummary["on_time"] ?? 0),
      "late" => (int)($attSummary["late"] ?? 0),
      "total" => (int)($attSummary["total"] ?? 0)
    ]
  ]);
}

// 8. Xuất báo cáo Excel (CSV)
if ($a === "export-report" && $m === "GET") {
  $month = (int)($_GET["month"] ?? date("n"));
  $year = (int)($_GET["year"] ?? date("Y"));

  salary_ensure_columns($pdo);
  $st = $pdo->prepare("SELECT e.username, e.full_name, d.name AS dept_name, p.title AS pos_title, s.base_salary, s.allowance, s.bonus, s.deduction, s.net_salary, s.work_days, s.finalized, s.bod_approved, s.paid FROM employees e LEFT JOIN departments d ON d.id=e.department_id LEFT JOIN positions p ON p.id=e.position_id LEFT JOIN salaries s ON s.employee_id=e.id AND s.month=? AND s.year=? WHERE e.status=1 ORDER BY d.name, e.full_name");
  $st->execute([$month, $year]);
  $rows = $st->fetchAll(PDO::FETCH_ASSOC);

  header("Content-Type: text/csv; charset=UTF-8");
  header("Content-Disposition: attachment; filename=\"BaoCao_BOD_Thang_" . sprintf("%02d_%d", $month, $year) . ".csv\"");

  echo "\xEF\xBB\xBF"; // UTF-8 BOM
  echo "BÁO CÁO QUẢN TRỊ TỔNG HỢP BAN GIÁM ĐỐC (BOD)\n";
  echo "Thời gian lập báo cáo: " . date("d/m/Y H:i:s") . "\n";
  echo "Kỳ báo cáo: Tháng " . $month . "/" . $year . "\n\n";

  echo "Mã NV,Họ và tên,Phòng ban,Chức vụ,Ngày công,Lương theo công,Phụ cấp,Thưởng,Khấu trừ,Thực lĩnh,Trạng thái\n";
  foreach ($rows as $r) {
    $stText = ((int)($r["paid"] ?? 0) === 1) ? "Đã giải ngân" : (((int)($r["bod_approved"] ?? 0) === 1) ? "Giám đốc đã duyệt" : (((int)($r["finalized"] ?? 0) === 1) ? "Đã trình GĐ" : "Bản nháp"));
    echo '"' . $r["username"] . '",';
    echo '"' . $r["full_name"] . '",';
    echo '"' . ($r["dept_name"] ?? "Chưa xếp") . '",';
    echo '"' . ($r["pos_title"] ?? "") . '",';
    echo '"' . ($r["work_days"] ?? 0) . '",';
    echo '"' . number_format((float)($r["base_salary"] ?? 0)) . '",';
    echo '"' . number_format((float)($r["allowance"] ?? 0)) . '",';
    echo '"' . number_format((float)($r["bonus"] ?? 0)) . '",';
    echo '"' . number_format((float)($r["deduction"] ?? 0)) . '",';
    echo '"' . number_format((float)($r["net_salary"] ?? 0)) . '",';
    echo '"' . $stText . '"' . "\n";
  }
  exit;
}

err("Unknown BOD action");
?>
