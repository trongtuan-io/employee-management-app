<?php
require_once __DIR__ . "/../employee-api/db.php";
$a = $_GET["action"] ?? ""; $m = $_SERVER["REQUEST_METHOD"];

if ($a === "login" && $m === "POST") {
  $b = body();
  $stmt = $pdo->prepare("SELECT e.*, r.name AS role_name FROM employees e LEFT JOIN roles r ON e.role_id=r.id WHERE e.username=? LIMIT 1");
  $stmt->execute([$b["username"] ?? ""]);
  $u = $stmt->fetch(PDO::FETCH_ASSOC);
  if (!$u || !password_verify($b["password"] ?? "", $u["password_hash"])) err("Sai tai khoan hoac mat khau", 401);
  if ((int)$u["status"] === 0) err("Tai khoan da bi khoa", 403);
  unset($u["password_hash"]);
  $u["token"] = "demo-token-" . $u["id"];
  out($u);
}
if ($a === "register" && $m === "POST") {
  $b = body();
  if (empty($b["username"]) || empty($b["password"]) || empty($b["full_name"]) || empty($b["email"])) err("Thieu username/password/full_name/email");
  $hash = password_hash($b["password"], PASSWORD_BCRYPT);
  try {
    $stmt = $pdo->prepare("INSERT INTO employees(username,password_hash,full_name,phone,email,base_salary,status,role_id,department_id,position_id) VALUES(?,?,?,?,?,?,?,?,?,?)");
    $stmt->execute([$b["username"], $hash, $b["full_name"], $b["phone"] ?? null, $b["email"], $b["base_salary"] ?? 0, 1, $b["role_id"] ?? 3, $b["department_id"] ?? null, $b["position_id"] ?? null]);
    out(["id" => $pdo->lastInsertId()]);
  } catch (Exception $e) { err("Username/Email da ton tai", 409); }
}
if ($a === "change-password" && $m === "POST") {
  $b = body();
  $stmt = $pdo->prepare("SELECT * FROM employees WHERE id=?");
  $stmt->execute([$b["employee_id"]]);
  $u = $stmt->fetch(PDO::FETCH_ASSOC);
  if (!$u || !password_verify($b["old_password"] ?? "", $u["password_hash"])) err("Mat khau cu sai", 401);
  $new = password_hash($b["new_password"], PASSWORD_BCRYPT);
  $pdo->prepare("UPDATE employees SET password_hash=? WHERE id=?")->execute([$new, $b["employee_id"]]);
  out(["updated" => true]);
}
err("Unknown auth action");
?>
