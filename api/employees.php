<?php
require_once __DIR__ . "/db.php";
require_once __DIR__ . "/employee_validation.php";
require_once __DIR__ . "/auth_guard.php";
require_once __DIR__ . "/hr_employee_policy.php";
require_once __DIR__ . "/hr_proposals_lib.php";
require_once __DIR__ . "/positions_logic.php";
positions_ensure($pdo); // Tu ap dung migration 003 + seed/backfill chuc vu (chi phan con thieu)
$a = $_GET["action"] ?? "list"; $m = $_SERVER["REQUEST_METHOD"];

if ($a === "directory" && $m === "GET") {
  // Danh ba TOI THIEU (id/username/ho ten/phong) cho man Bang luong chon nhan vien.
  // Trang Quan ly nhan vien da siet: manager chi xem phong minh, staff bi chan.
  // Nghiep vu luong van can chon nguoi TOAN cong ty nen tach rieng duong nay:
  // chi admin, phong Ke toan (ca truong lan nhan vien) va Truong phong HR duoc dung.
  $me = require_login($pdo);
  if (!can_view_others_salary($me)) err("Khong co quyen xem danh ba luong", 403);
  $rows = $pdo->query("SELECT e.id,e.username,e.full_name,e.status,e.department_id,d.name AS dept_name FROM employees e LEFT JOIN departments d ON d.id=e.department_id WHERE e.status=1 ORDER BY e.full_name,e.id")->fetchAll(PDO::FETCH_ASSOC);
  out($rows);
}
if ($a === "list" && $m === "GET") {
  // Danh sach nhan vien theo Role x Phong (siet chat theo yeu cau 09/11/2026):
  // - admin: xem TOAN cong ty.
  // - Manager HR, Staff HR: all departments; ordinary manager: only own department.
  // - Other staff: no employee directory access.
  $me = require_login($pdo);
  if (!employee_can_list($me)) err("Khong co quyen xem danh sach", 403);
  $listRestricted = $me["role"] === "manager" && !is_hr($me);
  $hrStaff = hr_staff_actor($me);
  $search = $_GET["search"] ?? "";
  $dept = $_GET["dept_id"] ?? "";
  // Manager chi xem duoc nhan vien phong minh: ep bo loc phong theo token,
  // bo qua dept_id client gui (chi admin xem duoc tat ca).
  if ($me["role"] === "manager" && !hr_manager_actor($me)) {
    if ($me["department_id"] === null) err("Tai khoan quan ly chua duoc gan phong ban", 403);
    $dept = (string)(int)$me["department_id"];
  }
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
    if ($listRestricted || $hrStaff) {
      foreach ($rows as &$row) {
        $row["base_salary"] = null;
        if ($listRestricted) { $row["email"] = null; $row["phone"] = null; }
      }
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
  $stmt = $pdo->prepare("SELECT e.*, d.name AS dept_name, p.title AS position_name,
      LOWER(r.name) AS target_role FROM employees e
      LEFT JOIN departments d ON e.department_id=d.id
      LEFT JOIN positions p ON e.position_id=p.id
      LEFT JOIN roles r ON r.id=e.role_id WHERE e.id=? LIMIT 1");
  $stmt->execute([$_GET["id"] ?? 0]);
  $employee = $stmt->fetch(PDO::FETCH_ASSOC);
  if (!$employee) err("Khong tim thay nhan vien", 404);
  if (!employee_can_view($me, $employee)) err("Khong co quyen xem ho so nay", 403);
  if ((int)$me['id'] !== (int)$employee['id']) {
    // Do not expose payroll-specific details on another person's profile.
    unset($employee['insurance_salary'], $employee['dependents']);
    if (hr_staff_actor($me)) $employee['base_salary'] = null;
    if ($me['role'] === 'manager' && !hr_manager_actor($me)) {
      $employee['base_salary'] = null;
      $employee['email'] = null;
      $employee['phone'] = null;
    }
  }
  unset($employee['password_hash'], $employee['target_role']);
  out($employee);
}
if ($a === "create" && $m === "POST") {
  // Legacy endpoint now creates an actual recruitment request, not an active account.
  $me = require_login($pdo);
  if (!hr_employee_actor($me)) err('De xuat tuyen dung phai do HR lap',403);
  hr_require_migration($pdo);
  $b=body(); $b['reason']=$b['reason']??'De xuat tuyen dung tu HR';
  try { $pdo->beginTransaction(); $r=hr_save_proposal($pdo,$me,'recruitment',$b); $pdo->commit(); out($r); }
  catch (InvalidArgumentException $e) { if($pdo->inTransaction())$pdo->rollBack();err($e->getMessage(),422); }
  catch (PDOException $e) { if($pdo->inTransaction())$pdo->rollBack();err($e->getCode()==='23000'?'Ho so tuyen dung trung':'Khong tao duoc de xuat',$e->getCode()==='23000'?409:500); }
}
if ($a === "update" && ($m === "PUT" || $m === "POST")) {
  $b=body();$id=(int)($b['id']??$_GET['id']??0);
  $me=require_login($pdo); if($id<=0)err('Thieu id',422);
  hr_require_migration($pdo);
  $st=$pdo->prepare('SELECT e.*, LOWER(r.name) AS target_role FROM employees e LEFT JOIN roles r ON r.id=e.role_id WHERE e.id=?');
  $st->execute([$id]);$target=$st->fetch(PDO::FETCH_ASSOC);
  if(!$target)err('Khong tim thay nhan vien',404);
  if(!employee_can_edit_contact($me,$target))err('Khong co quyen sua ho so',403);
  foreach ($b as $field=>$value) {
    // Gson on Android may send null placeholders for old employment fields.
    if(in_array($field,['id','requester_id','full_name','email','phone','birth_date','gender','address','direct_manager_id','hire_date','employment_status'],true))continue;
    if($value!==null)err('Truong '.$field.' can quy trinh rieng',403);
  }
  $self=$id===(int)$me['id'];
  if(!$self && !hr_employee_actor($me) && $me['role']!=='admin')err('Khong co quyen',403);
  // Normal staff can edit their own contact only; HR can edit administrative profile data.
  if(!hr_manager_actor($me)){
      foreach(['direct_manager_id','hire_date','employment_status'] as $restricted)
          if(array_key_exists($restricted,$b)&&$b[$restricted]!==null)err('Chi Truong phong HR duoc cap nhat truong hanh chinh',403);
  }
  try{
      $fields=hr_profile_changes($pdo,$b,$target);
      if(array_key_exists('hire_date',$b)&&hr_manager_actor($me))$fields['hire_date']=hr_date($b['hire_date']);
      if(array_key_exists('employment_status',$b)&&hr_manager_actor($me)){
          if(!in_array($b['employment_status'],['probation','working'],true))err('Nghi viec can BOD duyet',403);
          if(($target['employment_status']??'working')==='left')err('Khong tu kich hoat lai nguoi da nghi',403);
          $fields['employment_status']=$b['employment_status'];
      }
      if(!$fields)out(['updated'=>false]);
      $before=[];foreach($fields as $key=>$value)$before[$key]=$target[$key]??null;
      $pdo->beginTransaction();
      $sql='UPDATE employees SET '.implode(', ',array_map(fn($f)=>$f.'=?',array_keys($fields))).' WHERE id=?';
      $pdo->prepare($sql)->execute([...array_values($fields),$id]);
      hr_record_history($pdo,(int)$me['id'],$id,'employee.profile.updated',$before,$fields);
      $pdo->commit();out(['updated'=>true]);
  }catch(InvalidArgumentException $e){if($pdo->inTransaction())$pdo->rollBack();err($e->getMessage(),422);}
   catch(PDOException $e){if($pdo->inTransaction())$pdo->rollBack();err($e->getCode()==='23000'?'Email da ton tai':'Khong cap nhat duoc ho so',$e->getCode()==='23000'?409:500);}
}
if ($a === "upload-avatar" && $m === "POST") {
  $empId = (int)($_POST['employee_id'] ?? 0);
  $me = require_login($pdo);
  if (!$empId) err('Thieu employee_id', 422);
  $stmt = $pdo->prepare("SELECT e.id, e.department_id, LOWER(r.name) AS target_role
      FROM employees e LEFT JOIN roles r ON r.id=e.role_id WHERE e.id=? LIMIT 1");
  $stmt->execute([$empId]);
  $target = $stmt->fetch(PDO::FETCH_ASSOC);
  if (!$target) err('Khong tim thay nhan vien', 404);
  if (!employee_can_edit_contact($me, $target)) err('Khong co quyen cap nhat anh', 403);
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
  if (!$req || $req['role'] !== 'admin') err("Chi Admin khoa/mo tai khoan vi ly do quan tri", 403);
  $q->execute([$id]);
  $target = $q->fetch(PDO::FETCH_ASSOC);
  if (!$target) err("Khong tim thay nhan vien", 404);
  hr_require_migration($pdo);
  $emp=hr_employee($pdo,$id);
  if($status && ($emp['employment_status']??'working')==='left')err('Da nghi viec; khong duoc mo khoa tu dong',403);
  $pdo->beginTransaction();
  $pdo->prepare('UPDATE employees SET status=? WHERE id=?')->execute([$status?1:0,$id]);
  if(!$status)$pdo->prepare('DELETE FROM api_tokens WHERE employee_id=?')->execute([$id]);
  hr_record_history($pdo,(int)$me['id'],$id,'account.locked_by_admin',['status'=>$emp['status']],['status'=>$status?1:0]);
  $pdo->commit();out(['updated'=>true,'status'=>$status?1:0]);
}
if ($a === "delete" && ($m === "DELETE" || $m === "POST")) {
  require_role($pdo, ['admin']);
  err('Khong xoa vinh vien nhan vien. Cho quy trinh nghi viec BOD', 409);
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
    $stmt = $pdo->prepare("SELECT * FROM positions WHERE (department_id = ? OR department_id IS NULL) AND is_active=1 ORDER BY title ASC");
    $stmt->execute([(int)$dept]);
    out($stmt->fetchAll(PDO::FETCH_ASSOC));
  }
  out($pdo->query("SELECT * FROM positions WHERE is_active=1 ORDER BY title ASC")->fetchAll(PDO::FETCH_ASSOC));
}
err("Unknown employees action");
?>
