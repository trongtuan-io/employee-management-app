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
if ($a === "forgot-password" && $m === "POST") {
  require_once __DIR__ . "/../employee-api/mail_config.php";
  $b = body();
  $email = trim($b["email"] ?? "");
  if ($email === "") err("Vui long nhap email");
  $stmt = $pdo->prepare("SELECT id, full_name FROM employees WHERE email=? LIMIT 1");
  $stmt->execute([$email]);
  $u = $stmt->fetch(PDO::FETCH_ASSOC);
  // Email phai ton tai trong DB moi duoc gui ma
  if (!$u) err("Email khong ton tai trong he thong", 404);
  $code = str_pad((string)random_int(0, 999999), 6, "0", STR_PAD_LEFT);
  $pdo->prepare("UPDATE password_resets SET used=1 WHERE email=? AND used=0")->execute([$email]);
  $pdo->prepare("INSERT INTO password_resets(email, code, expires_at) VALUES(?,?,DATE_ADD(NOW(), INTERVAL 10 MINUTE))")->execute([$email, $code]);
  $ok = send_reset_code($email, $u["full_name"], $code);
  $res = ["sent" => true, "mail_ok" => $ok];
  if (DEBUG_SHOW_CODE) $res["debug_code"] = $code; // TAT khi demo that (chuyen false trong mail_config.php)
  out($res);
}
if ($a === "verify-code" && $m === "POST") {
  $b = body();
  $stmt = $pdo->prepare("SELECT * FROM password_resets WHERE email=? AND code=? AND used=0 AND expires_at > NOW() ORDER BY id DESC LIMIT 1");
  $stmt->execute([trim($b["email"] ?? ""), trim($b["code"] ?? "")]);
  $r = $stmt->fetch(PDO::FETCH_ASSOC);
  if (!$r) err("Ma xac nhan sai hoac het han", 401);
  $token = bin2hex(random_bytes(20));
  $pdo->prepare("UPDATE password_resets SET reset_token=? WHERE id=?")->execute([$token, $r["id"]]);
  out(["reset_token" => $token]);
}
if ($a === "reset-password" && $m === "POST") {
  $b = body();
  if (strlen($b["new_password"] ?? "") < 6) err("Mat khau moi it nhat 6 ky tu");
  $stmt = $pdo->prepare("SELECT * FROM password_resets WHERE reset_token=? AND used=0 AND expires_at > NOW() LIMIT 1");
  $stmt->execute([$b["reset_token"] ?? ""]);
  $r = $stmt->fetch(PDO::FETCH_ASSOC);
  if (!$r) err("Phien doi mat khau het han, vui long lam lai", 401);
  $hash = password_hash($b["new_password"], PASSWORD_BCRYPT);
  $pdo->prepare("UPDATE employees SET password_hash=? WHERE email=?")->execute([$hash, $r["email"]]);
  $pdo->prepare("UPDATE password_resets SET used=1 WHERE id=?")->execute([$r["id"]]);
  out(["updated" => true]);
}
err("Unknown auth action");
?>
