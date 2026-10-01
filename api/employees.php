<?php
require_once __DIR__ . "/../employee-api/db.php";
$a = $_GET["action"] ?? "list"; $m = $_SERVER["REQUEST_METHOD"];

if ($a === "list" && $m === "GET") {
  $s = "%" . ($_GET["search"] ?? "") . "%";
  $page = max(1, (int)($_GET["page"] ?? 1)); $limit = min(50, (int)($_GET["limit"] ?? 20)); $off = ($page - 1) * $limit;
  $dept = $_GET["dept_id"] ?? "";
  $sql = "SELECT e.id,e.username,e.full_name,e.phone,e.email,e.base_salary,e.status,d.name AS dept_name,p.title AS position_name,r.name AS role_name FROM employees e LEFT JOIN departments d ON e.department_id=d.id LEFT JOIN positions p ON e.position_id=p.id LEFT JOIN roles r ON e.role_id=r.id WHERE (e.full_name LIKE ? OR e.username LIKE ? OR e.email LIKE ?)";
  $params = [$s, $s, $s];
  if ($dept !== "") { $sql .= " AND e.department_id=?"; $params[] = $dept; }
  $sql .= " ORDER BY e.id DESC LIMIT $limit OFFSET $off";
  $stmt = $pdo->prepare($sql); $stmt->execute($params);
  out($stmt->fetchAll(PDO::FETCH_ASSOC));
}
if ($a === "detail" && $m === "GET") {
  $stmt = $pdo->prepare("SELECT e.*, d.name AS dept_name, p.title AS position_name FROM employees e LEFT JOIN departments d ON e.department_id=d.id LEFT JOIN positions p ON e.position_id=p.id WHERE e.id=?");
  $stmt->execute([$_GET["id"] ?? 0]);
  $r = $stmt->fetch(PDO::FETCH_ASSOC);
  if (!$r) err("Khong tim thay NV", 404);
  unset($r["password_hash"]); out($r);
}
if ($a === "create" && $m === "POST") {
  $b = body();
  if (empty($b["username"]) || empty($b["full_name"]) || empty($b["email"])) err("Thieu username/full_name/email");
  $hash = password_hash($b["password"] ?? "123456", PASSWORD_BCRYPT);
  try {
    $stmt = $pdo->prepare("INSERT INTO employees(username,password_hash,full_name,phone,email,base_salary,role_id,department_id,position_id) VALUES(?,?,?,?,?,?,?,?,?)");
    $stmt->execute([$b["username"], $hash, $b["full_name"], $b["phone"] ?? null, $b["email"], $b["base_salary"] ?? 0, $b["role_id"] ?? 3, $b["department_id"] ?? null, $b["position_id"] ?? null]);
    out(["id" => $pdo->lastInsertId()]);
  } catch (Exception $e) { err("Trung username/email", 409); }
}
if ($a === "update" && ($m === "PUT" || $m === "POST")) {
  $b = body(); $id = $b["id"] ?? $_GET["id"] ?? 0;
  if (!$id) err("Thieu id");
  $pdo->prepare("UPDATE employees SET full_name=?,phone=?,email=?,base_salary=?,department_id=?,position_id=?,role_id=?,status=? WHERE id=?")
    ->execute([$b["full_name"], $b["phone"] ?? null, $b["email"], $b["base_salary"] ?? 0, $b["department_id"] ?? null, $b["position_id"] ?? null, $b["role_id"] ?? 3, $b["status"] ?? 1, $id]);
  out(["updated" => true]);
}
if ($a === "delete" && ($m === "DELETE" || $m === "POST")) {
  $id = body()["id"] ?? $_GET["id"] ?? 0;
  if (!$id) err("Thieu id");
  $pdo->prepare("DELETE FROM employees WHERE id=?")->execute([$id]);
  out(["deleted" => true]);
}
if ($a === "departments" && $m === "GET") { out($pdo->query("SELECT * FROM departments")->fetchAll(PDO::FETCH_ASSOC)); }
if ($a === "positions" && $m === "GET") { out($pdo->query("SELECT * FROM positions")->fetchAll(PDO::FETCH_ASSOC)); }
err("Unknown employees action");
?>
