# Employee Management App (Android)

Đây là ứng dụng Android cho hệ thống Quản lý nhân sự, kết nối với Backend PHP qua REST API.

## ⚠️ Lưu ý quan trọng khi chạy trên Máy ảo (Emulator)

Khi bạn chạy máy chủ PHP (XAMPP/MAMP/WAMP) trên máy tính của bạn (Localhost), máy ảo Android **KHÔNG THỂ** kết nối tới bằng địa chỉ `localhost` hay `127.0.0.1`. Bạn BẮT BUỘC phải sử dụng **Địa chỉ IP mạng LAN (Wi-Fi)** thực tế của máy tính.

Đôi khi cục phát Wi-Fi sẽ cấp lại một IP mới cho máy tính của bạn, dẫn đến lỗi **"failed to connect"**. Khi đó, bạn cần lấy lại IP và cập nhật vào ứng dụng.

### Hướng dẫn tự lấy URL (Địa chỉ IP) để chạy App:

#### BƯỚC 1: Lấy địa chỉ IP của máy tính
**Nếu bạn dùng máy Mac (Macbook):**
1. Mở ứng dụng **Terminal** (Nhấn `Command + Space` gõ Terminal).
2. Copy và dán lệnh sau vào Terminal rồi nhấn Enter:
   ```bash
   ifconfig | grep "inet " | grep -v 127.0.0.1
   ```
3. Bạn sẽ thấy một dòng chứa địa chỉ IP dạng `192.168.x.x` (Ví dụ: `192.168.11.3`). Hãy ghi nhớ dãy số này.

*(**Nếu bạn dùng máy Windows:** Mở Command Prompt (cmd), gõ `ipconfig`, tìm dòng `IPv4 Address` trong phần Wi-Fi).*

#### BƯỚC 2: Cập nhật URL vào mã nguồn Android
1. Mở Android Studio.
2. Điều hướng tới file cấu hình API:
   `app -> java -> com.example.employee_management_app -> ApiClient.java`
3. Cập nhật lại chuỗi `BASE_URL` bằng IP bạn vừa lấy được. Chú ý thêm `http://` ở trước và tên thư mục chứa PHP `/employee-api/` ở sau.
   ```java
   // Ví dụ nếu IP là 192.168.11.3
   private static final String BASE_URL = "http://192.168.11.3/employee-api/";
   ```

#### BƯỚC 3: Chạy lại ứng dụng
- Nhấn nút **Run / Rerun** (biểu tượng hình tam giác hoặc mũi tên vòng cung màu xanh) trên thanh công cụ của Android Studio để cài đặt lại ứng dụng với cấu hình IP mới nhất.

---

## Các tính năng hiện tại
- [x] Giao diện Đăng nhập.
- [x] Kết nối API `auth.php?action=login`.
- [x] Chuyển hướng tới Trang Chủ (MainActivity) mang theo phiên đăng nhập.
- [ ] Danh sách nhân viên (Employee List).
- [ ] Quản lý chấm công (Check-in).
- [ ] Quản lý nghỉ phép & lương.