<?php
// Cau hinh gui mail lay lai mat khau (Nguoi 1 phu trach)
// Gmail: bat "Xac minh 2 buoc" -> Google Account > Bao mat > "Mat khau ung dung" -> tao 1 mat khau 16 ky tu -> dien vao SMTP_PASS.
const SMTP_HOST = "smtp.gmail.com";
const SMTP_PORT = 587;
const SMTP_USER = "email.cua.ban@gmail.com";   // <-- DOI THANH GMAIL THAT
const SMTP_PASS = "mat-khau-ung-dung-16-ky-tu"; // <-- DOI THANH APP PASSWORD
const MAIL_FROM_NAME = "Employee Management";
// Che do test: tra ma code trong response de test khong can mail that.
// Khi demo that: dien SMTP dung roi chuyen thanh false.
const DEBUG_SHOW_CODE = true;

use PHPMailer\PHPMailer\PHPMailer;
use PHPMailer\PHPMailer\Exception;

function send_reset_code($toEmail, $toName, $code) {
  require_once __DIR__ . "/lib/PHPMailer/Exception.php";
  require_once __DIR__ . "/lib/PHPMailer/PHPMailer.php";
  require_once __DIR__ . "/lib/PHPMailer/SMTP.php";
  $mail = new PHPMailer(true);
  try {
    $mail->isSMTP();
    $mail->Host = SMTP_HOST;
    $mail->SMTPAuth = true;
    $mail->Username = SMTP_USER;
    $mail->Password = SMTP_PASS;
    $mail->SMTPSecure = PHPMailer::ENCRYPTION_STARTTLS;
    $mail->Port = SMTP_PORT;
    $mail->CharSet = "UTF-8";
    $mail->setFrom(SMTP_USER, MAIL_FROM_NAME);
    $mail->addAddress($toEmail, $toName);
    $mail->isHTML(true);
    $mail->Subject = "Ma xac nhan lay lai mat khau";
    $mail->Body = "Xin chao <b>" . htmlspecialchars($toName) . "</b>,<br><br>"
      . "Ma xac nhan lay lai mat khau cua ban la: <h2 style='letter-spacing:6px'>" . $code . "</h2>"
      . "Ma het han sau <b>10 phut</b>. Neu khong phai ban yeu cau, hay bo qua email nay.";
    $mail->send();
    return true;
  } catch (Exception $e) { return false; }
}
?>
