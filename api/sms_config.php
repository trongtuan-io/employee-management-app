<?php
// SMS OTP qua SpeedSMS.vn (co go trial mien phi de demo)
// Dang ky tai https://speedsms.vn -> nap thu -> lay Access Token -> dien vao day.
// Nguoi 1 phu trach. Tai lieu: https://speedsms.vn/tai-lieu-huong-dan
const SMS_ACCESS_TOKEN = "access-token-cua-ban"; // <-- DOI THANH ACCESS TOKEN THAT
const SMS_BRANDNAME = "EmployeeApp"; // brandname da dang ky (type 2). Chua co thi dung type 5.
const SMS_DEBUG_SHOW_CODE = true; // true = tra ma trong response de test. Demo that thi false.

// Chuan hoa SDT Viet Nam: 0xxx -> 84xxx (gateway yeu cau)
function norm_phone($p) {
  $d = preg_replace('/\D/', '', (string)$p);
  if (strpos($d, '0') === 0) $d = '84' . substr($d, 1);
  return $d;
}

function send_sms_code($toPhone, $code) {
  $to = norm_phone($toPhone);
  $content = "Ma xac nhan lay lai mat khau EmployeeApp cua ban la: " . $code . ". Hieu luc 10 phut.";
  $ch = curl_init("https://api.speedsms.vn/index.php/sms/send");
  curl_setopt_array($ch, [
    CURLOPT_RETURNTRANSFER => true,
    CURLOPT_POST => true,
    CURLOPT_HTTPHEADER => [
      "Content-Type: application/json",
      "Authorization: Basic " . base64_encode(SMS_ACCESS_TOKEN . ":x")
    ],
    CURLOPT_POSTFIELDS => json_encode([
      "to" => [$to], "content" => $content, "type" => 2, "brandname" => SMS_BRANDNAME
    ]),
    CURLOPT_TIMEOUT => 15,
  ]);
  $res = curl_exec($ch);
  $http = curl_getinfo($ch, CURLINFO_HTTP_CODE);
  curl_close($ch);
  $j = json_decode($res, true);
  return ($http === 200 && isset($j["status"]) && $j["status"] === "success");
}
?>
