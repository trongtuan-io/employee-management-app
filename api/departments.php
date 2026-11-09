<?php
require_once __DIR__ . '/db.php';
require_once __DIR__ . '/employee_validation.php';
require_once __DIR__ . '/auth_guard.php';
require_once __DIR__ . '/positions_logic.php';
$action = $_GET['action'] ?? 'list';
$method = $_SERVER['REQUEST_METHOD'];
if ($action === 'list' && $method === 'GET') {
    require_login($pdo);
    $query = $pdo->query('SELECT d.id,d.name,d.description,COUNT(e.id) AS member_count FROM departments d LEFT JOIN employees e ON e.department_id=d.id GROUP BY d.id,d.name,d.description ORDER BY d.name,d.id');
    out($query->fetchAll(PDO::FETCH_ASSOC));
}
if ($action === 'members' && $method === 'GET') {
    // Thanh vien phong ban theo Role x Phong (siet chat 09/11/2026):
    // - admin: xem moi phong, day du thong tin.
    // - manager (MOI phong): chi xem phong cua minh.
    // - staff: khong xem duoc.
    $me = require_login($pdo);
    $isGlobal = $me['role'] === 'admin';
    if (!$isGlobal && $me['role'] !== 'manager') err('Khong co quyen xem thanh vien phong ban', 403);
    $id = filter_var($_GET['id'] ?? null, FILTER_VALIDATE_INT, ['options'=>['min_range'=>1]]);
    if (!$id) err('Thieu id phong ban', 422);
    if (!$isGlobal && (int)$id !== (int)$me['department_id']) err('Chi xem duoc thanh vien phong ban minh', 403);
    $stmt = $pdo->prepare('SELECT 1 FROM departments WHERE id=?');
    $stmt->execute([$id]);
    if (!$stmt->fetchColumn()) err('Khong tim thay phong ban', 404);
    $cols = 'id,username,full_name,email,phone,status';
    $stmt = $pdo->prepare("SELECT $cols FROM employees WHERE department_id=? ORDER BY full_name,id");
    $stmt->execute([$id]);
    out($stmt->fetchAll(PDO::FETCH_ASSOC));
}
if (!in_array($action, ['create','update','delete'], true)) err('Unknown departments action', 404);
if (($action === 'create' && $method !== 'POST') || ($action === 'update' && !in_array($method, ['PUT','POST'])) || ($action === 'delete' && !in_array($method, ['DELETE','POST']))) {
    err('Method not allowed', 405);
}
$b = body();
// Danh tinh lay tu token da xac thuc (auth_guard.php), khong con tin requester_id.
require_role($pdo, ['admin']);
$id = filter_var($b['id'] ?? null, FILTER_VALIDATE_INT, ['options'=>['min_range'=>1]]);
if ($action !== 'create' && !$id) err('Thieu id phong ban', 422);
try {
    if ($action !== 'delete') {
        $name = trim((string)($b['name'] ?? ''));
        $description = trim((string)($b['description'] ?? ''));
        if ($name === '' || strlen($name) > 250 || strlen($description) > 4000 || preg_match('/[\x00-\x1F\x7F]/', $name)) {
            err('Ten phong ban bat buoc, toi da 250 ky tu', 422);
        }
    }
    if ($action === 'create') {
        $stmt = $pdo->prepare('INSERT INTO departments(name,description) VALUES(?,?)');
        $stmt->execute([$name, $description ?: null]);
        out(['id'=>(int)$pdo->lastInsertId()]);
    }
    $stmt = $pdo->prepare('SELECT 1 FROM departments WHERE id=?');
    $stmt->execute([$id]);
    if (!$stmt->fetchColumn()) err('Khong tim thay phong ban', 404);
    if ($action === 'update') {
        $stmt = $pdo->prepare('UPDATE departments SET name=?,description=? WHERE id=?');
        $stmt->execute([$name,$description ?: null,$id]);
        out(['updated'=>true]);
    }
    $stmt = $pdo->prepare('SELECT COUNT(*) FROM employees WHERE department_id=?');
    $stmt->execute([$id]);
    if ((int)$stmt->fetchColumn() > 0) err('Khong the xoa phong ban dang co nhan vien', 409);
    // Chuc vu gan rieng phong nay thi dua ve "dung chung" de khong mo coi.
    positions_ensure($pdo);
    $pdo->prepare('UPDATE positions SET department_id=NULL WHERE department_id=?')->execute([$id]);
    $stmt = $pdo->prepare('DELETE FROM departments WHERE id=?');
    $stmt->execute([$id]);
    out(['deleted'=>true]);
} catch (PDOException $e) {
    if ($e->getCode() === '23000') err('Phong ban trung ten hoac dang duoc su dung', 409);
    err('Khong xu ly duoc phong ban', 500);
}
