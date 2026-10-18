<?php
<<<<<<< HEAD
require_once __DIR__ . "/db.php";
require_once __DIR__ . "/employee_validation.php";
$a = $_GET["action"] ?? "list"; $m = $_SERVER["REQUEST_METHOD"];

if ($a === "list" && $m === "GET") {
  $search = $_GET["search"] ?? "";
  $dept = $_GET["dept_id"] ?? "";
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

  $s = "%" . $search . "%";
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
    // Giu data la mang de khong lam hong client cu; them metadata rieng.
    echo json_encode([
      "success" => true,
      "data" => $stmt->fetchAll(PDO::FETCH_ASSOC),
      "pagination" => ["page" => $page, "limit" => $limit, "total" => $total, "total_pages" => $totalPages]
    ]);
    exit;
  } catch (PDOException $e) {
    err("Khong tai duoc danh sach nhan vien", 500);
  }
=======
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
>>>>>>> 2328eaa7a34a7d695abecba54db24530ae3bbd2f
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
<<<<<<< HEAD
  $b = body(); $id = (int)($b["id"] ?? $_GET["id"] ?? 0);
  $reqId = (int)($b["requester_id"] ?? 0);
  if (!$id || !$reqId) err("Thieu id/requester_id");
  $reqRole = employee_editor_role($pdo, $reqId);
  $isManager = in_array($reqRole, ["admin", "manager"], true);
  if (!$reqRole || ($reqId !== $id && !$isManager)) err("Khong co quyen", 403);
  $stmt = $pdo->prepare("SELECT * FROM employees WHERE id=? LIMIT 1");
  $stmt->execute([$id]);
  $cur = $stmt->fetch(PDO::FETCH_ASSOC);
  if (!$cur) err("Khong tim thay nhan vien", 404);
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
      $hire = array_key_exists('hire_date', $b) ? employee_hire_date($b['hire_date']) : ($cur['hire_date'] ?? null);
      $pdo->prepare("UPDATE employees SET full_name=?,email=?,phone=?,department_id=?,position_id=?,hire_date=? WHERE id=?")
        ->execute([$name, $email, $phone, $dept, $pos, $hire, $id]);
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
  $reqId = (int)($_POST["requester_id"] ?? 0);
  if (!$empId) err("Thieu employee_id");
  $stmt = $pdo->prepare("SELECT r.name FROM employees e LEFT JOIN roles r ON r.id=e.role_id WHERE e.id=? LIMIT 1");
  $stmt->execute([$reqId]);
  $reqRole = strtolower(($stmt->fetch(PDO::FETCH_ASSOC) ?: [])["name"] ?? "");
  $isAdmin = in_array($reqRole, ["admin", "manager"]);
  if ($reqId !== $empId && !$isAdmin) err("Khong co quyen", 403);
  if (!isset($_FILES["avatar"]) || $_FILES["avatar"]["error"] !== UPLOAD_ERR_OK) err("Chua chon anh");
  $f = $_FILES["avatar"];
  $mime = (new finfo(FILEINFO_MIME_TYPE))->file($f["tmp_name"]);
  $extensions = ["image/jpeg" => "jpg", "image/png" => "png", "image/webp" => "webp"];
  if (!isset($extensions[$mime]) || !@getimagesize($f["tmp_name"])) err("Chi nhan anh JPG/PNG/WebP", 422);
  $ext = $extensions[$mime];
  if ($f["size"] > 2 * 1024 * 1024) err("Anh toi da 2MB");
  $dir = __DIR__ . "/uploads/avatars";
  if (!is_dir($dir)) mkdir($dir, 0777, true);
  $name = "emp_" . $empId . "_" . bin2hex(random_bytes(8)) . "." . $ext;
  if (!move_uploaded_file($f["tmp_name"], $dir . "/" . $name)) err("Luu anh that bai", 500);
  $url = "uploads/avatars/" . $name;
  $pdo->prepare("UPDATE employees SET avatar_url=? WHERE id=?")->execute([$url, $empId]);
  out(["avatar_url" => $url]);
}
if ($a === "set-status" && ($m === "PUT" || $m === "POST")) {
  // Khoa/mo tai khoan. Admin: tat ca phong ban. Manager: chi staff cung phong.
  $b = body();
  $id = (int)($b["id"] ?? 0);
  $reqId = (int)($b["requester_id"] ?? 0);
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
    if ((int)$target["department_id"] !== (int)$req["department_id"]) err("Chi duoc khoa mo nhan vien phong ban minh", 403);
    if ($target["role"] !== "staff") err("Chi duoc khoa mo nhan vien thuong", 403);
  }
  $pdo->prepare("UPDATE employees SET status=? WHERE id=?")->execute([$status ? 1 : 0, $id]);
  out(["updated" => true, "status" => $status ? 1 : 0]);
=======
  $b = body(); $id = $b["id"] ?? $_GET["id"] ?? 0;
  if (!$id) err("Thieu id");
  $pdo->prepare("UPDATE employees SET full_name=?,phone=?,email=?,base_salary=?,department_id=?,position_id=?,role_id=?,status=? WHERE id=?")
    ->execute([$b["full_name"], $b["phone"] ?? null, $b["email"], $b["base_salary"] ?? 0, $b["department_id"] ?? null, $b["position_id"] ?? null, $b["role_id"] ?? 3, $b["status"] ?? 1, $id]);
  out(["updated" => true]);
>>>>>>> 2328eaa7a34a7d695abecba54db24530ae3bbd2f
}
if ($a === "delete" && ($m === "DELETE" || $m === "POST")) {
  $id = body()["id"] ?? $_GET["id"] ?? 0;
  if (!$id) err("Thieu id");
  $pdo->prepare("DELETE FROM employees WHERE id=?")->execute([$id]);
  out(["deleted" => true]);
}
<<<<<<< HEAD
if ($a === "departments" && $m === "GET") {
  try {
    out($pdo->query("SELECT * FROM departments ORDER BY name ASC, id ASC")->fetchAll(PDO::FETCH_ASSOC));
  } catch (PDOException $e) {
    err("Khong tai duoc danh sach phong ban", 500);
  }
}
=======
if ($a === "departments" && $m === "GET") { out($pdo->query("SELECT * FROM departments")->fetchAll(PDO::FETCH_ASSOC)); }
>>>>>>> 2328eaa7a34a7d695abecba54db24530ae3bbd2f
if ($a === "positions" && $m === "GET") { out($pdo->query("SELECT * FROM positions")->fetchAll(PDO::FETCH_ASSOC)); }
err("Unknown employees action");
?>
