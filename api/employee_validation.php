<?php
/** Shared validation for the employee module; no database connection required. */
function employee_name($value) {
    if (!is_string($value)) throw new InvalidArgumentException('Ho ten khong hop le');
    $value = trim($value);
    if ($value === '' || strlen($value) > 300 || preg_match('/[\x00-\x1F\x7F]/', $value) || preg_match_all('/./us', $value) > 100) {
        throw new InvalidArgumentException('Ho ten bat buoc, toi da 100 ky tu');
    }
    return $value;
}
function employee_email($value) {
    if (!is_string($value) || strlen(trim($value)) > 100 || !filter_var(trim($value), FILTER_VALIDATE_EMAIL)) {
        throw new InvalidArgumentException('Email khong hop le');
    }
    return trim($value);
}
function employee_phone($value) {
    if ($value === null || trim((string)$value) === '') return null;
    if (!is_string($value) || strlen($value) > 15 || !preg_match('/^\+?[0-9]{7,15}$/D', trim($value))) {
        throw new InvalidArgumentException('So dien thoai phai co 7-15 chu so');
    }
    return trim($value);
}
function employee_username($value) {
    if (!is_string($value) || !preg_match('/^[A-Za-z0-9_.-]{3,50}$/D', trim($value))) {
        throw new InvalidArgumentException('Ma NV (username) can 3-50 ky tu chu, so, _, ., -');
    }
    return trim($value);
}
function employee_hire_date($value) {
    if ($value === null || $value === '' || $value === 0 || $value === '0') return null;
    if (!is_string($value) || !preg_match('/^\d{4}-\d{2}-\d{2}$/D', $value)) {
        throw new InvalidArgumentException('Ngay vao lam phai co dang YYYY-MM-DD');
    }
    $date = DateTimeImmutable::createFromFormat('!Y-m-d', $value);
    if (!$date || $date->format('Y-m-d') !== $value || $date > new DateTimeImmutable('today')) {
        throw new InvalidArgumentException('Ngay vao lam khong hop le hoac o tuong lai');
    }
    // The team has not specified a maximum time in the past; do not impose one.
    return $value;
}
function employee_optional_fk(PDO $pdo, $value, $table) {
    if ($value === null || $value === '' || $value === 0 || $value === '0') return null;
    if (!in_array($table, ['departments', 'positions'], true)) throw new InvalidArgumentException('Danh muc khong hop le');
    $id = filter_var($value, FILTER_VALIDATE_INT, ['options'=>['min_range'=>1]]);
    if (!$id) throw new InvalidArgumentException('Ma danh muc khong hop le');
    $stmt = $pdo->prepare("SELECT 1 FROM $table WHERE id=?");
    $stmt->execute([$id]);
    if (!$stmt->fetchColumn()) throw new InvalidArgumentException('Phong ban/chuc vu khong ton tai');
    return (int)$id;
}
function employee_editor_role(PDO $pdo, $requesterId) {
    $stmt = $pdo->prepare('SELECT LOWER(r.name) FROM employees e LEFT JOIN roles r ON r.id=e.role_id WHERE e.id=?');
    $stmt->execute([(int)$requesterId]);
    return (string)($stmt->fetchColumn() ?: '');
}
