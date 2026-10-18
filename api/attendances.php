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
    $allowedRadius = 1000.0; // Mét

    $distance = calculateDistance($officeLat, $officeLng, $lat, $lng);

    if ($distance > $allowedRadius) {
        err("Check-in thất bại: Bạn đang ở ngoài bán kính cho phép của công ty (Cách " . round($distance, 1) . "m)", 400);
    }

    $today = date("Y-m-d"); 
    $now = date("Y-m-d H:i:s");
    $late = (strtotime(date("H:i")) > strtotime("08:15")) ? "late" : "present";

    try {
        $stmt = $pdo->prepare("INSERT INTO attendances(employee_id, work_date, check_in_time, status) VALUES(?, ?, ?, ?)");
        $stmt->execute([$emp, $today, $now, $late]);
        
        out([
            "check_in" => $now, 
            "status" => $late,
            "distance_meters" => round($distance, 1)
        ]);
    } catch (Exception $e) { 
        err("Hôm nay đã check-in rồi", 409); 
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

    if (!$attendance) {
        err("Bạn chưa check-in trong ngày hôm nay nên không thể check-out!", 400);
    }

    // 2. Kiểm tra nếu đã check-out trước đó rồi thì chặn lại
    if (!empty($attendance["check_out_time"]) && $attendance["check_out_time"] !== "0000-00-00 00:00:00") {
        err("Hôm nay bạn đã check-out rồi!", 400);
    }

    // 3. Tính số giờ làm việc thực tế (lấy thời điểm checkout trừ đi checkin, quy đổi ra giờ và làm tròn 2 chữ số)
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
// 4. REPORT (BÁO CÁO CHO ADMIN)
// ==========================================
if ($a === "report" && $m === "GET") {
    $date = $_GET["date"] ?? date("Y-m-d");
    $dept = $_GET["dept"] ?? "";

    $sql = "SELECT a.*, e.full_name, d.name AS department_name 
            FROM attendances a 
            JOIN employees e ON a.employee_id = e.id 
            LEFT JOIN departments d ON e.department_id = d.id 
            WHERE a.work_date = ?";
    
    $params = [$date];

    if (!empty($dept)) {
        $sql .= " AND (e.department_id = ? OR d.name LIKE ?)";
        $params[] = $dept;
        $params[] = "%$dept%";
    }

    $sql .= " ORDER BY a.id DESC";

    $stmt = $pdo->prepare($sql);
    $stmt->execute($params);
    $result = $stmt->fetchAll(PDO::FETCH_ASSOC);

    // Trả về dạng JSON chuẩn có bọc "data" để khớp với client Android
    header('Content-Type: application/json; charset=utf-8');
    echo json_encode([
        "success" => true,
        "message" => "Lấy báo cáo thành công",
        "data" => $result
    ]);
    exit();
}

// ==========================================
// 5. EXPORT BÁO CÁO CHẤM CÔNG RA FILE CSV/EXCEL
// ==========================================
if ($a === "export" && $m === "GET") {
    $month = $_GET["month"] ?? date("m");
    $year = $_GET["year"] ?? date("Y");

    $sql = "SELECT a.work_date, e.full_name, d.name AS department_name, 
                   a.check_in_time, a.check_out_time, a.work_hours, a.status
            FROM attendances a
            JOIN employees e ON a.employee_id = e.id
            LEFT JOIN departments d ON e.department_id = d.id
            WHERE MONTH(a.work_date) = ? AND YEAR(a.work_date) = ?
            ORDER BY a.work_date ASC, e.id ASC";

    $stmt = $pdo->prepare($sql);
    $stmt->execute([$month, $year]);
    $rows = $stmt->fetchAll(PDO::FETCH_ASSOC);

    // Thiết lập Header để trình duyệt hiểu là file CSV/Excel tải về
    header('Content-Type: text/csv; charset=utf-8');
    header('Content-Disposition: attachment; filename=Bao_Cao_Cham_Cong_' . $month . '_' . $year . '.csv');

    // Thêm BOM để hiển thị tiếng Việt có dấu chuẩn trên Excel
    echo "\xEF\xBB\xBF";

    $output = fopen('php://output', 'w');

    // Ghi tiêu đề các cột
    fputcsv($output, ['Ngày', 'Họ và Tên', 'Phòng Ban', 'Check-in', 'Check-out', 'Số Giờ Làm', 'Trạng Thái']);

    // Ghi dữ liệu từng dòng nhân viên
    foreach ($rows as $row) {
        fputcsv($output, [
            $row['work_date'],
            $row['full_name'],
            $row['department_name'] ?? 'N/A',
            $row['check_in_time'],
            $row['check_out_time'],
            $row['work_hours'],
            $row['status']
        ]);
    }

    fclose($output);
    exit();
}

err("Unknown attendance action", 404);
?>