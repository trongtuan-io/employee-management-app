<?php
require_once __DIR__ . "/db.php";
require_once __DIR__ . "/auth_guard.php";
require_once __DIR__ . "/system_settings.php";

$a = $_GET["action"] ?? "get";
$m = $_SERVER["REQUEST_METHOD"];
$me = require_login($pdo);
if (!is_director($me)) err("Chi Ban Giam doc duoc quan ly cau hinh he thong", 403);

if ($a === "get" && $m === "GET") out(system_settings_read($pdo));

if ($a === "save" && $m === "POST") {
  $allowed = system_settings_read($pdo);
  $input = body();
  if (!is_array($input) || !$input || array_diff_key($input, $allowed)) err("Cau hinh khong hop le", 422);
  foreach ($input as $key => $value) {
    if (!system_setting_valid($key, $value)) err("Gia tri $key khong hop le", 422);
  }
  $pdo->beginTransaction();
  $upd = $pdo->prepare("INSERT INTO system_settings (setting_key, setting_value) VALUES (?, ?)
    ON DUPLICATE KEY UPDATE setting_value=?");
  foreach ($input as $key => $value) $upd->execute([$key, (string)$value, (string)$value]);
  $pdo->commit();
  out(["saved" => count($input)]);
}
err("Unknown settings action", 404);

