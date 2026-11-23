<?php
// Management API for HR proposals and BOD executive approvals (Updated for new hr_proposals schema)
require_once __DIR__ . "/db.php";
require_once __DIR__ . "/auth_guard.php";
require_once __DIR__ . "/positions_logic.php";

$a = $_GET["action"] ?? "list";
$m = $_SERVER["REQUEST_METHOD"];

function proposal_type_label($type) {
  switch ($type) {
    case "recruitment":
    case "new_hire": return "Tuyển mới nhân viên";
    case "salary_change": return "Thay đổi lương cơ bản";
    case "department_transfer": return "Chuyển phòng ban";
    case "promotion": return "Thăng chức / Đổi chức vụ";
    case "termination": return "Cho nghỉ việc";
    default: return "Đề xuất nhân sự";
  }
}

function hr_proposals_columns(PDO $pdo) {
  static $cols = null;
  if ($cols !== null) return $cols;
  $stmt = $pdo->prepare("SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'hr_proposals'");
  $stmt->execute();
  $cols = array_column($stmt->fetchAll(PDO::FETCH_ASSOC), "COLUMN_NAME");
  return $cols;
}

// 1. Danh sách đề xuất cho BOD / Admin duyệt (hoặc HR xem lại)
if ($a === "list" && $m === "GET") {
  $me = require_login($pdo);

  // Chỉ Giám đốc (BOD), Admin và HR mới được xem danh sách đề xuất. Trưởng phòng khác bị chặn!
  if ($me["role"] !== "admin" && !is_director($me) && !is_hr($me)) {
    err("Chỉ Ban Giám đốc và HR mới có quyền xem danh sách đề xuất nhân sự", 403);
  }

  $cols = hr_proposals_columns($pdo);
  $typeCol = in_array("proposal_type", $cols, true) ? "proposal_type" : "type";
  $creatorCol = in_array("proposed_by", $cols, true) ? "proposed_by" : "created_by";
  $approverCol = in_array("reviewed_by", $cols, true) ? "reviewed_by" : "approved_by";
  $hasProposedData = in_array("proposed_data", $cols, true);

  $status = trim((string)($_GET["status"] ?? ""));
  $type = trim((string)($_GET["type"] ?? ""));

  $where = " WHERE 1=1 ";
  $params = [];

  if ($status !== "") {
    $where .= " AND p.status = ? ";
    $params[] = $status;
  }
  if ($type !== "") {
    if ($type === "new_hire" || $type === "recruitment") {
      $where .= " AND (p.$typeCol = 'new_hire' OR p.$typeCol = 'recruitment') ";
    } elseif ($type === "department_transfer" || $type === "transfer") {
      $where .= " AND (p.$typeCol = 'department_transfer' OR p.$typeCol = 'transfer') ";
    } else {
      $where .= " AND p.$typeCol = ? ";
      $params[] = $type;
    }
  }

  $sql = "SELECT p.*, p.$typeCol AS type, p.$creatorCol AS created_by, p.$approverCol AS approved_by,
                 e.full_name AS employee_name, hr.full_name AS creator_name, bod.full_name AS approver_name
          FROM hr_proposals p
          LEFT JOIN employees e ON e.id = p.employee_id
          LEFT JOIN employees hr ON hr.id = p.$creatorCol
          LEFT JOIN employees bod ON bod.id = p.$approverCol
          $where ORDER BY (p.status = 'pending') DESC, p.id DESC LIMIT 100";

  $stmt = $pdo->prepare($sql);
  $stmt->execute($params);
  $rawRows = $stmt->fetchAll(PDO::FETCH_ASSOC);

  // Cache danh mục phòng ban và chức vụ để map giá trị nhanh
  $depts = $pdo->query("SELECT id, name FROM departments")->fetchAll(PDO::FETCH_KEY_PAIR);
  $positions = $pdo->query("SELECT id, title FROM positions")->fetchAll(PDO::FETCH_KEY_PAIR);

  $result = [];
  foreach ($rawRows as $r) {
    $rawT = (string)($r["type"] ?? "");
    $normT = $rawT === "recruitment" ? "new_hire" : ($rawT === "transfer" ? "department_transfer" : $rawT);

    $item = [
      "id" => (int)$r["id"],
      "type" => $normT,
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
          case "salary_change":
            if (isset($pData["old_salary"])) $item["old_value"] = number_format((float)$pData["old_salary"]) . " đ";
            if (isset($pData["base_salary"])) $item["new_value"] = number_format((float)$pData["base_salary"]) . " đ";
            break;
          case "transfer":
          case "department_transfer":
            if (isset($pData["old_department_id"]) && isset($depts[$pData["old_department_id"]])) {
              $item["old_value"] = "Phòng " . $depts[$pData["old_department_id"]];
            }
            if (!empty($item["dept_name"])) $item["new_value"] = "Phòng " . $item["dept_name"];
            break;
          case "promotion":
            if (isset($pData["old_position_id"]) && isset($positions[$pData["old_position_id"]])) {
              $item["old_value"] = $positions[$pData["old_position_id"]];
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

// 2. BOD (Giám đốc / Phó Giám đốc) phê duyệt / từ chối đề xuất
if ($a === "approve" && $m === "POST") {
  $me = require_login($pdo);

  // Chỉ Giám đốc, Phó Giám đốc hoặc Admin mới được duyệt. Thư ký BOD bị chặn!
  if (!is_director($me)) {
    err("Chỉ Giám đốc hoặc Phó Giám đốc mới có thẩm quyền phê duyệt đề xuất nhân sự", 403);
  }

  $b = body();
  $id = (int)($b["id"] ?? 0);
  $status = trim((string)($b["status"] ?? ""));
  if (!$id || !in_array($status, ["approved", "rejected"], true)) {
    err("Thieu id hoac status khong hop le (approved/rejected)", 422);
  }

  $cols = hr_proposals_columns($pdo);
  $typeCol = in_array("proposal_type", $cols, true) ? "proposal_type" : "type";
  $creatorCol = in_array("proposed_by", $cols, true) ? "proposed_by" : "created_by";
  $approverCol = in_array("reviewed_by", $cols, true) ? "reviewed_by" : "approved_by";
  $approverTimeCol = in_array("reviewed_at", $cols, true) ? "reviewed_at" : "processed_at";

  $stmt = $pdo->prepare("SELECT * FROM hr_proposals WHERE id = ? LIMIT 1");
  $stmt->execute([$id]);
  $prop = $stmt->fetch(PDO::FETCH_ASSOC);
  if (!$prop) err("Khong tim thay de xuat", 404);

  // 1. BOD CHỈ ĐƯỢC DUYỆT KHI TRẠNG THÁI LÀ 'pending' (ĐÃ QUA HR KIỂM DUYỆT).
  // Nếu là 'hr_pending' (vẫn đang chờ Manager HR kiểm duyệt nội bộ), BOD chưa có thẩm quyền duyệt.
  if ($prop["status"] === "hr_pending") {
    err("Hồ sơ này còn đang chờ Trưởng phòng HR kiểm duyệt nội bộ (hr_pending), BOD chưa có thẩm quyền duyệt", 409);
  }
  if ($prop["status"] !== "pending") {
    err("Đề xuất này không ở trạng thái chờ duyệt (pending)", 409);
  }

  // Cập nhật trạng thái đề xuất
  $upd = $pdo->prepare("UPDATE hr_proposals SET status=?, $approverCol=?, $approverTimeCol=NOW() WHERE id=? AND status='pending'");
  $upd->execute([$status, (int)$me["id"], $id]);
  if ($upd->rowCount() === 0) err("Đề xuất đã được xử lý bởi người khác", 409);

  $applied = false;
  if ($status === "approved") {
    $empId = (int)($prop["employee_id"] ?? 0);
    $propType = $prop[$typeCol] ?? $prop["type"] ?? "";

    // Parse JSON data if present
    $pData = [];
    if (!empty($prop["proposed_data"])) {
      $pData = is_array($prop["proposed_data"]) ? $prop["proposed_data"] : json_decode($prop["proposed_data"], true);
      if (!is_array($pData)) $pData = [];
    }

    switch ($propType) {
      case "recruitment":
      case "new_hire":
        // THEO LUỒNG ĐÃ CHỐT: BOD CHỈ DUYỆT ĐỀ XUẤT TUYỂN DỤNG.
        // KHÔNG TỰ ĐỘNG TẠO TÀI KHOẢN HOẶC GÁN MẬT KHẨU MẶC ĐỊNH 123456 TẠI ĐÂY.
        // SAU KHI BOD DUYỆT, HR SẼ LÀ NGƯỜI HOÀN THIỆN VÀ KÍCH HOẠT TÀI KHOẢN QUA LUỒNG HR (hr_proposals.php?action=activate).
        $applied = true;
        break;

      case "salary_change":
        $newSal = $pData["base_salary"] ?? $pData["new_salary"] ?? $prop["new_value"] ?? null;
        if ($empId > 0 && is_numeric($newSal)) {
          $pdo->prepare("UPDATE employees SET base_salary=? WHERE id=?")->execute([(float)$newSal, $empId]);
          $applied = true;
        }
        break;

      case "department_transfer":
        $newDept = $pData["department_id"] ?? $pData["new_department_id"] ?? $prop["new_value"] ?? null;
        if ($empId > 0 && is_numeric($newDept)) {
          $pdo->prepare("UPDATE employees SET department_id=? WHERE id=?")->execute([(int)$newDept, $empId]);
          $applied = true;
        }
        break;

      case "promotion":
        $newPos = $pData["position_id"] ?? $pData["new_position_id"] ?? $prop["new_value"] ?? null;
        if ($empId > 0 && is_numeric($newPos)) {
          $pdo->prepare("UPDATE employees SET position_id=? WHERE id=?")->execute([(int)$newPos, $empId]);
          $applied = true;
        }
        break;

      case "termination":
        if ($empId > 0) {
          $pdo->prepare("UPDATE employees SET status=0 WHERE id=?")->execute([$empId]);
          $pdo->prepare("DELETE FROM api_tokens WHERE employee_id=?")->execute([$empId]);
          $applied = true;
        }
        break;
    }
  }

  // Thông báo kết quả về cho HR
  $creatorId = (int)($prop[$creatorCol] ?? 0);
  if ($creatorId > 0) {
    $ok = ($status === "approved");
    notify_push($pdo, $creatorId, $ok ? "proposal_approved" : "proposal_rejected",
      $ok ? "Đề xuất nhân sự đã được Ban Giám đốc phê duyệt" : "Đề xuất nhân sự bị từ chối",
      "Đề xuất " . proposal_type_label($prop[$typeCol] ?? "") . " đã được " . $me["full_name"] . ($ok ? " phê duyệt thành công." : " từ chối."),
      $id);
  }

  out(["id" => $id, "status" => $status, "applied" => $applied]);
}

err("Unknown proposals action");
?>
