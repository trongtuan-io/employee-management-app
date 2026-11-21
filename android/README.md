# Employee Management App (Android)

Ứng dụng Android (Java + Retrofit + Glide) cho hệ thống Quản lý nhân viên, kết nối Backend PHP qua REST API (xem `../api/README.md`). Thư mục `android/` này là project Android Studio **build được và dùng để demo**; repo còn một cây `app/` ở gốc được giữ đồng bộ — khi mở project, mở thư mục `android/`.

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

Tất cả đều đã hoàn thành và chạy được:

- [x] Đăng nhập / đăng ký / quên mật khẩu qua email (mã 6 số), phiên đăng nhập bằng token Bearer.
- [x] Trang chủ theo vai trò: thẻ chấm công, khối nghiệp vụ theo phòng ban, bảng tin công ty, chuông thông báo kèm số chưa đọc.
- [x] Quản lý nhân viên theo phạm vi phòng (xem/sửa/tạo/khoá tài khoản, avatar, import hàng loạt — chỉ Admin và trưởng phòng, staff không thấy trang này).
- [x] Phòng ban & thành viên; hồ sơ cá nhân; đổi mật khẩu; tạo tài khoản (Admin).
- [x] Chấm công vào/ra kèm GPS (gắn cờ ngoài văn phòng), lịch sử và báo cáo chấm công, xuất bảng công CSV.
- [x] Chấm công sinh trắc học: **khuôn mặt** (ML Kit + FaceNet TFLite, đăng ký khuôn mặt ngay trong app) và **vân tay** (BiometricPrompt).
- [x] Xin nghỉ với quỹ phép 12 ngày/năm, duyệt/từ chối đơn theo phòng, lịch vắng toàn công ty/phòng.
- [x] Giải trình quên chấm công và duyệt giải trình.
- [x] Bảng lương: xem lương cá nhân sau khi chốt, chi tiết từng khoản (BHXH, thuế TNCN), bảng lương tổng hợp toàn công ty, lịch sử thay đổi, quản lý phụ cấp chính sách, xuất Excel (CSV).
- [x] Thông báo trong app (chạm để đánh dấu đã đọc và mở đúng màn liên quan); các sự kiện quan trọng còn có bản sao qua email ở phía server.

## Các màn hình chính (Activity)

`LoginActivity`, `HomeActivity`, `StaffListActivity`, `DepartmentActivity`, `ProfileActivity`, `AdminActivity`, `CreateAccountActivity`, `ImportEmployeesActivity`, `LeaveActivity`, `AbsenceCalendarActivity`, `ExplanationActivity`, `SalaryActivity`, `SalaryDetailActivity`, `SalaryOverviewActivity`, `SalaryPolicyActivity`, `NotificationsActivity`, `FaceEnrollActivity`, `FaceCaptureActivity`, `ForgotPasswordActivity`, `ChangePasswordActivity`.
