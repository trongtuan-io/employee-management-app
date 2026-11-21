# Nâng cấp: Thông báo qua email (Gmail SMTP)

Ngày thực hiện: 19/11/2026.

## 1. Tổng quan

Trước đây hệ thống chỉ có thông báo trong ứng dụng (bảng `notifications`, chuông ở Trang chủ). Sau nâng cấp, **mỗi thông báo trong app đồng thời được gửi một bản sao qua email** tới địa chỉ email trong hồ sơ của nhân viên nhận thông báo, dùng Gmail SMTP — cùng cấu hình với tính năng quên mật khẩu (`mail_config.php`, thư viện PHPMailer đã có sẵn trong `api/lib/PHPMailer`).

Các sự kiện được gửi mail (tự động, vì mọi sự kiện đều đi qua một hàm chung `notify_push`):

| Sự kiện | Người nhận email |
|---|---|
| Có đơn nghỉ mới | Admin + trưởng phòng cùng phòng người nộp + phòng HR |
| Đơn nghỉ được duyệt / bị từ chối | Người nộp đơn |
| Bảng lương đã chốt | Nhân viên chủ dòng lương (kèm thực lĩnh) |
| Mở lại bảng lương | Nhân viên chủ dòng lương |
| Đã trả lương | Nhân viên chủ dòng lương |
| Đơn giải trình chấm công mới / kết quả duyệt | Người duyệt / người nộp (cùng cơ chế thông báo sẵn có) |

Quên mật khẩu vẫn giữ nguyên luồng cũ: mã xác thực 6 số gửi qua email (`send_reset_code`), mã chỉ lưu dạng băm trong DB, hết hạn sau 10 phút.

## 2. Thay đổi mã nguồn

- **`api/mail_lib.php` (mới):**
  - `send_mail($to, $name, $subject, $htmlBody)` — gửi email HTML qua Gmail SMTP (PHPMailer). Kiểu mã hoá theo cổng: 465 = SMTPS, 587 = STARTTLS. Hằng tuỳ chọn `SMTP_AUTH` trong `mail_config.php` (mặc định `true` khi không khai báo) cho phép tắt xác thực khi test SMTP nội bộ.
  - `notify_email($pdo, $userId, $title, $body)` — tra email + họ tên của nhân viên, dựng nội dung mail (lời chào + nội dung thông báo + chân trang) và gọi `send_mail`.
  - `mail_smtp_configured()` — nếu `SMTP_USER`/`SMTP_PASS` còn là placeholder thì bỏ qua trong im lặng.
- **`api/auth_guard.php`:** `notify_push()` sau khi lưu thông báo vào DB sẽ gọi `notify_email()` (nạp lười `mail_lib.php`, chỉ khi cả `mail_lib.php` và `mail_config.php` cùng tồn tại).
- **`api/mail_config.example.php`:** bổ sung ghi chú về việc dùng chung cấu hình cho thông báo email.

## 3. Nguyên tắc an toàn

- Gửi mail là việc phụ: mọi lỗi SMTP (sai cấu hình, mất mạng, Gmail từ chối) đều được bắt và bỏ qua, **không bao giờ làm hỏng request chính** (tạo đơn, duyệt đơn, chốt lương vẫn thành công kể cả khi mail gửi thất bại).
- Nội dung mail lấy nguyên từ nội dung thông báo trong app nên hai kênh luôn khớp nhau; họ tên và nội dung được thoát HTML (`htmlspecialchars`) trước khi đưa vào thân mail.
- Không lưu thêm dữ liệu mới: dùng email đã có trong hồ sơ nhân viên.

## 4. Cấu hình để gửi được mail thật

Trong `api/mail_config.php` trên máy chủ (XAMPP htdocs):

1. `SMTP_USER` = địa chỉ Gmail dùng để gửi.
2. `SMTP_PASS` = **App Password** 16 ký tự (Google Account → Bảo mật → Xác minh 2 bước → Mật khẩu ứng dụng). Không dùng mật khẩu Gmail thường.
3. Giữ `SMTP_HOST = smtp.gmail.com`, `SMTP_PORT = 587`.

Lưu ý bảo mật: app password từng nằm trong lịch sử git của repo public phải được **thu hồi** trên tài khoản Google và thay bằng app password mới điền vào `mail_config.php` trên máy chủ (file này không nằm trong git).

Đánh đổi đã biết: gửi mail đồng bộ trong request nên thao tác sinh nhiều thông báo (ví dụ tạo đơn nghỉ gửi cho nhiều người duyệt) có thể chậm thêm vài giây. Quy mô hiện tại chấp nhận được; nếu cần, có thể chuyển sang hàng đợi gửi nền sau.

## 5. Kiểm thử đã thực hiện (19/11/2026)

Môi trường test độc lập: MariaDB tạm + PHP built-in server + SMTP giả lập bắt toàn bộ thư gửi đi. Kết quả:

- Đơn nghỉ mới → mail tới đủ 3 người duyệt (admin, trưởng phòng HR, sếp HR), tiêu đề "Đơn nghỉ mới chờ duyệt"; không gửi cho chính người nộp.
- Duyệt đơn → mail "Đơn nghỉ đã được duyệt" tới người nộp.
- Chốt lương → mail "Bảng lương tháng 11/2026 đã chốt" tới đúng nhân viên chủ dòng lương (kiểm chứng với 2 nhân viên khác nhau).
- Đã trả lương → mail "Đã trả lương tháng 11/2026" tới nhân viên.
- Quên mật khẩu → nhận mail mã 6 số, xác thực mã, đặt mật khẩu mới và đăng nhập bằng mật khẩu mới thành công.
