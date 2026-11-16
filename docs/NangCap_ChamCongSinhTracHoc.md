# Nâng cấp chấm công sinh trắc học — khuôn mặt & vân tay (16/11/2026)

## Tổng quan kiến trúc

| Phần | Công nghệ | Ghi chú |
|---|---|---|
| Phát hiện + cắt khuôn mặt | ML Kit Face Detection (trên điện thoại) | Bắt buộc đúng 1 mặt, mặt ≥ 120px, không quay ngang quá 30° |
| Trích đặc trưng khuôn mặt | FaceNet TFLite (`assets/facenet.tflite`, input 160×160 → embedding 128 chiều, chuẩn hoá L2) | Chạy hoàn toàn trên điện thoại |
| Đối khớp | Server so **cosine similarity** với vector đã đăng ký, ngưỡng **0.70** | Hằng `FACE_SIM_THRESHOLD` trong `api/biometrics_lib.php` |
| Vân tay | AndroidX **BiometricPrompt** (BIOMETRIC_STRONG) | Android không cho app đọc dữ liệu vân tay thô — vân tay chỉ đối chiếu trên thiết bị với mẫu đã đăng ký trong Cài đặt máy; app nhận kết quả rồi mới gọi API |
| Dữ liệu lưu | Bảng `employee_faces` chỉ lưu **vector số**, KHÔNG lưu ảnh gốc; ảnh chụp xử lý xong xoá ngay khỏi cache | `attendances` thêm `check_in/out_method` + `check_in/out_face_score` để truy vết |

## Thay đổi CSDL — migration 011

`database/migrations/011_biometric_attendance.sql`: tạo bảng `employee_faces`
(employee_id UNIQUE FK, embedding TEXT JSON, model) + 4 cột mới ở `attendances`.
API cũng tự tạo khi thiếu (`bio_ensure_schema()`), nên DB chưa chạy migration vẫn dùng được.

## API mới / thay đổi

- `POST biometrics.php?action=face-enroll` — `{embedding: [128 số], model}` — đăng ký/đăng ký lại cho chính người đăng nhập.
- `GET biometrics.php?action=face-status` — trạng thái đăng ký (admin/HR xem được của người khác, không lộ vector).
- `POST biometrics.php?action=face-remove` — xoá đăng ký (chính chủ hoặc admin/HR).
- `attendances.php?action=checkin/checkout` — nhận thêm `method`:
  - `gps` (mặc định, như cũ), `face` (bắt buộc `face_embedding`, server đối khớp; chưa đăng ký → 422, không khớp → 403), `fingerprint` (bắt buộc `biometric_verified: true` từ BiometricPrompt).
  - Response và `action=today` trả thêm `method`, `face_score`, `check_in/out_method`.

## App Android

- Trang chủ: thêm 2 nút **🙂 Khuôn mặt** / **👆 Vân tay** trong thẻ chấm công (tự hiểu đang đến lượt vào hay ra) + dòng "Đăng ký / cập nhật khuôn mặt ›" mở màn **Sinh trắc học** (`FaceEnrollActivity`): xem trạng thái, đăng ký lại, xoá đăng ký.
- `FaceCaptureActivity` dùng chung cho đăng ký & chấm công: chụp ảnh full-size → ML Kit cắt mặt → FaceNet trích embedding → trả vector cho màn gọi.
- Thư viện mới: `androidx.biometric:1.1.0`, `com.google.mlkit:face-detection:16.1.7`, `org.tensorflow:tensorflow-lite:2.14.0`.

## Kịch bản demo

1. Mở XAMPP (Apache + MySQL) trên Mac — API ở htdocs đã cập nhật, DB tự bổ sung bảng/cột khi gọi lần đầu (hoặc chạy migration 011 trong phpMyAdmin).
2. Build app từ thư mục `android/` trong Android Studio (Sync Gradle để tải 3 thư viện mới; model FaceNet đã nằm sẵn trong `assets/`).
3. Đăng nhập → Trang chủ → "Đăng ký / cập nhật khuôn mặt" → chụp đăng ký (1 lần).
4. Bấm **🙂 Khuôn mặt** → chụp lại → chấm công thành công; thẻ chấm công hiện "· Khuôn mặt". Thử nhờ người khác chụp → bị từ chối "Khuôn mặt không khớp".
5. Bấm **👆 Vân tay** → chạm cảm biến → chấm công ra; thẻ hiện "Ra bằng Vân tay".
6. Đối chiếu DB: `attendances.check_in_method='face'`, `check_in_face_score` ≈ 0.8–0.99.

## Kiểm chứng đã làm (16/11/2026)

Test API end-to-end trên MariaDB + PHP server độc lập: **13/13 ca đạt** — đăng ký/trạng thái/xoá,
embedding sai chiều → 422, chưa đăng ký → 422, mặt người khác → 403, đúng người (cosine 0.9983) → chấm công OK,
vân tay chưa xác thực → 422 / đã xác thực → OK, chấm GPS cũ không đổi.
Mô hình FaceNet đã chạy thử bằng TFLite: input [1,160,160,3] float32, output 128 chiều.

## Giới hạn cần biết khi bảo vệ

- Chống giả mạo (anti-spoofing) mới ở mức cơ bản: ML Kit kiểm tra 1 khuôn mặt + góc mặt; chưa có liveness detection chuyên sâu (chớp mắt/mở miệng) — hướng phát triển: bật classification của ML Kit để yêu cầu chớp mắt trước khi chụp.
- Vân tay theo chuẩn Android là xác thực trên thiết bị (không có máy quét vân tay trung tâm); muốn chấm công tập trung tại 1 máy cố định thì dùng chính điện thoại/máy tính bảng của công ty làm kiosk với cùng cơ chế này.
- Ngưỡng 0.70 là điểm cân bằng thực nghiệm của FaceNet; môi trường ánh sáng kém có thể cần đăng ký lại ở nơi đủ sáng.
