<?php
// Thiết lập múi giờ Việt Nam (UTC+7)
date_default_timezone_set('Asia/Ho_Chi_Minh');

// Nhúng file kết nối cơ sở dữ liệu (điều chỉnh lại đường dẫn nếu cần)
require_once __DIR__ . "/../employee-api/db.php";

$today = date("Y-m-d");

try {
    // 1. Lọc ra ID của những nhân viên chưa có bất kỳ bản ghi chấm công nào trong ngày hôm nay
    $sql = "SELECT id FROM employees WHERE id NOT IN (
                SELECT employee_id FROM attendances WHERE work_date = ?
            )";
    $stmt = $pdo->prepare($sql);
    $stmt->execute([$today]);
    $absentEmployees = $stmt->fetchAll(PDO::FETCH_COLUMN);

    $count = 0;
    // 2. Tự động chèn bản ghi với trạng thái 'absent' cho các nhân viên đó
    foreach ($absentEmployees as $empId) {
        $stmtInsert = $pdo->prepare("
            INSERT INTO attendances (employee_id, work_date, status, work_hours) 
            VALUES (?, ?, 'absent', 0)
        ");
        $stmtInsert->execute([$empId, $today]);
        $count++;
    }

    // Trả về kết quả JSON thông báo
    header('Content-Type: application/json; charset=utf-8');
    echo json_encode([
        "success" => true,
        "message" => "Chạy cron thành công! Đã cập nhật trạng thái vắng mặt cuối ngày.",
        "work_date" => $today,
        "total_absent" => $count
    ]);
} catch (Exception $e) {
    header('Content-Type: application/json; charset=utf-8');
    echo json_encode([
        "success" => false,
        "error" => $e->getMessage()
    ]);
}
?>