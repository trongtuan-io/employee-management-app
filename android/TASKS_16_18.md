# Thành viên 2 — Task 16–18

## Kết quả

- **Task 16:** phòng ban lấy từ `employees.php?action=departments`; có “Tất cả phòng ban”, phối hợp với tìm theo họ tên/mã/email hiện có. Phòng ban rỗng/lỗi có thông báo và nút tải lại.
- **Task 17:** dùng phân trang Backend `page/limit`, 20 nhân viên/trang; có Trang trước/Trang sau, tổng số trang và tổng kết quả. Mỗi phản hồi thay danh sách cũ. Đổi từ khóa/phòng ban/cách sắp xếp về trang 1; đổi trang giữ các lựa chọn. Lưu trạng thái khi tạo lại Activity và khi quay về từ hồ sơ.
- **Task 18:** sắp xếp theo **họ tên đầy đủ** hoặc **username (mã nhân viên đang dùng)**, tăng/giảm. SQL lọc và sắp xếp trước LIMIT/OFFSET; thêm ID làm khóa phụ khi trùng tên.

## File sửa/thêm

| File | Lý do |
|---|---|
| `../api/employees.php` | Thêm sort theo danh sách cho phép, metadata phân trang, COUNT cùng điều kiện lọc, chặn tham số lỗi, giới hạn trang hợp lệ, lỗi JSON cho list/departments. Giữ định dạng data cũ và thứ tự ID giảm dần khi client không gửi sort. |
| `app/src/main/java/com/example/employee_management_app/EmployeeApi.java` | Thêm overload lấy nhân viên có đầy đủ tham số và API phòng ban; giữ chữ ký cũ. |
| `app/src/main/java/com/example/employee_management_app/EmployeeListResponse.java` | Đọc/kiểm tra pagination, giữ Item và cách hiển thị nhân viên cũ. |
| `app/src/main/java/com/example/employee_management_app/DepartmentListResponse.java` | Model phòng ban id/name cho spinner. |
| `app/src/main/java/com/example/employee_management_app/StaffListQuery.java` | Trạng thái truy vấn bất biến, giữ bộ lọc khi đổi trang và về trang đầu khi đổi điều kiện. |
| `app/src/main/java/com/example/employee_management_app/StaffListActivity.java` | Nối các lựa chọn và phân trang; debounce tìm kiếm 350 ms, hủy request cũ/bỏ phản hồi cũ, khóa nút chuyển trang khi tải, retry và thông báo lỗi/rỗng; xử lý khoảng cách với thanh trạng thái/điều hướng/bàn phím trên Pixel 7. Sửa dòng đầu bị chèn nhầm “git push…” trước package. |
| `app/src/main/res/layout/activity_staff_list.xml` | Thêm spinner, trạng thái tải/lỗi và hàng điều khiển trang; giữ ListView/ArrayAdapter. |
| `app/src/main/res/values/strings.xml` | Chuỗi giao diện và 4 lựa chọn sắp xếp. |
| `app/src/test/java/com/example/employee_management_app/StaffListQueryTest.java` | 7 unit test giữ/reset bộ lọc và trang. |
| `app/src/test/java/com/example/employee_management_app/EmployeeListContractTest.java` | 7 unit test tham số Retrofit, model và metadata API. |
| `app/src/androidTest/java/com/example/employee_management_app/StaffListActivityTest.java` | 5 test giao diện với 45 bản ghi giả; lỗi, dữ liệu rỗng, thao tác nhanh, phản hồi muộn, tạo lại Activity. Transport chỉ được thay trong test rồi phục hồi. |
| `app/src/androidTest/java/com/example/employee_management_app/StaffListLocalBackendTest.java` | Smoke test tùy chọn trên API cục bộ thật, chỉ đọc và chụp ảnh. |
| `tests/employee-list-api-test.php` | 50 kiểm tra SQL/API với 47 bản ghi, bảng TEMPORARY theo kết nối; không sửa bảng/dữ liệu thật. |
| `TASKS_16_18.md` | Báo cáo và hướng dẫn kiểm thử. |

Bản `C:\laragon\www\employee-api\employees.php` cũng đã được đồng bộ để kiểm thử cục bộ, khớp SHA-256 với source. Bản trước khi sửa được lưu tại `build/task16-18/employees.original.php`.

Không sửa Auth, Firebase, module thành viên khác, Gradle, IDE, db.php, local.properties hoặc ApiClient. URL hiện có vẫn là `http://10.0.2.2/employee-api/`. Các thay đổi Gradle/IDE/ApiClient xuất hiện trong Git status đã có trước phiên này. Không commit, push hoặc tạo PR.

## Kiểm tra đã chạy

- `assembleDebug`: đạt; APK: `app/build/outputs/apk/debug/app-debug.apk`.
- `testDebugUnitTest`: 15/15 đạt, gồm 14 test mới và 1 test mẫu có sẵn.
- Pixel_7, Android 16: 5/5 test mới dùng transport giả đạt; smoke test API thật đạt. Test mẫu có sẵn cũng đạt.
- PHP syntax: đạt. PHP/MySQL: 50/50 kiểm tra đạt, gồm tên trùng và tiếng Việt, thứ tự toàn bộ 3 trang, tìm/lọc kết hợp, trang cuối, kết quả rỗng, giới hạn đầu vào, tham số sai và lỗi truy vấn.
- Android lint: đạt, 0 lỗi; 110 cảnh báo toàn dự án, không có cảnh báo trong các file chức năng vừa sửa. Các cảnh báo toàn dự án nằm ngoài phạm vi Task 16–18 được giữ nguyên.
- API Laragon thật trả success và pagination hợp lệ; hiện có **4 nhân viên**.

Lệnh chạy lại trên máy hiện tại (PowerShell):

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
.\gradlew.bat :app:connectedDebugAndroidTest
.\gradlew.bat :app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.example.employee_management_app.StaffListLocalBackendTest' '-Pandroid.testInstrumentationRunnerArguments.staffLiveApi=true'
& 'C:\laragon\bin\php\php-8.1.10-Win32-vs16-x64\php.exe' tests/employee-list-api-test.php 'C:\laragon\www\employee-api\db.php'
```

Smoke test API thật chỉ bật khi có `staffLiveApi=true`, để test thường không phụ thuộc máy chủ cục bộ.

## Kiểm thử thủ công trên Pixel 7

Chuẩn bị: Laragon chạy Apache/MySQL; chọn Pixel_7 trong Android Studio, Run bản debug, đăng nhập tài khoản có quyền quản lý nhân viên, mở **Quản lý nhân viên**. Giữ URL Backend hiện tại.

1. **Task 16:** chọn HR/IT, kiểm tra chỉ còn nhân viên của phòng; gõ mã/tên trong phòng đó; chọn “Tất cả phòng ban” để tìm trên mọi phòng. Thử từ khóa không tồn tại; phải báo không tìm thấy. Chọn phòng liên tục: kết quả cuối phải khớp lựa chọn cuối.
2. **Task 17:** với bộ dữ liệu kiểm thử có hơn 20 kết quả, bấm Trang sau/Trang trước; kiểm tra tổng trang, ID không bị lặp giữa các trang, nút đầu/cuối bị khóa. Ở trang 2, đổi từ khóa hoặc phòng ban: phải về trang 1, giữ điều kiện còn lại. Xoay màn hình hoặc mở hồ sơ rồi quay lại: giữ trang và lựa chọn. CSDL thật hiện chỉ có 4 người nên chỉ có 1 trang; dùng test tự động 45/47 bản ghi để kiểm tra nhiều trang, không cần thêm dữ liệu vào CSDL thật.
3. **Task 18:** thử Tên A–Z/Z–A và Mã A–Z/Z–A; đối chiếu họ tên đầy đủ hoặc username. Giữ một phòng và từ khóa khi đổi cách sắp xếp; chỉ thứ tự thay đổi, trang về 1. Với dữ liệu nhiều trang, đối chiếu thứ tự nối tiếp giữa các trang.
4. **Lỗi/thao tác nhanh:** tạm tắt mạng máy ảo, bấm Tìm kiếm, kiểm tra thông báo lỗi; bật lại rồi bấm Tìm kiếm để tải lại với bộ lọc cũ. Lỗi tải phòng ban có nút Tải lại phòng ban. Gõ/xóa liên tục, đổi phòng và sort trong khi tải: danh sách cuối phải khớp điều kiện cuối.

## Giới hạn và lỗi còn lại

Chưa phát hiện lỗi chức năng Task 16–18 trong các ca đã chạy. Chưa kiểm thử điện thoại vật lý, CSDL quy mô lớn, font cực lớn hoặc dữ liệu nhân viên bị nhiều người sửa đồng thời. Phân trang vẫn dùng LIMIT/OFFSET theo Backend hiện tại; nếu bản ghi thay đổi giữa hai request, vị trí có thể dịch chuyển (không phải ảnh chụp cố định của CSDL). Dữ liệu nhiều trang trên Pixel được kiểm thử bằng transport giả; SQL thật được kiểm thử riêng bằng bảng tạm MySQL.

Backend phải dùng bản employees.php mới để Android nhận metadata phân trang. Nếu Backend cũ trả thiếu metadata, màn hình báo lỗi rõ ràng.

Kết quả lần chạy cuối trên Pixel 7: `build/task16-18/pixel7-tests.txt` — **OK (7 tests)**. APK cuối đã cài trên Pixel 7. Ảnh đã kiểm tra: `build/task16-18/staff-live-all.png`, `build/task16-18/staff-live-filtered.png`.
