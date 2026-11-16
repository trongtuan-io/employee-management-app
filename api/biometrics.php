<?php
require_once __DIR__ . "/db.php";
require_once __DIR__ . "/auth_guard.php";
require_once __DIR__ . "/biometrics_lib.php";
$a = $_GET["action"] ?? ""; $m = $_SERVER["REQUEST_METHOD"];

// Dang ky / dang ky lai khuon mat cho CHINH nguoi dang nhap.
// Body: { embedding: [128 so], model?: "facenet-128" }
// App da trich embedding tren dien thoai; server chuan hoa L2 roi luu de
// viec so khop sau nay chi con la tich vo huong.
if ($a === "face-enroll" && $m === "POST") {
  $me = require_login($pdo);
  bio_ensure_schema($pdo);
  $b = body();
  $emb = bio_validate_embedding($b["embedding"] ?? null);
  if ($emb === null) err("Embedding khong hop le: can mang " . FACE_EMBEDDING_DIM . " so thuc", 422);
  // model la dinh danh ASCII cua mo hinh (vd. "facenet-128"): loc ky tu la roi cat 50.
  $model = "facenet-128";
  if (is_string($b["model"] ?? null)) {
    $clean = preg_replace("/[^A-Za-z0-9._-]/", "", trim($b["model"]));
    if ($clean !== "") $model = substr($clean, 0, 50);
  }
  $pdo->prepare("INSERT INTO employee_faces(employee_id, embedding, model) VALUES(?,?,?)
                 ON DUPLICATE KEY UPDATE embedding=VALUES(embedding), model=VALUES(model)")
    ->execute([(int)$me["id"], json_encode($emb), $model]);
  out(["registered" => true, "model" => $model]);
}

// Trang thai dang ky khuon mat. Mac dinh cua chinh minh; admin/HR xem duoc
// cua nhan vien khac (chi trang thai, KHONG tra vector embedding ra ngoai).
if ($a === "face-status" && $m === "GET") {
  $me = require_login($pdo);
  bio_ensure_schema($pdo);
  $emp = isset($_GET["employee_id"]) ? (int)$_GET["employee_id"] : (int)$me["id"];
  if ($emp !== (int)$me["id"] && $me["role"] !== "admin" && !is_hr($me)) {
    err("Khong co quyen xem trang thai khuon mat cua nguoi khac", 403);
  }
  $stmt = $pdo->prepare("SELECT model, updated_at FROM employee_faces WHERE employee_id=? LIMIT 1");
  $stmt->execute([$emp]);
  $row = $stmt->fetch(PDO::FETCH_ASSOC);
  if (!$row) out(["registered" => false, "model" => null, "updated_at" => null]);
  out(["registered" => true, "model" => $row["model"], "updated_at" => $row["updated_at"]]);
}

// Xoa dang ky khuon mat: chinh chu duoc xoa cua minh; admin/HR xoa duoc cua
// nhan vien (vd. nhan vien nghi viec hoac can dang ky lai tu dau).
if ($a === "face-remove" && $m === "POST") {
  $me = require_login($pdo);
  bio_ensure_schema($pdo);
  $b = body();
  $emp = isset($b["employee_id"]) ? (int)$b["employee_id"] : (int)$me["id"];
  if ($emp !== (int)$me["id"] && $me["role"] !== "admin" && !is_hr($me)) {
    err("Khong co quyen xoa dang ky khuon mat cua nguoi khac", 403);
  }
  $stmt = $pdo->prepare("DELETE FROM employee_faces WHERE employee_id=?");
  $stmt->execute([$emp]);
  if ($stmt->rowCount() === 0) err("Nhan vien nay chua dang ky khuon mat", 404);
  out(["registered" => false]);
}
err("Unknown biometric action");
?>
