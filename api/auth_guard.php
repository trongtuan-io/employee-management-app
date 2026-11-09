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
  $stmt = $pdo->prepare("SELECT e.id, e.username, e.full_name, e.department_id, e.status, LOWER(r.name) AS role,
      d.name AS department_name
    FROM employees e
    LEFT JOIN roles r ON r.id = e.role_id
    LEFT JOIN departments d ON d.id = e.department_id
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

// ===== Phan quyen theo PHONG BAN (bo sung 2026-11-09, xem ThietKe_PhanQuyen_TheoPhong) =====
// Quyen = Role x Phong ban. Phong xac dinh bang MA phong he thong (departments.name,
// UNIQUE va do seed/migration tao: IT, HR, ACC, SALES, MKT, OPS, CS) lay tu token,
// khong bao gio lay tu tham so client gui.
// ACC = Ke toan - Tai chinh, HR = Nhan su.

function user_dept($u) {
  return strtoupper(trim((string)($u["department_name"] ?? "")));
}

function is_dept($u, $dept) {
  return user_dept($u) === strtoupper($dept);
}

// Nguoi goi co thuoc phong Ke toan khong (ca truong phong lan nhan vien).
function is_accounting($u) { return is_dept($u, "ACC"); }
function is_hr($u) { return is_dept($u, "HR"); }

// Quan ly nhan su toan cong ty: admin hoac Truong phong HR.
function is_hr_manager($u) {
  return $u["role"] === "admin" || (is_hr($u) && $u["role"] === "manager");
}

// Phan cong nghiep vu LUONG trong phong Ke toan (Tuan chot 2026-11-09):
// - SOAN NHAP + SUA KHOAN + XEM luong toan cong ty de soan: Admin + phong Ke
//   toan (ca Ke toan truong lan NV Ke toan).
// - CHOT bang luong: chi Admin + KE TOAN TRUONG (manager phong ACC).
// Nhan vien cac phong khac (ke ca Truong phong HR) chi xem bang luong CUA
// CHINH MINH sau khi da chot.
function is_payroll_manager($u) {
  return $u["role"] === "admin" || (is_accounting($u) && $u["role"] === "manager");
}

// Quyen thao tac LUONG: generate/adjust cho ca phong Ke toan; finalize chi
// Admin + Ke toan truong.
function require_payroll(PDO $pdo, $finalize = false) {
  $u = require_login($pdo);
  if ($u["role"] === "admin") return $u;
  if (is_accounting($u)) {
    if ($finalize && $u["role"] !== "manager") {
      err("Chi Ke toan truong moi duoc chot bang luong", 403);
    }
    return $u;
  }
  err("Chi phong Ke toan moi duoc thao tac bang luong", 403);
}

// Nguoi goi co duoc XEM bang luong cua nguoi khac khong (Admin + phong Ke toan,
// de phuc vu soan nhap; Truong phong HR thi KHONG).
function can_view_others_salary($u) {
  return $u["role"] === "admin" || is_accounting($u);
}

// ===== Thong bao trong app (dung chung cho leaves_salaries.php va attendances.php) =====
function notify_ensure_table(PDO $pdo) {
  static $done = false;
  if ($done) return;
  $done = true;
  $pdo->exec("CREATE TABLE IF NOT EXISTS notifications (
    id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL,
    type VARCHAR(30) NOT NULL,
    title VARCHAR(160) NOT NULL,
    body VARCHAR(400) NULL,
    ref_id INT NULL,
    is_read TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_read (user_id, is_read)
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
}

function notify_push(PDO $pdo, $userId, $type, $title, $body, $refId = null) {
  if ((int)$userId <= 0) return;
  notify_ensure_table($pdo);
  $pdo->prepare("INSERT INTO notifications(user_id, type, title, body, ref_id) VALUES(?,?,?,?,?)")
    ->execute([(int)$userId, $type, $title, $body, $refId === null ? null : (int)$refId]);
}

function notify_vnd($v) {
  return number_format((float)$v, 0, ",", ".") . " ₫";
}

// Gui thong bao cho TAP NGUOI DUYET cua 1 nhan vien: Admin + manager cung
// phong voi nhan vien do + toan phong HR. Khong gui cho chinh nhan vien do.
// Dung cho don nghi moi va don giai trinh cham cong moi.
function notify_approvers(PDO $pdo, $employeeId, $type, $title, $body, $refId = null) {
  $st = $pdo->prepare("SELECT department_id FROM employees WHERE id=?");
  $st->execute([(int)$employeeId]);
  $deptId = $st->fetchColumn();
  if ($deptId === false) return;
  $recips = $pdo->prepare("SELECT DISTINCT e.id FROM employees e LEFT JOIN departments d ON d.id=e.department_id
    WHERE e.status=1 AND e.id<>? AND (e.role_id=1 OR (e.role_id=2 AND e.department_id=?) OR d.name='HR')");
  $recips->execute([(int)$employeeId, (int)$deptId]);
  foreach ($recips->fetchAll(PDO::FETCH_COLUMN) as $rid) {
    notify_push($pdo, (int)$rid, $type, $title, $body, $refId);
  }
}

function auth_revoke_token(PDO $pdo) {
  $t = auth_bearer_token();
  if ($t === "") return;
  auth_ensure_table($pdo);
  $pdo->prepare("DELETE FROM api_tokens WHERE token_hash = ?")->execute([hash("sha256", $t)]);
}
?>
