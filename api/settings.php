<?php
// System settings API for BOD: GPS office location & Annual Leave quota
require_once __DIR__ . "/db.php";
require_once __DIR__ . "/auth_guard.php";

$a = $_GET["action"] ?? "get";
$m = $_SERVER["REQUEST_METHOD"];

function settings_ensure_table(PDO $pdo) {
  static $done = false;
  if ($done) return;
  $done = true;
  $pdo->exec("CREATE TABLE IF NOT EXISTS system_settings (
    setting_key VARCHAR(50) PRIMARY KEY,
    setting_value TEXT NOT NULL,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

  $defaults = [
    "office_lat" => "21.0371300",
    "office_lng" => "105.7831500",
    "office_radius" => "500",
    "annual_leave_quota" => "12",
  ];
  $ins = $pdo->prepare("INSERT IGNORE INTO system_settings (setting_key, setting_value) VALUES (?, ?)");
  foreach ($defaults as $k => $v) {
    $ins->execute([$k, $v]);
  }
}

if ($a === "get" && $m === "GET") {
  settings_ensure_table($pdo);
  $rows = $pdo->query("SELECT setting_key, setting_value FROM system_settings")->fetchAll(PDO::FETCH_KEY_PAIR);
  out($rows);
}

if ($a === "save" && $m === "POST") {
  $me = require_login($pdo);
  if (!is_director($me) && $me["role"] !== "admin") {
    err("Chỉ Giám đốc hoặc Admin mới có quyền thay đổi cấu hình hệ thống", 403);
  }
  settings_ensure_table($pdo);
  $b = body();
  $upd = $pdo->prepare("INSERT INTO system_settings (setting_key, setting_value) VALUES (?, ?) ON DUPLICATE KEY UPDATE setting_value = VALUES(setting_value)");

  $allowedKeys = ["office_lat", "office_lng", "office_radius", "annual_leave_quota"];
  foreach ($allowedKeys as $k) {
    if (isset($b[$k])) {
      $upd->execute([$k, (string)$b[$k]]);
    }
  }
  out(["success" => true]);
}

err("Unknown settings action");
?>
