<?php
require_once __DIR__ . '/hr_employee_policy.php';
require_once __DIR__ . '/employee_validation.php';

function hr_auth(PDO $pdo, bool $managerOnly = false): array {
    $me = require_login($pdo);
    if ($managerOnly ? !hr_manager_actor($me) : !hr_employee_actor($me)) err('Chi phong Nhan su moi co quyen', 403);
    return $me;
}
function hr_length(string $v): int { return function_exists('mb_strlen') ? \mb_strlen($v) : strlen($v); }
function hr_json($value): string { return json_encode($value, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES | JSON_THROW_ON_ERROR); }
function hr_record_history(PDO $pdo, int $actor, ?int $employee, string $action, $before = null, $after = null, ?int $proposalId = null): void {
    $st = $pdo->prepare('INSERT INTO hr_history(employee_id,actor_id,action,before_data,after_data,proposal_id) VALUES(?,?,?,?,?,?)');
    $st->execute([$employee, $actor, $action, $before === null ? null : hr_json($before),
        $after === null ? null : hr_json($after), $proposalId]);
}
function hr_date($value, bool $allowNull = true): ?string {
    if (($value === null || $value === '') && $allowNull) return null;
    if (!is_string($value) || !preg_match('/^\d{4}-\d{2}-\d{2}$/', $value)) err('Ngay phai co dang YYYY-MM-DD', 422);
    $d = DateTimeImmutable::createFromFormat('!Y-m-d', $value);
    if (!$d || $d->format('Y-m-d') !== $value) err('Ngay khong hop le', 422);
    return $value;
}
function hr_at_least_18_at_hire(string $birth, string $hire): bool {
    // A full 18 years, not a rough number of days. Feb 29 requires March 1 in a non-leap year.
    $born=DateTimeImmutable::createFromFormat('!Y-m-d',$birth);
    $hired=DateTimeImmutable::createFromFormat('!Y-m-d',$hire);
    return $born!==false && $hired!==false && $hired >= $born->modify('+18 years');
}
function hr_validate_direct_manager(PDO $pdo, int $managerId, int $employeeId): void {
    if ($managerId === $employeeId) err('Khong the tu quan ly minh',422);
    $st=$pdo->prepare("SELECT LOWER(r.name) AS role,e.status,e.employment_status
        FROM employees e JOIN roles r ON r.id=e.role_id WHERE e.id=? LIMIT 1");
    $st->execute([$managerId]);$row=$st->fetch(PDO::FETCH_ASSOC);
    if (!$row || ($row['role']??'')!=='manager' || (int)$row['status']!==1
        || !in_array($row['employment_status']??'working',['probation','working'],true))
        err('Nguoi quan ly phai la Truong phong dang hoat dong',422);
}
function hr_fk(PDO $pdo, string $table, $value): ?int {
    if ($value === null || $value === '' || (string)$value === '0') return null;
    if (!in_array($table, ['departments','positions','employees'], true) || !filter_var($value, FILTER_VALIDATE_INT, ['options'=>['min_range'=>1]])) err('Ma danh muc khong hop le',422);
    $st=$pdo->prepare("SELECT id FROM $table WHERE id=?"); $st->execute([(int)$value]);
    if (!$st->fetchColumn()) err('Khong tim thay danh muc/nhan vien',422);
    return (int)$value;
}
function hr_employee(PDO $pdo, int $id): array {
    $st=$pdo->prepare('SELECT * FROM employees WHERE id=? LIMIT 1'); $st->execute([$id]);
    $row=$st->fetch(PDO::FETCH_ASSOC);
    if (!$row) err('Khong tim thay nhan vien',404);
    return $row;
}
function hr_profile_changes(PDO $pdo, array $input, array $existing): array {
    // Strict whitelist. Technical role/salary/department/position/termination cannot be changed here.
    $fields=['full_name','email','phone','birth_date','gender','address','direct_manager_id'];
    $disallowed=['role_id','base_salary','department_id','position_id','status','termination_effective_date','password_hash','insurance_salary','dependents'];
    foreach ($disallowed as $name) if (array_key_exists($name,$input) && $input[$name] !== null) err('Truong '.$name.' can quy trinh rieng',403);
    $changes=[];
    foreach ($fields as $field) {
        if (!array_key_exists($field,$input)) continue;
        $v=$input[$field];
        switch ($field) {
            case 'full_name': $v=employee_name($v); break;
            case 'email': $v=employee_email($v); break;
            case 'phone': $v=$v === null || $v === '' ? null : employee_phone($v); break;
            case 'birth_date': $v=hr_date($v); if($v && $v>date('Y-m-d'))err('Ngay sinh o tuong lai',422); break;
            case 'gender': if(!in_array($v,['male','female','other',null],true))err('Gioi tinh khong hop le',422); break;
            case 'address': if($v!==null && (!is_string($v) || hr_length(trim($v))>500))err('Dia chi qua dai',422); $v=$v===null?null:trim($v); break;
            case 'direct_manager_id': $v=hr_fk($pdo,'employees',$v); if($v !== null && (string)$v !== (string)($existing['direct_manager_id']??'')) hr_validate_direct_manager($pdo,$v,(int)$existing['id']); break;
        }
        if ((string)($existing[$field]??'') !== (string)($v??'')) $changes[$field]=$v;
    }
    // HR details can send hire_date in the same request. Compare the final values,
    // including the stored counterpart when only one date is being changed.
    // Avoid blocking unrelated contact edits on imported legacy records.
    if (array_key_exists('birth_date', $input) || array_key_exists('hire_date', $input)) {
        $dob = array_key_exists('birth_date', $changes) ? $changes['birth_date'] : ($existing['birth_date'] ?? null);
        $hire = array_key_exists('hire_date', $input) ? hr_date($input['hire_date']) : ($existing['hire_date'] ?? null);
        if ($dob !== null && $dob !== '' && $hire !== null && $hire !== ''
            && !hr_at_least_18_at_hire($dob,$hire))
            err('Ngay vao lam phai tu ngay du 18 tuoi', 422);
    }
    return $changes;
}
function hr_require_migration(PDO $pdo): void {
    // Fail with a clear error on older installations; never mutate real DB from an API request.
    try { $pdo->query('SELECT id FROM hr_proposals LIMIT 0'); }
    catch (PDOException $e) { err('Chua chay migration HR 012 tren database test',503); }
}

/** No implicit document download permission for system admins, BOD or other managers. */
function hr_doc_access(array $me, int $employeeId): void {
    if ((int)($me['id']??0)!==$employeeId && !hr_employee_actor($me))
        err('Khong co quyen xem tai lieu nhay cam',403);
}
