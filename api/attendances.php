<?php
// Thiết lập múi giờ Việt Nam (UTC+7)
date_default_timezone_set('Asia/Ho_Chi_Minh');

require_once __DIR__ . "/../employee-api/db.php";

$a = $_GET["action"] ?? ""; 
$m = $_SERVER["REQUEST_METHOD"];

// Hàm tính khoảng cách giữa 2 điểm GPS theo công thức Haversine (trả về mét)
function calculateDistance($lat1, $lon1, $lat2, $lon2) {
    $earthRadius = 6371000; // Bán kính trái đất tính bằng mét
    $dLat = deg2rad($lat2 - $lat1);
    $dLon = deg2rad($lon2 - $lon1);
    $a = sin($dLat / 2) * sin($dLat / 2) +
         cos(deg2rad($lat1)) * cos(deg2rad($lat2)) *
         sin($dLon / 2) * sin($dLon / 2);
    $c = 2 * atan2(sqrt($a), sqrt(1 - $a));
    return $earthRadius * $c;
}

// ==========================================
// 1. CHECK-IN (CÓ CHẶN BÁN KÍNH GPS)
// ==========================================
if ($a === "checkin" && $m === "POST") {
    $b = body(); 
    $emp = (int)($b["employee_id"] ?? 0);
    $lat = (float)($b["latitude"] ?? 0.0);
    $lng = (float)($b["longitude"] ?? 0.0);

    if ($emp <= 0) {
        err("Thiếu hoặc employee_id không hợp lệ", 400);
    }

    // Cấu hình tọa độ văn phòng và bán kính cho phép (Sư phạm Hà Nội)
    $officeLat = 21.037466; 
    $officeLng = 105.783367; 
    $allowedRadius = 500.0; // Mét

    $distance = calculateDistance($officeLat, $officeLng, $lat, $lng);

    if ($distance > $allowedRadius) {
        err("Check-in thất bại: Bạn đang ở ngoài bán kính cho phép của công ty (Cách " . round($distance, 1) . "m)", 400);
    }

    $today = date("Y-m-d"); 
    $now = date("Y-m-d H:i:s");
    $late = (strtotime(date("H:i")) > strtotime("08:15")) ? "late" : "present";

    // Kiểm tra xem nhân viên đã có bản ghi chấm công ngày hôm nay chưa
    $stmtCheck = $pdo->prepare("SELECT id, check_in_time FROM attendances WHERE employee_id = ? AND work_date = ? LIMIT 1");
    $stmtCheck->execute([$emp, $today]);
    $existing = $stmtCheck->fetch(PDO::FETCH_ASSOC);

    if ($existing) {
        $cIn = $existing["check_in_time"];
        if (!empty($cIn) && $cIn !== "0000-00-00 00:00:00" && $cIn !== "null") {
            err("Hôm nay bạn đã check-in rồi", 409);
        } else {
            // Đã có bản ghi (ví dụ do cron quét vắng mặt tạo trước đó), tiến hành CẬP NHẬT thông tin check-in thực tế
            $stmtUpdate = $pdo->prepare("UPDATE attendances SET check_in_time = ?, status = ? WHERE id = ?");
            $stmtUpdate->execute([$now, $late, $existing["id"]]);
            out([
                "check_in" => $now,
                "status" => $late,
                "distance_meters" => round($distance, 1),
                "message" => "Check-in thành công!"
            ]);
        }
    } else {
        // Chưa có bản ghi, chèn mới
        $stmtInsert = $pdo->prepare("INSERT INTO attendances(employee_id, work_date, check_in_time, status) VALUES(?, ?, ?, ?)");
        $stmtInsert->execute([$emp, $today, $now, $late]);
        out([
            "check_in" => $now, 
            "status" => $late,
            "distance_meters" => round($distance, 1),
            "message" => "Check-in thành công!"
        ]);
    }
}

// ==========================================
// 2. CHECK-OUT & TÍNH SỐ GIỜ LÀM (WORK_HOURS)
// ==========================================
if ($a === "checkout" && $m === "POST") {
    $b = body();
    $emp = (int)($b["employee_id"] ?? 0);
    $today = date("Y-m-d");
    $now = date("Y-m-d H:i:s");

    if ($emp <= 0) {
        err("Thiếu hoặc employee_id không hợp lệ", 400);
    }

    // 1. Kiểm tra xem nhân viên đã check-in trong ngày hôm nay chưa
    $stmt = $pdo->prepare("
        SELECT id, check_in_time, check_out_time
        FROM attendances
        WHERE employee_id = ? AND work_date = ?
        LIMIT 1
    ");
    $stmt->execute([$emp, $today]);
    $attendance = $stmt->fetch(PDO::FETCH_ASSOC);

    if (!$attendance || empty($attendance["check_in_time"]) || $attendance["check_in_time"] === "0000-00-00 00:00:00") {
        err("Bạn chưa check-in trong ngày hôm nay nên không thể check-out!", 400);
    }

    // 2. Kiểm tra nếu đã check-out trước đó rồi thì chặn lại
    if (!empty($attendance["check_out_time"]) && $attendance["check_out_time"] !== "0000-00-00 00:00:00") {
        err("Hôm nay bạn đã check-out rồi!", 400);
    }

    // 3. Tính số giờ làm việc thực tế
    $checkInTime = $attendance["check_in_time"];
    $workHours = round((strtotime($now) - strtotime($checkInTime)) / 3600, 2);

    // 4. Thực hiện cập nhật thời gian check-out và số giờ làm vào database
    $attendanceId = $attendance["id"];
    $stmtUpdate = $pdo->prepare("
        UPDATE attendances
        SET check_out_time = ?, work_hours = ?
        WHERE id = ?
    ");
    $stmtUpdate->execute([$now, $workHours, $attendanceId]);

    // 5. Trả về kết quả thành công kèm theo số giờ làm
    out([
        "check_out" => $now,
        "work_hours" => $workHours,
        "message" => "Check-out thành công!"
    ]);
}

// ==========================================
// 3. HISTORY
// ==========================================
if ($a === "history" && $m === "GET") {
    $emp = (int)($_GET["employee_id"] ?? 0); 
    $month = $_GET["month"] ?? date("m"); 
    $year = $_GET["year"] ?? date("Y");

    $stmt = $pdo->prepare("SELECT * FROM attendances WHERE employee_id = ? AND MONTH(work_date) = ? AND YEAR(work_date) = ? ORDER BY work_date DESC");
    $stmt->execute([$emp, $month, $year]);
    out($stmt->fetchAll(PDO::FETCH_ASSOC));
}

// ==========================================
// 4. REPORT (BÁO CÁO CHO ADMIN & SẾP)
// ==========================================
if ($a === "report" && $m === "GET") {
    $date = $_GET["date"] ?? "";
    $dept = $_GET["dept"] ?? "";
    $role = $_GET["role"] ?? "";
    $month = $_GET["month"] ?? "";
    $year = $_GET["year"] ?? "";

    $sql = "SELECT a.*, e.full_name, r.name AS role_name, d.name AS department_name
            FROM attendances a 
            JOIN employees e ON a.employee_id = e.id 
            LEFT JOIN roles r ON e.role_id = r.id
            LEFT JOIN departments d ON e.department_id = d.id
            WHERE 1=1";
    
    $params = [];

    if (!empty($date)) {
        $sql .= " AND a.work_date = ?";
        $params[] = $date;
    } elseif (!empty($month) && !empty($year)) {
        $sql .= " AND MONTH(a.work_date) = ? AND YEAR(a.work_date) = ?";
        $params[] = $month;
        $params[] = $year;
    }

    if (!empty($dept)) {
        $sql .= " AND (e.department_id = ? OR d.name LIKE ?)";
        $params[] = $dept;
        $params[] = "%$dept%";
    }

    if (!empty($role)) {
        $sql .= " AND (r.name = ? OR e.role_id = ?)";
        $params[] = $role;
        $params[] = $role;
    }

    $sql .= " ORDER BY a.work_date DESC, a.id DESC";

    $stmt = $pdo->prepare($sql);
    $stmt->execute($params);
    $result = $stmt->fetchAll(PDO::FETCH_ASSOC);

    header('Content-Type: application/json; charset=utf-8');
    echo json_encode([
        "success" => true,
        "message" => "Lấy báo cáo thành công",
        "data" => $result
    ]);
    exit();
}

// ==========================================
// 5. EXPORT BÁO CÁO CHẤM CÔNG RA FILE EXCEL (.XLSX) CÓ BỘ LỌC (AUTOFILTER) HÀNG TIÊU ĐỀ
// ==========================================
if ($a === "export" && $m === "GET") {
    $date = $_GET["date"] ?? "";
    $dept = $_GET["dept"] ?? "";
    $role = $_GET["role"] ?? "";
    $month = $_GET["month"] ?? "";
    $year = $_GET["year"] ?? "";

    $sql = "SELECT a.work_date, e.full_name, r.name AS role_name, d.name AS department_name,
                   a.check_in_time, a.check_out_time, a.work_hours, a.status
            FROM attendances a
            JOIN employees e ON a.employee_id = e.id
            LEFT JOIN roles r ON e.role_id = r.id
            LEFT JOIN departments d ON e.department_id = d.id
            WHERE 1=1";

    $params = [];

    if (!empty($date)) {
        $sql .= " AND a.work_date = ?";
        $params[] = $date;
    } elseif (!empty($month) && !empty($year)) {
        $sql .= " AND MONTH(a.work_date) = ? AND YEAR(a.work_date) = ?";
        $params[] = $month;
        $params[] = $year;
    }

    if (!empty($dept)) {
        $sql .= " AND (e.department_id = ? OR d.name LIKE ?)";
        $params[] = $dept;
        $params[] = "%$dept%";
    }

    if (!empty($role)) {
        $sql .= " AND (r.name = ? OR e.role_id = ?)";
        $params[] = $role;
        $params[] = $role;
    }

    $sql .= " ORDER BY a.work_date ASC, e.id ASC";

    $stmt = $pdo->prepare($sql);
    $stmt->execute($params);
    $rows = $stmt->fetchAll(PDO::FETCH_ASSOC);

    function getRoleLabel($r) {
        if ($r === 'admin') return 'Quản trị viên';
        if ($r === 'manager') return 'Sếp / Trưởng phòng';
        if ($r === 'staff') return 'Nhân viên';
        return $r ?? 'N/A';
    }

    function getStatusLabel($s) {
        if ($s === 'present') return 'Đúng giờ';
        if ($s === 'late') return 'Đi muộn';
        if ($s === 'absent') return 'Vắng mặt';
        return $s ?? 'N/A';
    }

    $fileName = 'Bao_Cao_Cham_Cong';
    if (!empty($date)) {
        $fileName .= '_' . $date;
    } elseif (!empty($month) && !empty($year)) {
        $fileName .= '_' . $month . '_' . $year;
    }

    $headers = ['Ngày', 'Họ và Tên', 'Cấp Bậc', 'Phòng Ban', 'Check-in', 'Check-out', 'Số Giờ Làm', 'Trạng Thái'];
    $dataRows = [];
    foreach ($rows as $row) {
        $dataRows[] = [
            $row['work_date'],
            $row['full_name'],
            getRoleLabel($row['role_name']),
            $row['department_name'] ?? 'N/A',
            $row['check_in_time'],
            $row['check_out_time'] ?? '',
            $row['work_hours'] ?? '0',
            getStatusLabel($row['status'])
        ];
    }

    // Nếu hệ thống hỗ trợ ZipArchive, tạo file XLSX chuẩn có AutoFilter
    if (class_exists('ZipArchive')) {
        $getColLetter = function($idx) {
            $str = '';
            while ($idx >= 0) {
                $str = chr(65 + ($idx % 26)) . $str;
                $idx = intdiv($idx, 26) - 1;
            }
            return $str;
        };

        $colCount = count($headers);
        $rowCount = count($dataRows) + 1; // Bao gồm hàng tiêu đề
        if ($rowCount < 2) $rowCount = 2; // Đảm bảo range tối thiểu A1:H2
        $lastCol = $getColLetter($colCount - 1);
        $autoFilterRange = "A1:{$lastCol}{$rowCount}";

        $sheetXml = '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>' . "\n";
        $sheetXml .= '<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">' . "\n";
        $sheetXml .= '  <sheetData>' . "\n";

        // Hàng tiêu đề (Row 1)
        $sheetXml .= '    <row r="1">' . "\n";
        foreach ($headers as $cIdx => $hText) {
            $cellRef = $getColLetter($cIdx) . '1';
            $safeText = htmlspecialchars($hText, ENT_QUOTES | ENT_XML1, 'UTF-8');
            $sheetXml .= "      <c r=\"{$cellRef}\" t=\"inlineStr\"><is><t>{$safeText}</t></is></c>\n";
        }
        $sheetXml .= '    </row>' . "\n";

        // Hàng dữ liệu (Row 2 trở đi)
        $rIdx = 2;
        foreach ($dataRows as $row) {
            $sheetXml .= "    <row r=\"{$rIdx}\">\n";
            foreach ($row as $cIdx => $val) {
                $cellRef = $getColLetter($cIdx) . $rIdx;
                $safeVal = htmlspecialchars((string)($val ?? ''), ENT_QUOTES | ENT_XML1, 'UTF-8');
                $sheetXml .= "      <c r=\"{$cellRef}\" t=\"inlineStr\"><is><t>{$safeVal}</t></is></c>\n";
            }
            $sheetXml .= "    </row>\n";
            $rIdx++;
        }

        $sheetXml .= '  </sheetData>' . "\n";
        $sheetXml .= '  <autoFilter ref="' . $autoFilterRange . '"/>' . "\n";
        $sheetXml .= '</worksheet>';

        $tmpFile = tempnam(sys_get_temp_dir(), 'xlsx_');
        $zip = new ZipArchive();
        if ($zip->open($tmpFile, ZipArchive::CREATE | ZipArchive::OVERWRITE) === true) {
            $zip->addFromString('[Content_Types].xml', '<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/></Types>');

            $zip->addFromString('_rels/.rels', '<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>');

            $zip->addFromString('xl/_rels/workbook.xml.rels', '<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/></Relationships>');

            $zip->addFromString('xl/workbook.xml', '<?xml version="1.0" encoding="UTF-8" standalone="yes"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="Sheet1" sheetId="1" r:id="rId1"/></sheets></workbook>');

            $zip->addFromString('xl/worksheets/sheet1.xml', $sheetXml);
            $zip->close();

            header('Content-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet');
            header('Content-Disposition: attachment; filename=' . $fileName . '.xlsx');
            header('Content-Length: ' . filesize($tmpFile));
            readfile($tmpFile);
            @unlink($tmpFile);
            exit();
        }
    }

    // Dự phòng CSV nếu môi trường thiếu ZipArchive
    header('Content-Type: text/csv; charset=utf-8');
    header('Content-Disposition: attachment; filename=' . $fileName . '.csv');
    echo "\xEF\xBB\xBF";
    $output = fopen('php://output', 'w');
    fputcsv($output, $headers);
    foreach ($dataRows as $r) {
        fputcsv($output, $r);
    }
    fclose($output);
    exit();
}

err("Unknown attendance action", 404);
?>
