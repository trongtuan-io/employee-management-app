<?php
// Thu vien gui email thong bao qua Gmail SMTP.
// Dung chung cau hinh SMTP voi mail_config.php (noi gui ma reset password).
//
// Nguyen tac thiet ke:
// - Moi thong bao trong app deu di qua notify_push() (auth_guard.php). Sau khi
//   luu thong bao, notify_push goi notify_email() o day de gui mot ban sao qua
//   email cho dung nhan vien do: don nghi moi/duoc duyet/bi tu choi, chot luong,
//   mo lai bang luong, da tra luong, giai trinh cham cong...
// - Gui mail la viec PHU: moi loi SMTP (sai cau hinh, mat mang, Gmail chan...)
//   deu duoc nuot gon, tra ve false, KHONG bao gio lam hong request chinh.
// - Neu SMTP_USER/SMTP_PASS con la placeholder trong mail_config.php thi bo qua
//   trong im lang (moi truong chua cau hinh mail).

require_once __DIR__ . "/mail_config.php";
require_once __DIR__ . "/lib/PHPMailer/Exception.php";
require_once __DIR__ . "/lib/PHPMailer/PHPMailer.php";
require_once __DIR__ . "/lib/PHPMailer/SMTP.php";

use PHPMailer\PHPMailer\PHPMailer;
use PHPMailer\PHPMailer\Exception;

function mail_smtp_configured() {
  if (!defined("SMTP_USER") || !defined("SMTP_PASS")) return false;
  if (SMTP_USER === "" || SMTP_PASS === "") return false;
  // Cac dang placeholder con sot trong file mau (ca 2 phien ban tung dung):
  // "dien-gmail-that-cua-ban@gmail.com", "email.cua.ban@gmail.com",
  // "DIEN_APP_PASSWORD_16_KY_TU", "mat-khau-ung-dung-16-ky-tu".
  foreach (["dien-gmail", "cua.ban", "dien_thanh", "example"] as $mark) {
    if (stripos(SMTP_USER, $mark) !== false) return false;
  }
  foreach (["DIEN_APP_PASSWORD", "mat-khau-ung-dung", "app_password"] as $mark) {
    if (stripos(SMTP_PASS, $mark) !== false) return false;
  }
  return true;
}

// Gui 1 email HTML qua Gmail SMTP. Tra ve true neu gui thanh cong.
function send_mail($toEmail, $toName, $subject, $htmlBody) {
  if (!mail_smtp_configured()) return false;
  if (!filter_var($toEmail, FILTER_VALIDATE_EMAIL)) return false;
  $mail = new PHPMailer(true);
  try {
    $mail->isSMTP();
    $mail->Host = SMTP_HOST;
    $mail->Port = (int) SMTP_PORT;
    // Kieu ma hoa theo cong: 465 = SMTPS, 587 = STARTTLS,
    // cong khac (chi dung khi test SMTP noi bo) = khong ma hoa.
    if ((int) SMTP_PORT === 465) $mail->SMTPSecure = PHPMailer::ENCRYPTION_SMTPS;
    elseif ((int) SMTP_PORT === 587) $mail->SMTPSecure = PHPMailer::ENCRYPTION_STARTTLS;
    // Mac dinh bat xac thuc; mail_config co the dat const SMTP_AUTH = false khi test noi bo.
    $mail->SMTPAuth = defined("SMTP_AUTH") ? (bool) SMTP_AUTH : true;
    if ($mail->SMTPAuth) {
      $mail->Username = SMTP_USER;
      $mail->Password = SMTP_PASS;
    }
    $mail->Timeout = 10;
    $mail->CharSet = "UTF-8";
    $mail->setFrom(SMTP_USER, defined("MAIL_FROM_NAME") ? MAIL_FROM_NAME : "Employee Management");
    $mail->addAddress($toEmail, $toName ?? "");
    $mail->isHTML(true);
    $mail->Subject = $subject;
    $mail->Body = $htmlBody;
    $mail->send();
    return true;
  } catch (Throwable $e) {
    return false;
  }
}

// Gui ban sao email cho mot thong bao trong app.
// $title/$body lay nguyen tu notify_push de noi dung mail khop thong bao trong app.
function notify_email(PDO $pdo, $userId, $title, $body) {
  try {
    $st = $pdo->prepare("SELECT email, full_name FROM employees WHERE id=? LIMIT 1");
    $st->execute([(int) $userId]);
    $u = $st->fetch(PDO::FETCH_ASSOC);
    if (!$u || empty($u["email"])) return false;
    $safeName = htmlspecialchars($u["full_name"] ?? "", ENT_QUOTES, "UTF-8");
    $safeBody = nl2br(htmlspecialchars($body ?? "", ENT_QUOTES, "UTF-8"));
    $html = "<p>Xin chào <b>" . $safeName . "</b>,</p>"
      . "<p>" . $safeBody . "</p>"
      . "<hr><p style='color:#888;font-size:12px'>Đây là email tự động từ hệ thống quản lý nhân viên. "
      . "Thông báo này cũng có trong mục Thông báo của ứng dụng. Vui lòng không trả lời email này.</p>";
    return send_mail($u["email"], $u["full_name"], $title, $html);
  } catch (Throwable $e) {
    return false;
  }
}
?>
