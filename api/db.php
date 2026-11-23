<?php
// Khong de loi PHP tho (message/path/SQL) lo ra response; loi chua bat se tra JSON 500 thong nhat.
ini_set("display_errors", "0");
error_reporting(E_ALL);
set_exception_handler(function ($e) {
  error_log("Unhandled exception: " . $e->getMessage());
  if (!headers_sent()) http_response_code(500);
  echo json_encode(["success" => false, "error" => "Loi he thong, vui long thu lai sau"]);
  exit;
});
// Moi gio giac trong he thong (cham cong, han token, ngay tao...) theo gio Viet Nam.
// Truoc day PHP chay theo mui gio mac dinh cua XAMPP nen gio cham cong bi lech.
date_default_timezone_set("Asia/Ho_Chi_Minh");
header("Content-Type: application/json; charset=utf-8");
header("Access-Control-Allow-Origin: *");
header("Access-Control-Allow-Methods: GET, POST, PUT, DELETE, OPTIONS");
header("Access-Control-Allow-Headers: Content-Type, Authorization");
if ($_SERVER['REQUEST_METHOD'] === 'OPTIONS') exit(0);

$host = "localhost"; $db = "employee_management"; $user = "root"; $pass = "";
try {
  $pdo = new PDO("mysql:host=$host;dbname=$db;charset=utf8mb4", $user, $pass);
  $pdo->setAttribute(PDO::ATTR_ERRMODE, PDO::ERRMODE_EXCEPTION);
  // Dong bo mui gio phien MySQL voi PHP de NOW()/CURDATE() khop date().
  $pdo->exec("SET time_zone = '+07:00'");
} catch (Exception $e) {
  error_log("DB connect failed: " . $e->getMessage());
  http_response_code(500);
  echo json_encode(["success" => false, "error" => "Khong ket noi duoc co so du lieu"]);
  exit;
}
function body() { return json_decode(file_get_contents("php://input"), true) ?? []; }
function out($d) { echo json_encode(["success" => true, "data" => $d]); exit; }
function err($m, $c = 400) { http_response_code($c); echo json_encode(["success" => false, "error" => $m]); exit; }
?>
