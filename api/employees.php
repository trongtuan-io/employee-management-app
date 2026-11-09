<?php
require_once __DIR__ . "/db.php";
require_once __DIR__ . "/employee_validation.php";
require_once __DIR__ . "/auth_guard.php";
require_once __DIR__ . "/positions_logic.php";
positions_ensure($pdo); // Tu ap dung migration 003 + seed/backfill chuc vu (chi phan con thieu)
$a = $_GET["action"] ?? "list"; $m = $_SERVER["REQUEST_METHOD"];

if ($a === "list" && $m === "GET") {
  // Danh sach nhan vien theo Role x Phong:
  // - admin + phong HR (ca manager lan staff): xem TOAN cong ty.
  // - phong Ke toan (ACC): xem toan cong ty nhung RUT GON (an email/SDT/luong co ban
  //   trong danh sach; xem day du qua salary-list/detail theo quyen luong).
  // - manager phong nghiep vu: chi phong minh. Nhan vien thuong: khong xem.
  $me = require_login($pdo);
  $isGlobalViewer = $me["role"] === "admin" || is_hr($me) || is_accounting($me);
  if (!$isGlobalViewer && $me["role"] !== "manager") err("Khong co quyen xem danh sach nhan vien", 403);
  $listRestricted = is_accounting($me) || (is_hr($me) && $me["role"] === "staff");
  $search = $_GET["search"] ?? "";
  $dept = $_GET["dept_id"] ?? "";
  // Sep phong nghiep vu chi xem duoc nhan vien phong do: ep bo loc phong theo token,
  // bo qua dept_id client gui (admin/HR/Ke toan xem tat ca).
  if ($me["role"] === "manager" && !$isGlobalViewer) $dept = (string)(int)$me["department_id"];
  $sort = $_GET["sort"] ?? "id_desc";
  $searchMode = $_GET["search_mode"] ?? "like";
  if (!in_array($searchMode, ["like", "fulltext"], true)) err("search_mode khong hop le");
  if (!is_string($search) || !is_string($dept) || !is_string($sort)) err("Tham so khong hop le");

  $page = filter_var($_GET["page"] ?? 1, FILTER_VALIDATE_INT);
  $limit = filter_var($_GET["limit"] ?? 20, FILTER_VALIDATE_INT);
  if ($page === false || $limit === false) err("page/limit phai la so nguyen");
  $page = max(1, $page);
  $limit = max(1, min(50, $limit));
  if ($dept !== "" && filter_var($dept, FILTER_VALIDATE_INT, ["options" => ["min_range" => 1]]) === false) {
    err("dept_id khong hop le");
  }
  // Chi nhan cac cot/huong sap xep trong danh sach cho phep.
  // ID la khoa phu de cac nhan vien trung ten khong bi dao giua cac trang.
  $orders = [
    "id_desc" => "e.id DESC",
    "name_asc" => "e.full_name ASC, e.id ASC",
    "name_desc" => "e.full_name DESC, e.id DESC",
    "code_asc" => "e.username ASC, e.id ASC",
    "code_desc" => "e.username DESC, e.id DESC"
  ];
  if (!isset($orders[$sort])) err("sort khong hop le");

  $s = "%" . addcslashes($search, '%_\\') . "%"; // escape ky tu dac biet cua LIKE
  // FULLTEXT needs migration 001; fall back to LIKE for short words/missing index.
  $useFulltext = false;
  if ($searchMode === "fulltext" && trim($search) !== "" && strlen(trim($search)) >= 3) {
    $idx = $pdo->query("SHOW INDEX FROM employees WHERE Key_name='idx_employee_fulltext'");
    $useFulltext = (bool)$idx->fetch(PDO::FETCH_ASSOC);
  }
  if ($useFulltext) {
    $where = " WHERE (MATCH(e.full_name,e.username,e.email) AGAINST(? IN NATURAL LANGUAGE MODE)"
      . " OR e.full_name LIKE ? OR e.username LIKE ? OR e.email LIKE ?)";
    $params = [$search, $s, $s, $s];
  } else {
    $where = " WHERE (e.full_name LIKE ? OR e.username LIKE ? OR e.email LIKE ?)";
    $params = [$s, $s, $s];
  }
  if ($dept !== "") { $where .= " AND e.department_id=?"; $params[] = (int)$dept; }

  try {
    $count = $pdo->prepare("SELECT COUNT(*) FROM employees e" . $where);
    $count->execute($params);
    $total = (int)$count->fetchColumn();
    $totalPages = max(1, (int)ceil($total / $limit));
    $page = min($page, $totalPages);
    $off = ($page - 1) * $limit;
    $sql = "SELECT e.id,e.username,e.full_name,e.phone,e.email,e.base_salary,e.status,d.name AS dept_name,p.title AS position_name,r.name AS role_name FROM employees e LEFT JOIN departments d ON e.department_id=d.id LEFT JOIN positions p ON e.position_id=p.id LEFT JOIN roles r ON e.role_id=r.id"
      . $where . " ORDER BY " . $orders[$sort] . " LIMIT $limit OFFSET $off";
    $stmt = $pdo->prepare($sql);
    $stmt->execute($params);
    $rows = $stmt->fetchAll(PDO::FETCH_ASSOC);
    // Nguoi xem RUT GON (Ke toan, NV Nhan su): an cac truong nhay cam trong danh sach.
    if ($listRestricted) {
      foreach ($rows as &$row) { $row["email"] = null; $row["phone"] = null; $row["base_salary"] = null; }
      unset($row);
    }
    // Giu data la mang de khong lam hong client cu; them metadata rieng.
    echo json_encode([
      "success" => true,
      "data" => $rows,
      "pagination" => ["page" => $page, "limit" => $limit, "total" => $total, "total_pages" => $totalPages]
    ]);
    exit;
  } catch (PDOException $e) {
    err("Khong tai duoc danh sach nhan vien", 500);
  }
}
if ($a === "detail" && $m === "GET") {
  $me = require_login($pdo);
  if ((int)($_GET["id"] ?? 0) !== (int)$me["id"] && !in_array($me["role"], ["admin", "manager"], true)
      && !is_hr($me) && !is_accounting($me)) err("Khong co quyen xem ho so nguoi khac", 403);
  $stmt = $pdo->prepare("SELECT e.*, d.name AS dept_name, p.title AS position_name FROM employees e LEFT JOIN departments d ON e.department_id=d.id LEFT JOIN positions p ON e.position_id=p.id WHERE e.id=?");
  $stmt->execute([$_GET["id"] ?? 0]);
  $r = $stmt->fetch(PDO::FETCH_ASSOC);
  if (!$r) err("Khong tim thay NV", 404);
  // Manager phong nghiep vu chi xem duoc ho so nhan vien phong minh (tru ho so
  // cua chinh minh). Phong HR va Ke toan duoc xem ho so toan cong ty theo nghiep vu.
  if ($me["role"] === "manager" && !is_hr($me) && !is_accounting($me)
      && (int)$r["id"] !== (int)$me["id"]
      && (int)$r["department_id"] !== (int)$me["department_id"]) {
    err("Chi xem duoc ho so nhan vien phong ban minh", 403);
  }
  // Nhan vien phong HR/Ke toan (staff) xem ho so nguoi khac theo nghiep vu duoc,
  // nhan vien thuong thi chi xem ho so cua minh (da chan o tren).
  if ($me["role"] === "staff" && (int)$r["id"] !== (int)$me["id"] && !is_hr($me) && !is_accounting($me)) {
    err("Khong co quyen xem ho so nguoi khac", 403);
  }
  unset($r["password_hash"]); out($r);
}
if ($a === "create" && $m === "POST") {
  $me = require_role($pdo, ["admin", "manager"]);
  $b = body();
  if (empty($b["username"]) || empty($b["full_name"]) || empty($b["email"])) err("Thieu username/full_name/email");
  if (isset($b["base_salary"]) && (!is_scalar($b["base_salary"]) || !is_numeric($b["base_salary"]) || (float)$b["base_salary"] < 0)) err("Luong co ban khong hop le", 422);
  if (isset($b["password"]) && (!is_string($b["password"]) || strlen($b["password"]) < 6)) err("Mat khau it nhat 6 ky tu");
  try {
    $b["username"] = employee_username($b["username"]);
    $b["full_name"] = employee_name($b["full_name"]);
    $b["email"] = employee_email($b["email"]);
    if (isset($b["phone"]) && $b["phone"] !== null && $b["phone"] !== "") $b["phone"] = employee_phone($b["phone"]);
  } catch (InvalidArgumentException $e) { err($e->getMessage(), 422); }
  // Phong Ke toan khong tao nhan vien (tach nhiem vu voi quy trinh luong).
  if ($me["role"] === "manager" && is_accounting($me)) err("Phong Ke toan khong duoc tao tai khoan nhan vien", 403);
  // Manager chi tao nhan vien thuong. Truong phong HR tao duoc cho MOI phong;
  // truong phong nghiep vu chi tao cho phong minh.
  if ($me["role"] === "manager") {
    $b["role_id"] = 3;
    if (!is_hr($me)) $b["department_id"] = (int)$me["department_id"];
  }
  positions_ensure($pdo);
  $posId = validate_position_for_department($pdo, $b["position_id"] ?? null, $b["department_id"] ?? null);
  // Khong con mat khau mac dinh "123456": thieu mat khau thi sinh ngau nhien, tra ve 1 lan.
  $tempPw = null;
  if (empty($b["password"])) { $tempPw = bin2hex(random_bytes(8)); $b["password"] = $tempPw; }
  $hash = password_hash($b["password"], PASSWORD_BCRYPT);
  try {
    $stmt = $pdo->prepare("INSERT INTO employees(username,password_hash,full_name,phone,email,base_salary,role_id,department_id,position_id) VALUES(?,?,?,?,?,?,?,?,?)");
    $stmt->execute([$b["username"], $hash, $b["full_name"], $b["phone"] ?? null, $b["email"], $b["base_salary"] ?? 0, $b["role_id"] ?? 3, $b["department_id"] ?? null, $posId]);
    $res = ["id" => $pdo->lastInsertId()];
    if ($tempPw !== null) $res["temporary_password"] = $tempPw;
    out($res);
  } catch (Exception $e) {
    if ($e instanceof PDOException && $e->getCode() === "23000") err("Trung username/email", 409);
    err("Khong tao duoc nhan vien", 500);
  }
}
if ($a === "update" && ($m === "PUT" || $m === "POST")) {
  $b = body(); $id = (int)($b["id"] ?? $_GET["id"] ?? 0);
  $me = require_login($pdo);
  $reqId = (int)$me["id"]; // Danh tinh lay tu token, bo qua requester_id client gui
  if (!$id) err("Thieu id");
  $reqRole = employee_editor_role($pdo, $reqId);
  $isManager = in_array($reqRole, ["admin", "manager"], true);
  if (!$reqRole || ($reqId !== $id && !$isManager)) err("Khong co quyen", 403);
  $stmt = $pdo->prepare("SELECT * FROM employees WHERE id=? LIMIT 1");
  $stmt->execute([$id]);
  $cur = $stmt->fetch(PDO::FETCH_ASSOC);
  if (!$cur) err("Khong tim thay nhan vien", 404);
  $hrManager = is_hr_manager($me);
  // Phong Ke toan khong sua ho so nguoi khac (chi tu sua ten hien thi cua minh
  // theo nhanh staff ben duoi).
  if ($me["role"] === "manager" && is_accounting($me) && $reqId !== $id) {
    err("Phong Ke toan khong duoc sua ho so nhan vien", 403);
  }
  // Manager phong nghiep vu chi sua duoc nhan vien phong minh.
  // Admin va Truong phong HR sua duoc ho so toan cong ty.
  if ($me["role"] === "manager" && !$hrManager && $reqId !== $id
      && (int)$cur["department_id"] !== (int)$me["department_id"]) {
    err("Chi sua duoc ho so nhan vien phong ban minh", 403);
  }
  // Manager phong nghiep vu khong duoc chuyen nhan vien sang phong khac.
  // Admin/Truong phong HR duoc chuyen phong (viec cua Nhan su).
  if ($me["role"] === "manager" && !$hrManager && array_key_exists('department_id', $b) && $b['department_id'] !== null
      && (int)$b['department_id'] !== (int)$me["department_id"]) {
    err("Chi duoc giu nhan vien trong phong ban minh", 403);
  }
  // Manager phong nghiep vu chi sua ho so nhan vien THUONG (khong sua manager/admin
  // khac cung phong). Truong phong HR sua duoc ca staff lan manager (tru admin).
  if ($me["role"] === "manager" && !$hrManager && $reqId !== $id) {
    $rq = $pdo->prepare("SELECT LOWER(name) AS role FROM roles WHERE id=?");
    $rq->execute([$cur["role_id"]]);
    if ((($rq->fetch(PDO::FETCH_ASSOC) ?: [])["role"] ?? "") !== "staff") err("Chi sua duoc ho so nhan vien thuong", 403);
  }
  // Truong phong HR khong sua duoc tai khoan admin.
  if ($me["role"] === "manager" && $hrManager && $reqId !== $id) {
    $rq = $pdo->prepare("SELECT LOWER(name) AS role FROM roles WHERE id=?");
    $rq->execute([$cur["role_id"]]);
    if ((($rq->fetch(PDO::FETCH_ASSOC) ?: [])["role"] ?? "") === "admin") err("Khong duoc sua tai khoan admin", 403);
  }
  try {
    $name = employee_name($b["full_name"] ?? $cur["full_name"]);
    if ($isManager) {
      // Null fields omitted by Gson leave existing data untouched.
      $email = array_key_exists('email', $b) && $b['email'] !== null
        ? employee_email($b['email']) : $cur['email'];
      $phone = array_key_exists('phone', $b) && $b['phone'] !== null
        ? employee_phone($b['phone']) : $cur['phone'];
      $dept = array_key_exists('department_id', $b) ? employee_optional_fk($pdo, $b['department_id'], 'departments') : $cur['department_id'];
      $pos = array_key_exists('position_id', $b) ? employee_optional_fk($pdo, $b['position_id'], 'positions') : $cur['position_id'];
      // Client gui 0 (placeholder "khong chi dinh") thi giu nguyen gia tri cu, khong xoa phong/chuc vu.
      if (array_key_exists('department_id', $b) && (int)($b['department_id'] ?? 0) === 0) $dept = $cur['department_id'];
      if (array_key_exists('position_id', $b) && (int)($b['position_id'] ?? 0) === 0) $pos = $cur['position_id'];
      // Chuc vu phai thuoc phong ban (sau khi doi) cua nhan vien, hoac la chuc vu dung chung.
      if (array_key_exists('position_id', $b) || array_key_exists('department_id', $b)) {
        positions_ensure($pdo);
        $pos = validate_position_for_department($pdo, $pos, $dept);
      }
      $hire = array_key_exists('hire_date', $b) ? employee_hire_date($b['hire_date']) : ($cur['hire_date'] ?? null);
      // Luong co ban: CHI admin va Truong phong HR duoc doi (ke toan chi duoc xem).
      // Truoc day UPDATE khong co cot nay nen luong chi dat duoc luc tao tai khoan.
      $salary = $cur['base_salary'];
      if (array_key_exists('base_salary', $b) && $b['base_salary'] !== null) {
        if (!$hrManager) err("Chi admin va Truong phong HR moi duoc doi luong co ban", 403);
        if (!is_numeric($b['base_salary']) || (float)$b['base_salary'] < 0) err("Luong co ban khong hop le", 422);
        $salary = (float)$b['base_salary'];
      }
      $pdo->prepare("UPDATE employees SET full_name=?,email=?,phone=?,department_id=?,position_id=?,hire_date=?,base_salary=? WHERE id=?")
        ->execute([$name, $email, $phone, $dept, $pos, $hire, $salary, $id]);
    } else {
      $pdo->prepare("UPDATE employees SET full_name=? WHERE id=?")->execute([$name, $id]);
    }
    out(["updated" => true]);
  } catch (InvalidArgumentException $e) { err($e->getMessage(), 422);
  } catch (PDOException $e) {
    if ($e->getCode() === '23000') err("Email da ton tai", 409);
    err("Khong cap nhat duoc ho so. Kiem tra migration hire_date", 500);
  }
}
if ($a === "upload-avatar" && $m === "POST") {
  $empId = (int)($_POST["employee_id"] ?? 0);
  $me = require_login($pdo);
  $reqId = (int)$me["id"]; // Danh tinh lay tu token
  if (!$empId) err("Thieu employee_id");
  $stmt = $pdo->prepare("SELECT r.name FROM employees e LEFT JOIN roles r ON r.id=e.role_id WHERE e.id=? LIMIT 1");
  $stmt->execute([$reqId]);
  $reqRole = strtolower(($stmt->fetch(PDO::FETCH_ASSOC) ?: [])["name"] ?? "");
  $isAdmin = in_array($reqRole, ["admin", "manager"]);
  if ($reqId !== $empId && !$isAdmin) err("Khong co quyen", 403);
  // Manager up avatar ho thi cung chi trong phong minh.
  if ($reqRole === "manager" && $reqId !== $empId) {
    $dq = $pdo->prepare("SELECT department_id FROM employees WHERE id IN (?,?)");
    $dq->execute([$reqId, $empId]);
    $rows = $dq->fetchAll(PDO::FETCH_COLUMN);
    if (count($rows) === 2 && (int)$rows[0] !== (int)$rows[1]) err("Chi up duoc avatar cho nhan vien phong minh", 403);
  }
  if (!isset($_FILES["avatar"]) || $_FILES["avatar"]["error"] !== UPLOAD_ERR_OK) err("Chua chon anh");
  $f = $_FILES["avatar"];
  $mime = (new finfo(FILEINFO_MIME_TYPE))->file($f["tmp_name"]);
  $extensions = ["image/jpeg" => "jpg", "image/png" => "png", "image/webp" => "webp"];
  if (!isset($extensions[$mime]) || !@getimagesize($f["tmp_name"])) err("Chi nhan anh JPG/PNG/WebP", 422);
  $ext = $extensions[$mime];
  if ($f["size"] > 2 * 1024 * 1024) err("Anh toi da 2MB");
  $dir = __DIR__ . "/uploads/avatars";
  if (!is_dir($dir)) mkdir($dir, 0755, true);
  $name = "emp_" . $empId . "_" . bin2hex(random_bytes(8)) . "." . $ext;
  if (!move_uploaded_file($f["tmp_name"], $dir . "/" . $name)) err("Luu anh that bai", 500);
  $url = "uploads/avatars/" . $name;
  $old = $pdo->prepare("SELECT avatar_url FROM employees WHERE id=?");
  $old->execute([$empId]);
  $oldUrl = ($old->fetch(PDO::FETCH_ASSOC) ?: [])["avatar_url"] ?? null;
  $pdo->prepare("UPDATE employees SET avatar_url=? WHERE id=?")->execute([$url, $empId]);
  // Xoa file avatar cu cua chinh nhan vien nay (tranh rac dia).
  if ($oldUrl && $oldUrl !== $url && strpos($oldUrl, "uploads/avatars/") === 0) {
    $oldPath = __DIR__ . "/" . $oldUrl;
    if (is_file($oldPath)) @unlink($oldPath);
  }
  out(["avatar_url" => $url]);
}
if ($a === "set-status" && ($m === "PUT" || $m === "POST")) {
  // Khoa/mo tai khoan. Admin: tat ca phong ban. Manager: chi staff cung phong.
  $b = body();
  $id = (int)($b["id"] ?? 0);
  $me = require_login($pdo);
  $reqId = (int)$me["id"]; // Danh tinh lay tu token
  $status = (int)($b["status"] ?? 1);
  if (!$id) err("Thieu id");
  if ($id === $reqId) err("Khong the tu khoa tai khoan cua minh", 403);
  $q = $pdo->prepare("SELECT e.department_id, LOWER(r.name) AS role FROM employees e LEFT JOIN roles r ON r.id=e.role_id WHERE e.id=? LIMIT 1");
  $q->execute([$reqId]);
  $req = $q->fetch(PDO::FETCH_ASSOC);
  if (!$req || !in_array($req["role"], ["admin", "manager"])) err("Chi admin/manager duoc khoa mo tai khoan", 403);
  $q->execute([$id]);
  $target = $q->fetch(PDO::FETCH_ASSOC);
  if (!$target) err("Khong tim thay nhan vien", 404);
  if ($req["role"] === "manager") {
    // Phong Ke toan khong khoa/mo tai khoan.
    if (is_accounting($me)) err("Phong Ke toan khong duoc khoa/mo tai khoan", 403);
    // Truong phong HR khoa/mo duoc toan cong ty (tru tai khoan admin).
    if (is_hr_manager($me)) {
      if ($target["role"] === "admin") err("Khong duoc khoa tai khoan admin", 403);
    } else {
      if ((int)$target["department_id"] !== (int)$req["department_id"]) err("Chi duoc khoa mo nhan vien phong ban minh", 403);
      if ($target["role"] !== "staff") err("Chi duoc khoa mo nhan vien thuong", 403);
    }
  }
  $pdo->prepare("UPDATE employees SET status=? WHERE id=?")->execute([$status ? 1 : 0, $id]);
  out(["updated" => true, "status" => $status ? 1 : 0]);
}
if ($a === "delete" && ($m === "DELETE" || $m === "POST")) {
  $me = require_role($pdo, ["admin"]);
  $id = body()["id"] ?? $_GET["id"] ?? 0;
  if (!$id) err("Thieu id");
  // Khong cho tu xoa chinh tai khoan dang dang nhap (se tu khoa minh ra khoi he thong).
  if ((int)$id === (int)$me["id"]) err("Khong the xoa chinh tai khoan dang dang nhap", 400);
  $del = $pdo->prepare("DELETE FROM employees WHERE id=?");
  $del->execute([(int)$id]);
  if ($del->rowCount() === 0) err("Khong tim thay nhan vien", 404);
  out(["deleted" => true]);
}
if ($a === "departments" && $m === "GET") {
  require_login($pdo);
  try {
    out($pdo->query("SELECT * FROM departments ORDER BY name ASC, id ASC")->fetchAll(PDO::FETCH_ASSOC));
  } catch (PDOException $e) {
    err("Khong tai duoc danh sach phong ban", 500);
  }
}
if ($a === "positions" && $m === "GET") {
  require_login($pdo);
  positions_ensure($pdo);
  // ?dept_id= -> chi chuc vu cua phong do + chuc vu dung chung (department_id NULL).
  $dept = $_GET["dept_id"] ?? "";
  if ($dept !== "" && filter_var($dept, FILTER_VALIDATE_INT, ["options" => ["min_range" => 1]]) !== false) {
    $stmt = $pdo->prepare("SELECT * FROM positions WHERE department_id = ? OR department_id IS NULL ORDER BY title ASC");
    $stmt->execute([(int)$dept]);
    out($stmt->fetchAll(PDO::FETCH_ASSOC));
  }
  out($pdo->query("SELECT * FROM positions ORDER BY title ASC")->fetchAll(PDO::FETCH_ASSOC));
}
err("Unknown employees action");
?>
