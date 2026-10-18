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
  require_once __DIR__ . "/../employee-api/sms_config.php";
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
  if (!$u) err(($channel === "email" ? "Email" : "So dien thoai") . " khong ton tai trong he thong", 404);
  $code = str_pad((string)random_int(0, 999999), 6, "0", STR_PAD_LEFT);
  $pdo->prepare("UPDATE password_resets SET used=1 WHERE (email=? OR phone=?) AND used=0")->execute([$u["email"], $u["phone"]]);
  $pdo->prepare("INSERT INTO password_resets(email, phone, code, expires_at) VALUES(?,?,?,DATE_ADD(NOW(), INTERVAL 10 MINUTE))")->execute([$u["email"], $u["phone"], $code]);
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
    $stmt = $pdo->prepare("SELECT * FROM password_resets WHERE email=? AND code=? AND used=0 AND expires_at > NOW() ORDER BY id DESC LIMIT 1");
    $stmt->execute([trim($b["email"]), $code]);
  } else {
    $stmt = $pdo->prepare("SELECT * FROM password_resets WHERE phone=? AND code=? AND used=0 AND expires_at > NOW() ORDER BY id DESC LIMIT 1");
    $stmt->execute([trim($b["phone"] ?? ""), $code]);
  }
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
if ($a === "reset-password-by-phone" && $m === "POST") {
  // Client da xac thuc SDT qua Firebase Phone Auth (SMS mien phi).
  // Luu y bao cao: production can verify Firebase ID token server-side.
  $b = body();
  $phone = trim($b["phone"] ?? "");
  $fbUid = trim($b["firebase_uid"] ?? "");
  if ($phone === "" || $fbUid === "") err("Thieu phone/firebase_uid");
  if (strlen($b["new_password"] ?? "") < 6) err("Mat khau moi it nhat 6 ky tu");
  $stmt = $pdo->prepare("SELECT id FROM employees WHERE phone=? LIMIT 1");
  $stmt->execute([$phone]);
  if (!$stmt->fetch()) err("So dien thoai khong ton tai trong he thong", 404);
  $hash = password_hash($b["new_password"], PASSWORD_BCRYPT);
  $pdo->prepare("UPDATE employees SET password_hash=? WHERE phone=?")->execute([$hash, $phone]);
  $pdo->prepare("UPDATE password_resets SET used=1 WHERE phone=? AND used=0")->execute([$phone]);
  out(["updated" => true]);
}
err("Unknown auth action");
?>
