<?php
/** Upload new batch atomically; track the precise employee IDs for safe sync/rollback. */
require_once __DIR__ . '/db.php';
require_once __DIR__ . '/import_support.php';
if ($_SERVER['REQUEST_METHOD'] !== 'POST') err('Method not allowed', 405);
batch_actor($pdo, (int)($_POST['requester_id'] ?? 0));
try {
    $f = $_FILES['file'] ?? [];
    $input = batch_parse_file($pdo, $f);
    $role = $pdo->query("SELECT id FROM roles WHERE name='staff' LIMIT 1")->fetchColumn();
    if (!$role) throw new RuntimeException('Chua co role staff');
    // Database schema has no UNIQUE(phone), so reject duplicates explicitly.
    $phoneStmt = $pdo->prepare('SELECT 1 FROM employees WHERE phone=? LIMIT 1');
    foreach ($input as $row) {
        if ($row['phone'] !== null) {
            $phoneStmt->execute([$row['phone']]);
            if ($phoneStmt->fetchColumn()) throw new InvalidArgumentException('SDT da ton tai trong MySQL');
        }
    }
    $pdo->beginTransaction();
    $s = $pdo->prepare("INSERT INTO employee_import_batches(source_name,imported_by,total_rows,status) VALUES(?,?,?,'active')");
    $name = substr(basename((string)$f['name']), 0, 200);
    $s->execute([$name, (int)$_POST['requester_id'], count($input)]);
    $batchId = (int)$pdo->lastInsertId();
    $insert = $pdo->prepare('INSERT INTO employees(username,password_hash,full_name,phone,email,role_id,department_id,position_id,hire_date,status) VALUES(?,?,?,?,?,?,?,?,?,1)');
    $saveItem = $pdo->prepare('INSERT INTO employee_import_items(batch_id,`row_number`,employee_id,last_hash) VALUES(?,?,?,?)');
    $credentials = [];
    foreach ($input as $i=>$item) {
        $temporary = bin2hex(random_bytes(8));
        $insert->execute([$item['username'], password_hash($temporary, PASSWORD_BCRYPT), $item['full_name'], $item['phone'], $item['email'], (int)$role, $item['department_id'], $item['position_id'], $item['hire_date']]);
        $id = (int)$pdo->lastInsertId();
        $employee = batch_load_employee($pdo, $id);
        $saveItem->execute([$batchId, $i+2, $id, batch_employee_hash($employee)]);
        $credentials[] = ['username'=>$item['username'], 'temporary_password'=>$temporary];
    }
    $pdo->commit();
    out(['batch_id'=>$batchId, 'imported'=>count($input), 'temporary_credentials'=>$credentials]);
} catch (Throwable $e) {
    if ($pdo->inTransaction()) $pdo->rollBack();
    if ($e instanceof PDOException && $e->getCode() === '23000') err('Trung username/email/SDT, khong nhap dong nao', 409);
    if ($e instanceof InvalidArgumentException) err($e->getMessage(), 422);
    err('Import that bai: kiem tra migration/import va cau hinh PHP', 500);
}
