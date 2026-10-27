<?php
require_once __DIR__ . '/db.php';
require_once __DIR__ . '/employee_validation.php';
$action = $_GET['action'] ?? 'list';
$method = $_SERVER['REQUEST_METHOD'];
if ($action === 'list' && $method === 'GET') {
    $query = $pdo->query('SELECT d.id,d.name,d.description,COUNT(e.id) AS member_count FROM departments d LEFT JOIN employees e ON e.department_id=d.id GROUP BY d.id,d.name,d.description ORDER BY d.name,d.id');
    out($query->fetchAll(PDO::FETCH_ASSOC));
}
if ($action === 'members' && $method === 'GET') {
    $id = filter_var($_GET['id'] ?? null, FILTER_VALIDATE_INT, ['options'=>['min_range'=>1]]);
    if (!$id) err('Thieu id phong ban', 422);
    $stmt = $pdo->prepare('SELECT 1 FROM departments WHERE id=?');
    $stmt->execute([$id]);
    if (!$stmt->fetchColumn()) err('Khong tim thay phong ban', 404);
    $stmt = $pdo->prepare('SELECT id,username,full_name,email,phone,status FROM employees WHERE department_id=? ORDER BY full_name,id');
    $stmt->execute([$id]);
    out($stmt->fetchAll(PDO::FETCH_ASSOC));
}
if (!in_array($action, ['create','update','delete'], true)) err('Unknown departments action', 404);
if (($action === 'create' && $method !== 'POST') || ($action === 'update' && !in_array($method, ['PUT','POST'])) || ($action === 'delete' && !in_array($method, ['DELETE','POST']))) {
    err('Method not allowed', 405);
}
$b = body();
$editor = (int)($b['requester_id'] ?? 0);
// Project currently uses demo-token auth; replace this with VERIFIED identity once Auth is integrated.
if (employee_editor_role($pdo, $editor) !== 'admin') err('Chi admin duoc quan ly phong ban', 403);
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
    $stmt = $pdo->prepare('DELETE FROM departments WHERE id=?');
    $stmt->execute([$id]);
    out(['deleted'=>true]);
} catch (PDOException $e) {
    if ($e->getCode() === '23000') err('Phong ban trung ten hoac dang duoc su dung', 409);
    err('Khong xu ly duoc phong ban', 500);
}
