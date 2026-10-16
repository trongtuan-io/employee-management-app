<?php
/** Task 22 batch operations. Does not modify the team's auth endpoints. */
require_once __DIR__ . '/db.php';
require_once __DIR__ . '/import_support.php';
$action = $_GET['action'] ?? 'list';
$method = $_SERVER['REQUEST_METHOD'];
$params = ($method === 'GET') ? $_GET : ($_POST ?: body());
$actor = batch_actor($pdo, (int)($params['requester_id'] ?? 0));
try {
    if ($action === 'list' && $method === 'GET') {
        $s = $pdo->query('SELECT id,source_name,total_rows,status,created_at,last_synced_at,rolled_back_at FROM employee_import_batches ORDER BY id DESC LIMIT 100');
        out(['batches'=>$s->fetchAll(PDO::FETCH_ASSOC)]);
    }
    if ($action === 'detail' && $method === 'GET') {
        $id = filter_var($params['batch_id'] ?? null, FILTER_VALIDATE_INT, ['options'=>['min_range'=>1]]);
        if (!$id) err('ID dot import khong hop le', 422);
        $s = $pdo->prepare('SELECT id,source_name,total_rows,status,created_at,last_synced_at,rolled_back_at FROM employee_import_batches WHERE id=?');
        $s->execute([$id]); $batch = $s->fetch(PDO::FETCH_ASSOC);
        if (!$batch) err('Khong tim thay dot import', 404);
        $s = $pdo->prepare('SELECT `row_number`,employee_id FROM employee_import_items WHERE batch_id=? ORDER BY `row_number`');
        $s->execute([$id]);
        out(['batch'=>$batch, 'rows'=>$s->fetchAll(PDO::FETCH_ASSOC)]);
    }
    if (!in_array($action, ['sync', 'rollback'], true) || $method !== 'POST') err('Khong ho tro yeu cau', 405);
    $batchId = filter_var($params['batch_id'] ?? null, FILTER_VALIDATE_INT, ['options'=>['min_range'=>1]]);
    if (!$batchId) err('Thieu ID dot import', 422);
    // Validate the file before starting DB transaction.
    $input = $action === 'sync' ? batch_parse_file($pdo, $_FILES['file'] ?? []) : [];
    $pdo->beginTransaction();
    $stmt = $pdo->prepare('SELECT * FROM employee_import_batches WHERE id=? FOR UPDATE');
    $stmt->execute([$batchId]); $batch = $stmt->fetch(PDO::FETCH_ASSOC);
    if (!$batch) throw new InvalidArgumentException('Dot import khong ton tai');
    if ($batch['status'] !== 'active') throw new InvalidArgumentException('Dot import da rollback');
    $stmt = $pdo->prepare('SELECT * FROM employee_import_items WHERE batch_id=? ORDER BY `row_number` FOR UPDATE');
    $stmt->execute([$batchId]); $items = $stmt->fetchAll(PDO::FETCH_ASSOC);
    if (count($items) !== (int)$batch['total_rows']) throw new RuntimeException('Danh sach dong import khong khop');
    if ($action === 'sync' && count($input) !== count($items)) throw new InvalidArgumentException('Khong duoc them/xoa dong khi dong bo; chi sua thong tin tren dong co san');
    $employees = [];
    foreach ($items as $index=>$item) {
        if ($item['employee_id'] === null) throw new InvalidArgumentException('Mot nhan vien da bi xoa khoi MySQL; khong the tu dong xu ly');
        $employee = batch_load_employee($pdo, (int)$item['employee_id']);
        if (!$employee) throw new InvalidArgumentException('Khong tim thay nhan vien trong dot import');
        if (!hash_equals($item['last_hash'], batch_employee_hash($employee))) {
            throw new InvalidArgumentException('Nhan vien ' . $employee['username'] . ' da thay doi ngoai Excel; khong the dong bo/rollback tu dong');
        }
        $employees[] = $employee;
        if ($action === 'sync' && (int)$item['row_number'] !== $index + 2) throw new RuntimeException('Thu tu dong du lieu bi sai');
    }
    if ($action === 'rollback') {
        // Reject any referenced record before deletion, including attendance, payroll, leave and login data.
        foreach ($employees as $employee) {
            if (batch_has_references($pdo, (int)$employee['id'])) {
                throw new InvalidArgumentException('Nhan vien ' . $employee['username'] . ' da co du lieu lien quan; khong duoc rollback');
            }
        }
        $delete = $pdo->prepare('DELETE FROM employees WHERE id=?');
        foreach ($employees as $employee) {
            $delete->execute([(int)$employee['id']]);
            if ($delete->rowCount() !== 1) throw new RuntimeException('Khong the hoan tac du so nhan vien');
        }
        $s = $pdo->prepare("UPDATE employee_import_batches SET status='rolled_back',rolled_back_at=NOW() WHERE id=?");
        $s->execute([$batchId]);
        $pdo->commit();
        out(['rolled_back'=>count($employees), 'batch_id'=>$batchId]);
    }
    $changed = 0;
    $update = $pdo->prepare('UPDATE employees SET username=?,full_name=?,email=?,phone=?,department_id=?,position_id=?,hire_date=? WHERE id=?');
    $phoneConflict = $pdo->prepare('SELECT id FROM employees WHERE phone=? AND id<>? LIMIT 1');
    $saveHash = $pdo->prepare('UPDATE employee_import_items SET last_hash=? WHERE id=?');
    foreach ($items as $i=>$item) {
        $row = $input[$i]; $old = $employees[$i];
        // If a row was reordered in an external spreadsheet, do not map it to the wrong ID.
        // username is the stable identity key in a file. Never silently remap rows.
        // To fix a mistaken employee code, rollback and import corrected file again.
        if ($row['username'] !== $old['username']) {
            throw new InvalidArgumentException('Dong ' . ($i+2) . ': khong duoc doi username hay thu tu dong. Hay rollback neu can sua ma NV');
        }
        $keys = ['username','full_name','email','phone','department_id','position_id','hire_date'];
        $changedRow = false;
        foreach ($keys as $key) {
            if ((string)($row[$key] ?? '') !== (string)($old[$key] ?? '')) { $changedRow = true; break; }
        }
        if (!$changedRow) continue;
        if ($row['phone'] !== null) {
            $phoneConflict->execute([$row['phone'], (int)$old['id']]);
            if ($phoneConflict->fetchColumn()) throw new InvalidArgumentException('Dong ' . ($i+2) . ': SDT da duoc nhan vien khac su dung');
        }
        $update->execute([$row['username'],$row['full_name'],$row['email'],$row['phone'],$row['department_id'],$row['position_id'],$row['hire_date'],(int)$old['id']]);
        $new = batch_load_employee($pdo, (int)$old['id']);
        $saveHash->execute([batch_employee_hash($new), (int)$item['id']]);
        $changed++;
    }
    $s = $pdo->prepare('UPDATE employee_import_batches SET last_synced_at=NOW() WHERE id=?');
    $s->execute([$batchId]);
    $pdo->commit();
    out(['batch_id'=>$batchId,'updated'=>$changed,'total'=>count($items)]);
} catch (Throwable $e) {
    if ($pdo->inTransaction()) $pdo->rollBack();
    if ($e instanceof InvalidArgumentException) err($e->getMessage(), 422);
    if ($e instanceof PDOException && $e->getCode() === '23000') err('Trung username/email hoac du lieu rang buoc, khong cap nhat dong nao', 409);
    err('Khong xu ly duoc dot import; kiem tra migration va du lieu lien quan', 500);
}
