# Employee Management App (Android)

Đây là ứng dụng Android cho hệ thống Quản lý nhân sự, kết nối với Backend PHP qua REST API.

## ⚠️ Lưu ý quan trọng khi chạy trên Máy ảo (Emulator)

Khi bạn chạy máy chủ PHP (XAMPP/MAMP/WAMP) trên máy tính của bạn (Localhost), máy ảo Android **KHÔNG THỂ** kết nối tới bằng địa chỉ `localhost` hay `127.0.0.1`. Bạn BẮT BUỘC phải sử dụng **Địa chỉ IP mạng LAN (Wi-Fi)** thực tế của máy tính.

Đôi khi cục phát Wi-Fi sẽ cấp lại một IP mới cho máy tính của bạn, dẫn đến lỗi **"failed to connect" / "Lỗi kết nối"**. Khi đó, bạn cần lấy lại IP và cập nhật vào ứng dụng. Điện thoại phải bắt **cùng Wi-Fi** với laptop.

### Hướng dẫn tự lấy URL (Địa chỉ IP) để chạy App:

#### BƯỚC 1: Lấy địa chỉ IP của máy tính
**Nếu bạn dùng máy Mac (Macbook):** mở **Terminal**, chạy:
```bash
ipconfig getifaddr en0
```
Bạn sẽ thấy địa chỉ IP dạng `192.168.x.x` (Ví dụ: `192.168.11.10`). Hãy ghi nhớ dãy số này.

*(**Nếu bạn dùng máy Windows:** Mở Command Prompt (cmd), gõ `ipconfig`, tìm dòng `IPv4 Address` trong phần Wi-Fi).*

#### BƯỚC 2: Cập nhật IP (không sửa code)
Mở file `local.properties` (ngang hàng `settings.gradle.kts`, file này gitignore nên mỗi máy tự giữ), thêm/sửa 1 dòng:
```properties
apiUrl=http://<IP-cua-ban>/employee-api/
```
Ví dụ: `apiUrl=http://192.168.11.10/employee-api/` (máy ảo dùng `http://10.0.2.2/employee-api/`).
Không sửa `ApiClient.java` — app tự đọc IP này lúc Sync Gradle.

#### BƯỚC 3: Chạy lại ứng dụng
- Bấm **Sync Now** trong Android Studio, rồi nhấn **Run / Rerun** để cài lại app với IP mới nhất.

## Ghi chú: đã gỡ Firebase

Quên mật khẩu chỉ dùng Email (server gửi mã 6 số). Kênh SMS Firebase đã gỡ nên clone về build chạy ngay, không cần `google-services.json`.

---

## Các tính năng hiện tại
- [x] Giao diện Đăng nhập.
- [x] Kết nối API `auth.php?action=login`.
- [x] Chuyển hướng tới Trang Chủ (MainActivity) mang theo phiên đăng nhập.
- [ ] Danh sách nhân viên (Employee List).
- [ ] Quản lý chấm công (Check-in).
- [ ] Quản lý nghỉ phép & lương.