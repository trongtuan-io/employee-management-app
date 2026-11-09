<?php
require_once __DIR__ . "/db.php";
require_once __DIR__ . "/auth_guard.php";
require_once __DIR__ . "/positions_logic.php";
require_once __DIR__ . "/employee_validation.php";
$a = $_GET["action"] ?? ""; $m = $_SERVER["REQUEST_METHOD"];

if ($a === "login" && $m === "POST") {
  $b = body();
  $username = is_string($b["username"] ?? null) ? trim($b["username"]) : "";
  $password = is_string($b["password"] ?? null) ? $b["password"] : "";
  $ip = $_SERVER["REMOTE_ADDR"] ?? "";
  // Khoa tam thoi khi dang nhap sai qua nhieu (chong brute-force mat khau).
  $pdo->exec("CREATE TABLE IF NOT EXISTS login_attempts (id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, username VARCHAR(100) NOT NULL, ip VARCHAR(45) NOT NULL, attempted_at DATETIME NOT NULL, INDEX idx_la_user (username, attempted_at)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
  $st = $pdo->prepare("SELECT COUNT(*) FROM login_attempts WHERE username=? AND ip=? AND attempted_at > DATE_SUB(NOW(), INTERVAL 10 MINUTE)");
  $st->execute([$username, $ip]);
  if ((int)$st->fetchColumn() >= 10) err("Dang nhap sai qua nhieu lan, vui long thu lai sau 10 phut", 429);
  $stmt = $pdo->prepare("SELECT e.*, r.name AS role_name, d.name AS department_name FROM employees e LEFT JOIN roles r ON e.role_id=r.id LEFT JOIN departments d ON d.id=e.department_id WHERE e.username=? LIMIT 1");
  $stmt->execute([$username]);
  $u = $stmt->fetch(PDO::FETCH_ASSOC);
  // User khong ton tai van chay password_verify voi hash gia de thoi gian phan hoi deu nhau.
  $hash = $u["password_hash"] ?? '$2y$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy';
  if (!$u || !password_verify($password, $hash)) {
    $pdo->prepare("INSERT INTO login_attempts(username, ip, attempted_at) VALUES(?,?,NOW())")->execute([$username, $ip]);
    err("Sai tai khoan hoac mat khau", 401);
  }
  if ((int)$u["status"] === 0) err("Tai khoan da bi khoa", 403);
  $pdo->prepare("DELETE FROM login_attempts WHERE username=? AND ip=?")->execute([$username, $ip]);
  unset($u["password_hash"]);
  $u["token"] = auth_issue_token($pdo, $u["id"]); // Token that ngau nhien, server luu hash (auth_guard.php)
  out($u);
}
if ($a === "register" && $m === "POST") {
  // Tao tai khoan la viec cua admin/manager dang dang nhap (app mo man nay tu AdminActivity).
  $me = require_role($pdo, ["admin", "manager"]);
  $b = body();
  if ((int)($b["role_id"] ?? 3) !== 3 && $me["role"] !== "admin") err("Chi admin moi tao duoc tai khoan admin/manager", 403);
  // Manager (MOI phong) chi tao nhan vien thuong cho PHONG MINH; tao tai khoan
  // cho phong khac la viec cua admin (siet chat 09/11/2026).
  if ($me["role"] === "manager") {
    $b["role_id"] = 3;
    $b["department_id"] = (int)$me["department_id"];
  }
  if (empty($b["username"]) || empty($b["password"]) || empty($b["full_name"]) || empty($b["email"])) err("Thieu username/password/full_name/email");
  if (!is_string($b["password"]) || strlen($b["password"]) < 6) err("Mat khau it nhat 6 ky tu");
  if (isset($b["base_salary"]) && (!is_scalar($b["base_salary"]) || !is_numeric($b["base_salary"]) || (float)$b["base_salary"] < 0)) err("Luong co ban khong hop le", 422);
  // Chuan hoa + validate dinh dang nhu duong import (truoc day email/username sai dinh dang van luu).
  try {
    $b["username"] = employee_username($b["username"]);
    $b["full_name"] = employee_name($b["full_name"]);
    $b["email"] = employee_email($b["email"]);
    if (isset($b["phone"]) && $b["phone"] !== null && $b["phone"] !== "") $b["phone"] = employee_phone($b["phone"]);
  } catch (InvalidArgumentException $e) { err($e->getMessage(), 422); }
  positions_ensure($pdo);
  $posId = validate_position_for_department($pdo, $b["position_id"] ?? null, $b["department_id"] ?? null);
  $hash = password_hash($b["password"], PASSWORD_BCRYPT);
  try {
    $stmt = $pdo->prepare("INSERT INTO employees(username,password_hash,full_name,phone,email,base_salary,status,role_id,department_id,position_id) VALUES(?,?,?,?,?,?,?,?,?,?)");
    $stmt->execute([$b["username"], $hash, $b["full_name"], $b["phone"] ?? null, $b["email"], $b["base_salary"] ?? 0, 1, $b["role_id"] ?? 3, $b["department_id"] ?? null, $posId]);
    out(["id" => $pdo->lastInsertId()]);
  } catch (Exception $e) {
    if ($e instanceof PDOException && $e->getCode() === "23000") err("Username/Email da ton tai", 409);
    err("Khong tao duoc tai khoan", 500);
  }
}
if ($a === "change-password" && $m === "POST") {
  $me = require_login($pdo); // Chi duoc doi mat khau cua chinh minh
  $b = body();
  if ((int)($b["employee_id"] ?? 0) !== (int)$me["id"]) err("Khong co quyen doi mat khau tai khoan khac", 403);
  $stmt = $pdo->prepare("SELECT * FROM employees WHERE id=?");
  $stmt->execute([$b["employee_id"]]);
  $u = $stmt->fetch(PDO::FETCH_ASSOC);
  if (!$u || !password_verify(is_string($b["old_password"] ?? null) ? $b["old_password"] : "", $u["password_hash"])) err("Mat khau cu sai", 401);
  if (!is_string($b["new_password"] ?? null) || strlen($b["new_password"]) < 6) err("Mat khau moi it nhat 6 ky tu");
  if ($b["new_password"] === ($b["old_password"] ?? null)) err("Mat khau moi phai khac mat khau cu");
  $new = password_hash($b["new_password"], PASSWORD_BCRYPT);
  $pdo->prepare("UPDATE employees SET password_hash=? WHERE id=?")->execute([$new, $b["employee_id"]]);
  // Doi mat khau xong: thu hoi cac phien dang nhap KHAC cua tai khoan (giu phien hien tai).
  $pdo->prepare("DELETE FROM api_tokens WHERE employee_id=? AND token_hash<>?")
      ->execute([$me["id"], hash("sha256", auth_bearer_token())]);
  out(["updated" => true]);
}
if ($a === "forgot-password" && $m === "POST") {
  require_once __DIR__ . "/mail_config.php";
  require_once __DIR__ . "/sms_config.php";
  $b = body();
  // 2 kenh: email hoac phone. Bat buoc phai khop voi DB.
  $email = trim($b["email"] ?? "");
  $phone = trim($b["phone"] ?? "");
  if ($email === "" && $phone === "") err("Vui long nhap email hoac so dien thoai");
  if ($email !== "") {
    $stmt = $pdo->prepare("SELECT id, full_name, email, phone FROM employees WHERE email=? LIMIT 1");
    $stmt->execute([$email]);
    $channel = "email";
  } else {
    $stmt = $pdo->prepare("SELECT id, full_name, email, phone FROM employees WHERE phone=? LIMIT 1");
    $stmt->execute([$phone]);
    $channel = "sms";
  }
  $u = $stmt->fetch(PDO::FETCH_ASSOC);
  if (!$u) {
    // Khong tiet lo email/SDT co ton tai hay khong: tra cung mot dang response nhu khi gui that.
    out(["sent" => true, "channel" => $channel, $channel === "email" ? "mail_ok" : "sms_ok" => false]);
  }
  // Chong spam: toi da 5 ma moi gio cho mot tai khoan (ke ca ma chua dung).
  $st = $pdo->prepare("SELECT COUNT(*) FROM password_resets WHERE (email=? OR phone=?) AND created_at > DATE_SUB(NOW(), INTERVAL 1 HOUR)");
  $st->execute([$u["email"], $u["phone"]]);
  if ((int)$st->fetchColumn() >= 5) {
    out(["sent" => true, "channel" => $channel, $channel === "email" ? "mail_ok" : "sms_ok" => false]);
  }
  $code = str_pad((string)random_int(0, 999999), 6, "0", STR_PAD_LEFT);
  $pdo->prepare("UPDATE password_resets SET used=1 WHERE (email=? OR phone=?) AND used=0")->execute([$u["email"], $u["phone"]]);
  // Chi luu HASH cua ma trong DB (ma tho chi ton tai trong email/SMS da gui).
  $pdo->prepare("INSERT INTO password_resets(email, phone, code, expires_at) VALUES(?,?,?,DATE_ADD(NOW(), INTERVAL 10 MINUTE))")->execute([$u["email"], $u["phone"], hash("sha256", $code)]);
  if ($channel === "email") {
    $ok = send_reset_code($u["email"], $u["full_name"], $code);
    $res = ["sent" => true, "channel" => "email", "mail_ok" => $ok];
    if (DEBUG_SHOW_CODE) $res["debug_code"] = $code; // TAT khi demo that
  } else {
    $ok = send_sms_code($u["phone"], $code);
    $res = ["sent" => true, "channel" => "sms", "sms_ok" => $ok];
    if (SMS_DEBUG_SHOW_CODE) $res["debug_code"] = $code; // TAT khi demo that
  }
  out($res);
}
if ($a === "verify-code" && $m === "POST") {
  $b = body();
  $code = trim($b["code"] ?? "");
  if (isset($b["email"]) && trim($b["email"]) !== "") {
    $stmt = $pdo->prepare("SELECT * FROM password_resets WHERE email=? AND used=0 AND expires_at > NOW() ORDER BY id DESC LIMIT 1");
    $stmt->execute([trim($b["email"])]);
  } else {
    $stmt = $pdo->prepare("SELECT * FROM password_resets WHERE phone=? AND used=0 AND expires_at > NOW() ORDER BY id DESC LIMIT 1");
    $stmt->execute([trim($b["phone"] ?? "")]);
  }
  $r = $stmt->fetch(PDO::FETCH_ASSOC);
  if (!$r) err("Ma xac nhan sai hoac het han", 401);
  if (!hash_equals($r["code"], hash("sha256", $code))) {
    // Chong do ma: nhap sai 5 lan thi huy ma nay, bat buoc xin ma moi.
    $att = (int)($r["attempts"] ?? 0) + 1;
    $pdo->prepare("UPDATE password_resets SET attempts=?, used=? WHERE id=?")->execute([$att, $att >= 5 ? 1 : 0, $r["id"]]);
    err("Ma xac nhan sai hoac het han", 401);
  }
  $token = bin2hex(random_bytes(20));
  $pdo->prepare("UPDATE password_resets SET reset_token=? WHERE id=?")->execute([hash("sha256", $token), $r["id"]]);
  out(["reset_token" => $token]);
}
if ($a === "reset-password" && $m === "POST") {
  $b = body();
  if (strlen($b["new_password"] ?? "") < 6) err("Mat khau moi it nhat 6 ky tu");
  $stmt = $pdo->prepare("SELECT * FROM password_resets WHERE reset_token=? AND used=0 AND expires_at > NOW() LIMIT 1");
  $stmt->execute([hash("sha256", $b["reset_token"] ?? "")]);
  $r = $stmt->fetch(PDO::FETCH_ASSOC);
  if (!$r) err("Phien doi mat khau het han, vui long lam lai", 401);
  $hash = password_hash($b["new_password"], PASSWORD_BCRYPT);
  $pdo->prepare("UPDATE employees SET password_hash=? WHERE email=?")->execute([$hash, $r["email"]]);
  $pdo->prepare("UPDATE password_resets SET used=1 WHERE id=?")->execute([$r["id"]]);
  // Dat lai mat khau xong: thu hoi MOI phien dang nhap cua tai khoan (phai dang nhap lai).
  $pdo->prepare("DELETE tk FROM api_tokens tk JOIN employees e ON tk.employee_id=e.id WHERE e.email=?")->execute([$r["email"]]);
  out(["updated" => true]);
}
if ($a === "logout" && $m === "POST") {
  auth_revoke_token($pdo);
  out(["logged_out" => true]);
}
err("Unknown auth action");
?>
