<?php
require_once __DIR__ . "/../employee-api/db.php";
$a = $_GET["action"] ?? "list"; $m = $_SERVER["REQUEST_METHOD"];

if ($a === "list" && $m === "GET") {
  $search = $_GET["search"] ?? "";
  $dept = $_GET["dept_id"] ?? "";
  $sort = $_GET["sort"] ?? "id_desc";
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
  $where = " WHERE (e.full_name LIKE ? OR e.username LIKE ? OR e.email LIKE ?)";
  $params = [$s, $s, $s];
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
  $b = body(); $id = (int)($b["id"] ?? $_GET["id"] ?? 0);
  $reqId = (int)($b["requester_id"] ?? 0);
  if (!$id) err("Thieu id");
  // Xac dinh quyen nguoi sua: chi admin/manager duoc doi mail, sdt, luong, role...
  $stmt = $pdo->prepare("SELECT r.name FROM employees e LEFT JOIN roles r ON r.id=e.role_id WHERE e.id=? LIMIT 1");
  $stmt->execute([$reqId]);
  $reqRole = strtolower(($stmt->fetch(PDO::FETCH_ASSOC) ?: [])["name"] ?? "");
  $isAdmin = in_array($reqRole, ["admin", "manager"]);
  if ($reqId !== $id && !$isAdmin) err("Khong co quyen sua ho so nguoi khac", 403);
  if ($isAdmin) {
    // Giu nguyen cac field khong gui len (tranh mat phong ban/quyen nhu cu)
    $stmt = $pdo->prepare("SELECT * FROM employees WHERE id=? LIMIT 1");
    $stmt->execute([$id]);
    $cur = $stmt->fetch(PDO::FETCH_ASSOC);
    if (!$cur) err("Khong tim thay nhan vien", 404);
    $pick = function($k) use ($b, $cur) {
      return array_key_exists($k, $b) && $b[$k] !== "" && $b[$k] !== null ? $b[$k] : $cur[$k];
    };
    try {
      $pdo->prepare("UPDATE employees SET full_name=?,phone=?,email=?,base_salary=?,department_id=?,position_id=?,role_id=?,status=? WHERE id=?")
        ->execute([$pick("full_name"), $pick("phone"), $pick("email"), $pick("base_salary"), $pick("department_id"), $pick("position_id"), $pick("role_id"), $pick("status"), $id]);
    } catch (Exception $e) { err("Email da duoc dung boi nguoi khac", 409); }
  } else {
    // Nhan vien tu sua: chi duoc doi ho ten. Mail, SDT, luong, role... giu nguyen tu DB.
    $pdo->prepare("UPDATE employees SET full_name=? WHERE id=?")->execute([$b["full_name"], $id]);
  }
  out(["updated" => true]);
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
  $ext = strtolower(pathinfo($f["name"], PATHINFO_EXTENSION));
  if (!in_array($ext, ["jpg", "jpeg", "png", "webp"])) err("Chi nhan anh jpg/png/webp");
  if ($f["size"] > 2 * 1024 * 1024) err("Anh toi da 2MB");
  $dir = __DIR__ . "/uploads/avatars";
  if (!is_dir($dir)) mkdir($dir, 0777, true);
  $name = "emp_" . $empId . "_" . time() . "." . $ext;
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
}
if ($a === "delete" && ($m === "DELETE" || $m === "POST")) {
  $id = body()["id"] ?? $_GET["id"] ?? 0;
  if (!$id) err("Thieu id");
  $pdo->prepare("DELETE FROM employees WHERE id=?")->execute([$id]);
  out(["deleted" => true]);
}
if ($a === "departments" && $m === "GET") {
  try {
    out($pdo->query("SELECT * FROM departments ORDER BY name ASC, id ASC")->fetchAll(PDO::FETCH_ASSOC));
  } catch (PDOException $e) {
    err("Khong tai duoc danh sach phong ban", 500);
  }
}
if ($a === "positions" && $m === "GET") { out($pdo->query("SELECT * FROM positions")->fetchAll(PDO::FETCH_ASSOC)); }
err("Unknown employees action");
?>
