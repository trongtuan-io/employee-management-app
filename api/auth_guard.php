<?php
// Xac thuc bang token THAT (sua loi "demo-token"):
// - Login cap token ngau nhien 64 ky tu hex; server chi luu ban hash sha256 trong bang api_tokens.
// - Android da co san co che gui header "Authorization: Bearer <token>" o moi request
//   (ApiClient + TokenManager), server chi can kiem tra token thay vi tin requester_id client gui.
// - Danh tinh (id, role, phong ban) cua nguoi goi LUON lay tu token, khong lay tu body/query.

function auth_ensure_table(PDO $pdo) {
  static $done = false;
  if ($done) return;
  $done = true;
  // Tu tao bang neu chua chay migration 002 (de demo khong sup vi thieu bang).
  $pdo->exec("CREATE TABLE IF NOT EXISTS api_tokens (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    employee_id INT NOT NULL,
    token_hash CHAR(64) NOT NULL UNIQUE,
    expires_at DATETIME NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_api_tokens_employee (employee_id)
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
}

function auth_issue_token(PDO $pdo, $employeeId) {
  auth_ensure_table($pdo);
  $token = bin2hex(random_bytes(32));
  $pdo->prepare("DELETE FROM api_tokens WHERE expires_at <= NOW()")->execute();
  $pdo->prepare("INSERT INTO api_tokens(employee_id, token_hash, expires_at) VALUES(?,?, DATE_ADD(NOW(), INTERVAL 7 DAY))")
      ->execute([(int)$employeeId, hash("sha256", $token)]);
  return $token;
}

function auth_bearer_token() {
  $h = "";
  if (!empty($_SERVER["HTTP_AUTHORIZATION"])) $h = $_SERVER["HTTP_AUTHORIZATION"];
  elseif (!empty($_SERVER["REDIRECT_HTTP_AUTHORIZATION"])) $h = $_SERVER["REDIRECT_HTTP_AUTHORIZATION"];
  elseif (function_exists("getallheaders")) {
    $hs = getallheaders();
    if (isset($hs["Authorization"])) $h = $hs["Authorization"];
  }
  if (stripos($h, "Bearer ") === 0) return trim(substr($h, 7));
  return "";
}

// Tra ve mang user (id, username, full_name, department_id, role) hoac null neu token khong hop le/het han.
function current_user(PDO $pdo) {
  $t = auth_bearer_token();
  if ($t === "") return null;
  auth_ensure_table($pdo);
  $stmt = $pdo->prepare("SELECT e.id, e.username, e.full_name, e.department_id, e.status, LOWER(r.name) AS role
    FROM employees e
    LEFT JOIN roles r ON r.id = e.role_id
    JOIN api_tokens tk ON tk.employee_id = e.id
    WHERE tk.token_hash = ? AND tk.expires_at > NOW() LIMIT 1");
  $stmt->execute([hash("sha256", $t)]);
  $u = $stmt->fetch(PDO::FETCH_ASSOC);
  if (!$u || (int)$u["status"] === 0) return null;
  return $u;
}

function require_login(PDO $pdo) {
  $u = current_user($pdo);
  if (!$u) err("Chua dang nhap hoac phien dang nhap het han", 401);
  return $u;
}

function require_role(PDO $pdo, array $roles) {
  $u = require_login($pdo);
  if (!in_array($u["role"], $roles, true)) err("Khong co quyen thuc hien thao tac nay", 403);
  return $u;
}

function auth_revoke_token(PDO $pdo) {
  $t = auth_bearer_token();
  if ($t === "") return;
  auth_ensure_table($pdo);
  $pdo->prepare("DELETE FROM api_tokens WHERE token_hash = ?")->execute([hash("sha256", $t)]);
}
?>
