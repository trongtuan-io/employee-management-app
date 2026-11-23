<?php
$runDue = PHP_SAPI === 'cli';
if ($runDue) {
  if (($argv[1] ?? '') !== '--apply-due') {
    fwrite(STDERR, "Usage: php api/proposals.php --apply-due\n");
    exit(2);
  }
  $_SERVER['REQUEST_METHOD'] = 'CLI';
}
// Management API for HR proposals and BOD executive approvals (Updated for new hr_proposals schema)
require_once __DIR__ . "/db.php";
require_once __DIR__ . "/auth_guard.php";
require_once __DIR__ . "/hr_proposals_lib.php";

$a = $_GET["action"] ?? "list";
$m = $_SERVER["REQUEST_METHOD"];

function proposal_type_label($type) {
  switch ($type) {
    case "recruitment":
    case "new_hire": return "Tuyển mới nhân viên";
    case "base_salary_change":
    case "salary_change": return "Thay đổi lương cơ bản";
    case "transfer":
    case "department_transfer": return "Chuyển phòng ban";
    case "promotion": return "Thăng chức / Đổi chức vụ";
    case "termination": return "Cho nghỉ việc";
    default: return "Đề xuất nhân sự";
  }
}

if ($a === "list" && $m === "GET") {
  $me = require_login($pdo);
  if ($me["role"] !== "admin" && !is_director($me) && !hr_employee_actor($me)) {
    err("Khong co quyen xem danh sach de xuat nhan su", 403);
  }
  hr_require_migration($pdo);
  hr_require_internal_review_migration($pdo);

  $status = trim((string)($_GET["status"] ?? ""));
  $type = trim((string)($_GET["type"] ?? ""));

  $where = " WHERE 1=1 ";
  $params = [];
  if (hr_staff_actor($me)) {
    $where .= " AND p.proposal_type='recruitment' AND p.proposed_by=? ";
    $params[] = (int)$me['id'];
  } elseif (!hr_manager_actor($me)) {
    $where .= " AND (p.status='pending' OR p.reviewed_by IS NOT NULL) ";
  }

  if ($status !== "") {
    $where .= " AND p.status = ? ";
    $params[] = $status;
  }
  if ($type !== "") {
    $where .= " AND p.proposal_type=? ";
    $params[] = ['new_hire'=>'recruitment', 'department_transfer'=>'transfer', 'salary_change'=>'base_salary_change'][$type] ?? $type;
  }

  $sql = "SELECT p.*, e.full_name AS employee_name, hr.full_name AS creator_name, bod.full_name AS approver_name
          FROM hr_proposals p
          LEFT JOIN employees e ON e.id = p.employee_id
          LEFT JOIN employees hr ON hr.id = p.proposed_by
          LEFT JOIN employees bod ON bod.id = p.reviewed_by
          $where ORDER BY (p.status = 'pending') DESC, p.id DESC LIMIT 100";

  $stmt = $pdo->prepare($sql);
  $stmt->execute($params);
  $rawRows = $stmt->fetchAll(PDO::FETCH_ASSOC);

  // Cache danh mục phòng ban và chức vụ để map giá trị nhanh
  $depts = $pdo->query("SELECT id, name FROM departments")->fetchAll(PDO::FETCH_KEY_PAIR);
  $positions = $pdo->query("SELECT id, title FROM positions")->fetchAll(PDO::FETCH_KEY_PAIR);

  $result = [];
  foreach ($rawRows as $r) {
    $rawT = $r["proposal_type"];
    $normT = ['recruitment'=>'new_hire', 'transfer'=>'department_transfer', 'base_salary_change'=>'salary_change'][$rawT] ?? $rawT;
    $before = json_decode($r['before_data'] ?? 'null', true) ?? [];

    $item = [
      "id" => (int)$r["id"],
      "type" => $normT,
      "version" => (int)$r["version"],
      "effective_date" => $r["effective_date"],
      "can_apply" => bod_can_apply($r),
      "created_by" => (int)$r["proposed_by"],
      "approved_by" => $r["reviewed_by"],
      "processed_at" => $r["reviewed_at"],
      "employee_id" => $r["employee_id"] !== null ? (int)$r["employee_id"] : null,
      "employee_name" => $r["employee_name"],
      "creator_name" => $r["creator_name"],
      "approver_name" => $r["approver_name"],
      "status" => $r["status"],
      "reason" => $r["reason"] ?? "",
      "created_at" => $r["created_at"] ?? "",
      "old_value" => $r["old_value"] ?? null,
      "new_value" => $r["new_value"] ?? null,
      "dept_name" => null,
      "pos_title" => null,
    ];

    // Decode proposed_data (JSON của cấu trúc HR mới)
    if (!empty($r["proposed_data"])) {
      $pData = is_array($r["proposed_data"]) ? $r["proposed_data"] : json_decode($r["proposed_data"], true);
      if (is_array($pData)) {
        if (!empty($pData["full_name"])) $item["employee_name"] = $pData["full_name"];
        if (!empty($pData["department_id"]) && isset($depts[$pData["department_id"]])) {
          $item["dept_name"] = $depts[$pData["department_id"]];
        }
        if (!empty($pData["position_id"]) && isset($positions[$pData["position_id"]])) {
          $item["pos_title"] = $positions[$pData["position_id"]];
        }

        // Map old/new values cho từng loại đề xuất
        switch ($rawT) {
          case "base_salary_change":
            if (isset($before["base_salary"])) $item["old_value"] = number_format((float)$before["base_salary"]) . " đ";
            if (isset($pData["base_salary"])) $item["new_value"] = number_format((float)$pData["base_salary"]) . " đ";
            break;
          case "transfer":
          case "department_transfer":
            if (isset($depts[$before["department_id"] ?? 0])) {
              $item["old_value"] = "Phòng " . $depts[$before["department_id"]];
            }
            if (!empty($item["dept_name"])) $item["new_value"] = "Phòng " . $item["dept_name"];
            break;
          case "promotion":
            if (isset($positions[$before["position_id"] ?? 0])) {
              $item["old_value"] = $positions[$before["position_id"]];
            }
            if (!empty($item["pos_title"])) $item["new_value"] = $item["pos_title"];
            break;
          case "recruitment":
          case "new_hire":
            if (empty($item["new_value"]) && !empty($pData["username"])) {
              $item["new_value"] = "Tạo tài khoản: " . $pData["username"];
            }
            break;
        }
      }
    }

    $result[] = $item;
  }

  out($result);
}

function bod_lock_employee(PDO $pdo, array $p, array $before): array {
  $id = (int)$p['employee_id'];
  $stmt = $pdo->prepare('SELECT * FROM employees WHERE id=? FOR UPDATE');
  $stmt->execute([$id]);
  $employee = $stmt->fetch(PDO::FETCH_ASSOC);
  if (!$employee || (int)$employee['role_id'] === 1 || !$before) {
    throw new DomainException('Nhan vien hoac du lieu goc khong hop le', 409);
  }
  foreach ($before as $field => $value) {
    if (!array_key_exists($field, $employee) || $employee[$field] != $value
      || ($employee[$field] === null) !== ($value === null)) {
      throw new DomainException('Ho so nhan vien da thay doi, can lap de xuat moi', 409);
    }
  }

  return $employee;
}

function bod_validate_position(PDO $pdo, ?int $position, ?int $department): void {
  if ($position === null) return;
  $stmt = $pdo->prepare('SELECT department_id, is_active FROM positions WHERE id=? FOR UPDATE');
  $stmt->execute([$position]);
  $row = $stmt->fetch(PDO::FETCH_ASSOC);
  if (!$row || (int)$row['is_active'] !== 1
    || ($row['department_id'] !== null && (int)$row['department_id'] !== $department)) {
    throw new DomainException('Chuc vu da ngung hoac khong thuoc phong ban de xuat', 409);
  }
}

function bod_apply_employee_change(PDO $pdo, array $p, array $data, array $before, int $actor): void {
  $employee = bod_lock_employee($pdo, $p, $before);
  $id = (int)$employee['id'];
  switch ($p['proposal_type']) {
    case 'base_salary_change':
      $fields = ['base_salary' => $data['base_salary']];
      break;
    case 'promotion':
      $position = (int)$data['position_id'];
      bod_validate_position($pdo, $position, $employee['department_id'] === null ? null : (int)$employee['department_id']);
      $fields = ['position_id' => $position];
      if (isset($data['role_id'])) $fields['role_id'] = (int)$data['role_id'];
      break;
    case 'transfer':
      $department = (int)$data['department_id'];
      $position = $data['position_id'] ?? null;
      bod_validate_position($pdo, $position, $department);
      $fields = ['department_id' => $department, 'position_id' => $position];
      break;
    case 'termination':
      $fields = ['employment_status' => 'left', 'status' => 0,
        'termination_effective_date' => $p['effective_date'] ?? date('Y-m-d')];
      $pdo->prepare('DELETE FROM api_tokens WHERE employee_id=?')->execute([$id]);
      break;
    default:
      throw new DomainException('Loai de xuat khong hop le', 422);
  }

  $assignments = implode(', ', array_map(fn($field) => "$field=?", array_keys($fields)));
  $pdo->prepare("UPDATE employees SET $assignments WHERE id=?")->execute([...array_values($fields), $id]);
  $pdo->prepare('UPDATE hr_proposals SET applied_at=NOW() WHERE id=?')->execute([$p['id']]);
  hr_record_history($pdo, $actor, $id, 'proposal.applied', array_intersect_key($employee, $fields), $fields, (int)$p['id']);
}

function bod_can_apply(array $proposal): bool {
  return $proposal['status'] === 'approved' && $proposal['reviewed_by'] && $proposal['applied_at'] === null
    && $proposal['proposal_type'] !== 'recruitment'
    && ($proposal['effective_date'] === null || $proposal['effective_date'] <= date('Y-m-d'));
}

if ($runDue) {
  hr_require_migration($pdo);
  hr_require_internal_review_migration($pdo);
  $due = $pdo->query("SELECT id FROM hr_proposals WHERE status='approved' AND reviewed_by IS NOT NULL
    AND applied_at IS NULL AND proposal_type<>'recruitment' AND effective_date<=CURDATE() ORDER BY effective_date, id");
  $ids = $due->fetchAll(PDO::FETCH_COLUMN);
  $lock = $pdo->prepare('SELECT * FROM hr_proposals WHERE id=? FOR UPDATE');
  $inc = $pdo->prepare('UPDATE hr_proposals SET version=version+1 WHERE id=?');
  $result = ['applied' => [], 'failed' => []];
  foreach ($ids as $id) {
    try {
      $pdo->beginTransaction();
      $lock->execute([$id]);
      $proposal = $lock->fetch(PDO::FETCH_ASSOC);
      $lock->closeCursor();
      if ($proposal && bod_can_apply($proposal)) {
        $data = json_decode($proposal['proposed_data'], true, 512, JSON_THROW_ON_ERROR);
        $before = json_decode($proposal['before_data'] ?? 'null', true, 512, JSON_THROW_ON_ERROR) ?? [];
        bod_apply_employee_change($pdo, $proposal, $data, $before, (int)$proposal['reviewed_by']);
        $inc->execute([$id]);
        $pdo->commit();
        $result['applied'][] = (int)$id;
        continue;
      }
      $pdo->rollBack();
    } catch (Throwable $e) {
      if ($pdo->inTransaction()) $pdo->rollBack();
      error_log("BOD effective-date apply failed for proposal $id: " . $e->getMessage());
      $result['failed'][] = (int)$id;
    }
  }
  echo json_encode($result) . PHP_EOL;
  exit($result['failed'] ? 1 : 0);
}

if (in_array($a, ['approve', 'apply'], true) && $m === 'POST') {
  $me = require_login($pdo);
  if (!is_director($me)) err('Chi Giam doc hoac Pho Giam doc duoc xu ly de xuat nhan su', 403);
  hr_require_migration($pdo);
  hr_require_internal_review_migration($pdo);

  $b = body();
  $positiveInt = ['options' => ['min_range' => 1]];
  $id = filter_var($b['id'] ?? null, FILTER_VALIDATE_INT, $positiveInt);
  $version = filter_var($b['version'] ?? null, FILTER_VALIDATE_INT, $positiveInt);
  $status = $a === 'apply' ? 'approved' : ($b['status'] ?? '');
  $note = trim((string)($b['review_note'] ?? ''));
  if (!$id || !$version || !in_array($status, ['approved', 'rejected'], true) || hr_length($note) > 500) {
    err('ID, phien ban hoac quyet dinh khong hop le', 422);
  }

  try {
    $pdo->beginTransaction();
    $stmt = $pdo->prepare('SELECT * FROM hr_proposals WHERE id=? FOR UPDATE');
    $stmt->execute([$id]);
    $p = $stmt->fetch(PDO::FETCH_ASSOC);
    if (!$p) throw new DomainException('Khong tim thay de xuat', 404);
    if ((int)$p['version'] !== $version) throw new DomainException('Phien ban da thay doi, hay tai lai de xuat', 409);
    $actor = (int)$me['id'];
    if ((int)$p['proposed_by'] === $actor || (int)$p['employee_id'] === $actor) {
      throw new DomainException('Khong the tu duyet de xuat cua minh', 403);
    }
    $recruitment = $p['proposal_type'] === 'recruitment';
    $due = $p['effective_date'] === null || $p['effective_date'] <= date('Y-m-d');
    $data = json_decode($p['proposed_data'], true, 512, JSON_THROW_ON_ERROR);
    $before = json_decode($p['before_data'] ?? 'null', true, 512, JSON_THROW_ON_ERROR) ?? [];
    $applied = false;

    if ($a === 'apply') {
      if (!bod_can_apply($p)) {
        throw new DomainException('De xuat chua duoc duyet, chua den ngay hieu luc hoac da ap dung', 409);
      }
      bod_apply_employee_change($pdo, $p, $data, $before, $actor);
      $pdo->prepare('UPDATE hr_proposals SET version=version+1 WHERE id=?')->execute([$id]);
      $applied = true;
    } else {
      if ($p['status'] !== 'pending') throw new DomainException('De xuat khong o hang cho BOD', 409);
      if ($recruitment && (int)$p['workflow_version'] >= 2 && !$p['hr_reviewed_by']) {
        throw new DomainException('Ho so thieu ket qua kiem duyet noi bo HR', 409);
      }
      if ($recruitment && $status === 'approved') {
        if ((int)$p['workflow_version'] >= 3 && hr_recruitment_verified_level($pdo, $p) !== 'senior') {
          throw new DomainException('Chuc vu hoac cap tuyen dung da thay doi', 409);
        }
        $check = $pdo->prepare('SELECT id FROM employees WHERE username=? OR email=? LIMIT 1');
        $check->execute([$data['username'], $data['email']]);
        if ($check->fetchColumn()) throw new DomainException('Nhan vien da ton tai', 409);
      }

      if ($status === 'approved' && !$recruitment && !$due) bod_lock_employee($pdo, $p, $before);

      $pdo->prepare('UPDATE hr_proposals SET status=?, reviewed_by=?, reviewed_at=NOW(), review_note=?,
        version=version+1, reservation_username=NULL, reservation_email=NULL WHERE id=?')
        ->execute([$status, $actor, $note, $id]);
      $employeeId = $p['employee_id'] === null ? null : (int)$p['employee_id'];
      hr_record_history($pdo, $actor, $employeeId, 'proposal.' . $status, ['status' => $p['status']],
        ['status' => $status, 'version' => $version + 1, 'review_note' => $note], $id);

      if ($status === 'approved' && !$recruitment && $due) {
        bod_apply_employee_change($pdo, $p, $data, $before, $actor);
        $applied = true;
      }
    }
    $pdo->commit();
  } catch (Throwable $e) {
    if ($pdo->inTransaction()) $pdo->rollBack();
    if ($e instanceof DomainException) err($e->getMessage(), (int)$e->getCode());
    error_log('BOD proposal failure: ' . $e->getMessage());
    err('Khong xu ly duoc de xuat', 500);
  }

  if ($a === 'approve') {
    try {
      notify_push($pdo, (int)$p['proposed_by'], 'proposal_' . $status,
        $status === 'approved' ? 'Đề xuất nhân sự đã được phê duyệt' : 'Đề xuất nhân sự bị từ chối',
        proposal_type_label($p['proposal_type']) . ' · ' . $me['full_name'], $id);
    } catch (Throwable $e) {
      error_log('BOD proposal notification failed: ' . $e->getMessage());
    }
  }
  out(['id' => $id, 'status' => $status, 'version' => $version + 1, 'applied' => $applied]);
}

err('Unknown proposals action', 404);

