<?php
// Thiết lập múi giờ Việt Nam (UTC+7)
date_default_timezone_set('Asia/Ho_Chi_Minh');

// Nhúng file kết nối cơ sở dữ liệu
require_once __DIR__ . "/../employee-api/db.php";

$currentHour = (int)date("H");
$reqDate = $_GET["date"] ?? "";

// KIỂM TRA THỜI GIAN QUÉT VẮNG MẶT:
// Chỉ tự động chốt vắng mặt cho ngày HÔM NAY sau 23:00 (khi ngày làm việc đã kết thúc).
// Nếu tự động chạy trước 23:00 (và không truyền tham số date cụ thể), hệ thống sẽ chốt vắng mặt cho ngày HÔM QUA.
if (!empty($reqDate)) {
    $targetDate = $reqDate;
} else {
    if ($currentHour < 23) {
        $targetDate = date("Y-m-d", strtotime("-1 day"));
    } else {
        $targetDate = date("Y-m-d");
    }
}

try {
    // 1. Lọc ra ID của những nhân viên chưa có bất kỳ bản ghi chấm công nào trong ngày targetDate
    $sql = "SELECT id FROM employees WHERE id NOT IN (
                SELECT employee_id FROM attendances WHERE work_date = ?
            )";
    $stmt = $pdo->prepare($sql);
    $stmt->execute([$targetDate]);
    $absentEmployees = $stmt->fetchAll(PDO::FETCH_COLUMN);

    $count = 0;
    // 2. Tự động chèn bản ghi với trạng thái 'absent' cho các nhân viên đó
    foreach ($absentEmployees as $empId) {
        $stmtInsert = $pdo->prepare("
            INSERT INTO attendances (employee_id, work_date, status, work_hours) 
            VALUES (?, ?, 'absent', 0)
        ");
        $stmtInsert->execute([$empId, $targetDate]);
        $count++;
    }

    // Trả về kết quả JSON thông báo
    header('Content-Type: application/json; charset=utf-8');
    echo json_encode([
        "success" => true,
        "message" => "Chạy quét vắng mặt thành công!",
        "work_date" => $targetDate,
        "total_absent" => $count,
        "note" => ($currentHour < 23 && empty($reqDate)) ? "Do chưa đến 23h00, hệ thống tự động chốt vắng mặt cho ngày hôm qua (" . $targetDate . ")" : "Đã chốt vắng mặt ngày " . $targetDate
    ]);
} catch (Exception $e) {
    header('Content-Type: application/json; charset=utf-8');
    echo json_encode([
        "success" => false,
        "error" => $e->getMessage()
    ]);
}
?>
