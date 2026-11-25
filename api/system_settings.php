<?php
function system_settings_read(PDO $pdo): array {
  $pdo->exec("CREATE TABLE IF NOT EXISTS system_settings (
    setting_key VARCHAR(50) PRIMARY KEY,
    setting_value TEXT NOT NULL,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

  $defaults = [
    "office_lat" => "21.0378000",
    "office_lng" => "105.7830000",
    "office_radius" => "500",
    "annual_leave_quota" => "12",
  ];
  $stored = $pdo->query("SELECT setting_key, setting_value FROM system_settings")->fetchAll(PDO::FETCH_KEY_PAIR);
  foreach ($defaults as $key => $default) {
    if (isset($stored[$key]) && system_setting_valid($key, $stored[$key])) $defaults[$key] = $stored[$key];
  }
  return $defaults;
}

function system_setting_valid(string $key, $value): bool {
  if (!is_scalar($value) || is_bool($value) || !is_numeric($value)) return false;
  $number = (float)$value;
  return match ($key) {
    "office_lat" => $number >= -90 && $number <= 90,
    "office_lng" => $number >= -180 && $number <= 180,
    "office_radius", "annual_leave_quota" => filter_var($value, FILTER_VALIDATE_INT, ["options" => ["min_range" => 1]]) !== false,
    default => false,
  };
}

