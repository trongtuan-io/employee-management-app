# Hợp đồng dữ liệu HR ↔ BOD (cần thống nhất với thành viên phụ trách BOD)

**Đã thực hiện phía HR**: tạo đề xuất (`hr_proposals.php?action=create`), hiển thị (`action=list`), gửi bản nháp Staff HR để Manager HR duyệt nội bộ (`action=submit`), audit tạo/gửi đề xuất. **Chưa có API BOD và chưa có thao tác phê duyệt nào.**

Schema tạo bằng `database/migrations/012_hr_workflow.sql`:

- `hr_proposals(id, proposal_type, employee_id, proposed_by, before_data, proposed_data, reason, effective_date, status, version, reviewed_by, reviewed_at, review_note, applied_at, reservation_username, reservation_email, import_batch_id, created_at, updated_at)`.
- `proposal_type`: `recruitment`, `base_salary_change`, `promotion`, `transfer`, `termination`.
- `status` sử dụng phía HR: `draft`, `pending`. Trạng thái dự kiến phía BOD (chưa có mã xử lý): `approved`, `rejected`. BOD cần quy định các trạng thái cuối cùng và cơ chế hiệu lực trong tương lai.
- `before_data` là ảnh chụp dữ liệu gốc từ PHP/server; `proposed_data` là dữ liệu đề nghị đã kiểm tra. HR không thể ghi trực tiếp `reviewed_by`, `status=approved`, `applied_at`.
- `reservation_username`, `reservation_email` có UNIQUE để chống trùng tuyển dụng đang chờ. **BOD phải giải phóng hai cột này trong cùng transaction khi duyệt/từ chối**, sau khi kiểm tra username/email với `employees`.
- `hr_history` là bảng audit chung (actor, before/after, proposal_id). BOD bổ sung sự kiện `proposal.approved`/`proposal.rejected`/`proposal.applied` và thao tác phát sinh mà không ghi đè dữ liệu audit cũ.
- `hr_import_batches` dùng cho nhóm hồ sơ tuyển dụng pending và cập nhật liên hệ nhân viên hiện hữu. Không có file import hoặc mật khẩu trong database.

## Yêu cầu bắt buộc ở BOD (chưa implement trong gói)

1. Backend BOD xác thực role **và** phòng `BOD` **và** chức vụ được phép (Giám đốc) hoặc quyền ủy quyền còn hiệu lực, phạm vi chức năng. Không cấp quyền duyệt chỉ vì có `role=manager`, `department=BOD`.
2. Dùng transaction và `SELECT ... FOR UPDATE` khóa `hr_proposals`; chỉ duyệt từ `pending`, kiểm tra `version`/`before_data` khớp dữ liệu mới nhất. Không duyệt hai lần; không cho Admin kỹ thuật sửa thẳng.
3. Khi tuyển dụng được duyệt: kiểm tra `username/email` lần cuối và tạo tài khoản với mật khẩu sinh ngẫu nhiên/hash, **không tái sử dụng mật khẩu nhập trước khi duyệt**. Không kích hoạt trước ngày được duyệt/ngày hiệu lực.
4. Khi đổi lương, chức vụ, phòng, nghỉ việc: chỉ cập nhật `employees` sau phê duyệt. Chuyển phòng phải bảo đảm chức vụ thuộc phòng mới. Thao tác có ngày hiệu lực tương lai cần xử lý định kỳ an toàn (chưa có job trong gói HR).
5. Khi nghỉ việc có hiệu lực: `employees.employment_status='left'`, `termination_effective_date=...`, `status=0`, thu hồi `api_tokens` và không xóa `employees`, `attendances`, `salaries`.
6. Quy trình chốt lương và phê duyệt đơn của **Manager** thuộc module BOD/ACC. Các API cũ hiện có đường quyền Admin/ACC còn tồn tại; chưa được thay đổi trong bản vá HR để tránh xâm phạm module nhóm. **Không phát hành toàn hệ thống với phân quyền mới cho đến khi nhóm BOD/ACC chặn được đường duyệt cũ**.
7. Phòng ban: Admin còn có quyền kỹ thuật CRUD trong `api/departments.php`. Nhóm BOD cần định nghĩa cách ghi quyết định cơ cấu trước khi Admin thực thi.

## Phân quyền tài liệu

- `hr_documents.php`: nhân viên tự xem/tải giấy tờ của mình; HR Manager/Staff xem và kiểm tra cho nhân viên khác. Người làm HR không tự xác nhận giấy tờ của chính mình; Manager phòng khác, BOD, Admin kỹ thuật **không mặc định tải** CCCD/BH.
- Tài liệu lưu trên thư mục **ngoài webroot**, qua PHP token guard. File và bản ghi giữ lịch sử. Không chứa mật khẩu/mã OTP trong audit.

## Giới hạn còn lại

- Không có chức năng BOD duyệt/từ chối/áp dụng, không có giao diện BOD, không có chốt bảng lương theo quy trình mới, không có job xử lý ngày hiệu lực tương lai.
- Không tự động sửa các bảng/hàm khác của Kế toán, chấm công sinh trắc học, thông báo thời gian thực.
- `hr_import_batches` đã lưu thống kê batch; chưa có rollback batch sau khi đã cập nhật thông tin nhân viên thực tế. Hành động đảo dữ liệu cần chính sách riêng tránh xóa thay đổi hợp lệ về sau.
