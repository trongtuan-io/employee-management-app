<?php
// Logic CHUC VU dung chuan:
// - Bang positions co them cot department_id: chuc vu thuoc 1 phong cu the,
//   hoac department_id = NULL nghia la chuc vu dung chung cho moi phong.
// - Nhan vien chi duoc gan chuc vu cua phong minh (hoac chuc vu dung chung).
// - positions_ensure() tu ap dung migration 003 + seed + backfill du lieu mau
//   (chi chay phan con thieu, an toan khi goi nhieu lan) de demo khong sup
//   vi chua chay SQL tay.

function positions_ensure(PDO $pdo) {
  static $done = false;
  if ($done) return;
  $done = true;
  try {
    $has = $pdo->query("SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'positions' AND COLUMN_NAME = 'department_id'")
      ->fetchColumn();
    if (!$has) {
      $pdo->exec("ALTER TABLE positions ADD COLUMN department_id INT NULL DEFAULT NULL");
      $pdo->exec("ALTER TABLE positions ADD INDEX idx_positions_dept (department_id)");
    }
    // Phong ban theo ten (khong gia dinh id cung).
    $deptId = function ($name) use ($pdo) {
      $s = $pdo->prepare("SELECT id FROM departments WHERE name = ? LIMIT 1");
      $s->execute([$name]);
      $v = $s->fetchColumn();
      return $v === false ? null : (int)$v;
    };
    $it = $deptId("IT"); $hr = $deptId("HR");
    // Gan phong cho cac chuc vu seed san co (chi khi chua gan).
    if ($it) $pdo->prepare("UPDATE positions SET department_id = ? WHERE title IN ('Developer','Tester') AND department_id IS NULL")->execute([$it]);
    if ($hr) $pdo->prepare("UPDATE positions SET department_id = ? WHERE title = 'HR Staff' AND department_id IS NULL")->execute([$hr]);
    // Bo sung cac chuc vu chuan tieng Viet (INSERT IGNORE theo UNIQUE title).
    $seed = [
      ["Trưởng phòng IT", $it], ["Trưởng phòng HR", $hr],
      ["Thực tập sinh", null], ["Quản trị hệ thống", null],
    ];
    $ins = $pdo->prepare("INSERT IGNORE INTO positions(title, department_id) VALUES(?, ?)");
    foreach ($seed as [$title, $d]) $ins->execute([$title, $d]);
    // Backfill 4 tai khoan mau (chi khi position_id van NULL).
    $backfill = ["admin" => "Quản trị hệ thống", "tuannt03" => "Developer",
                 "sep_hr" => "Trưởng phòng HR", "nv_hr" => "HR Staff"];
    $find = $pdo->prepare("SELECT id FROM positions WHERE title = ? LIMIT 1");
    $upd = $pdo->prepare("UPDATE employees SET position_id = ? WHERE username = ? AND position_id IS NULL");
    foreach ($backfill as $username => $title) {
      $find->execute([$title]);
      $pid = $find->fetchColumn();
      if ($pid !== false) $upd->execute([(int)$pid, $username]);
    }
  } catch (Exception $e) {
    // Khong chan request chinh vi loi seed; logic validate ben duoi van chay.
  }
}

// Kiem tra chuc vu co hop le voi phong ban cua nhan vien khong.
// Tra ve position_id da chuan hoa (null neu khong gan). Loi -> err() 422.
function validate_position_for_department(PDO $pdo, $positionId, $departmentId) {
  if ($positionId === null || $positionId === "" || (int)$positionId === 0) return null;
  $positionId = (int)$positionId;
  $s = $pdo->prepare("SELECT id, department_id FROM positions WHERE id = ? LIMIT 1");
  $s->execute([$positionId]);
  $pos = $s->fetch(PDO::FETCH_ASSOC);
  if (!$pos) err("Chuc vu khong ton tai", 422);
  $posDept = $pos["department_id"] === null ? null : (int)$pos["department_id"];
  $empDept = ($departmentId === null || $departmentId === "") ? null : (int)$departmentId;
  if ($posDept !== null && $posDept !== $empDept) {
    err("Chuc vu khong thuoc phong ban da chon", 422);
  }
  return $positionId;
}
?>
