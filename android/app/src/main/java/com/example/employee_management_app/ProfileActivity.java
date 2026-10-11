package com.example.employee_management_app;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.AdapterView;
import android.widget.DatePicker;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.bumptech.glide.Glide;
import com.google.gson.Gson;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

// Man hinh sua ho so ca nhan.
// Nhan vien: chi sua duoc ho ten + anh. Mail/SDT bi khoa (server giu nguyen).
// Admin (mo kem EMPLOYEE_ID): sua duoc tat ca.
public class ProfileActivity extends AppCompatActivity {
    private static final int PICK_IMAGE = 101;

    private ImageView ivAvatar;
    private EditText etName, etEmail, etPhone;
    private int targetId, myId, currentStatus = 1;
    private boolean isAdminMode, suppressLockEvent;
    private androidx.appcompat.widget.SwitchCompat swLock;
    private TextView tvLockStatus;
    private Button btnSave, btnAvatar;
    private TextView profileMessage;
    private EmployeeDetailResponse.Detail loadedProfile;
    private Call<EmployeeDetailResponse> profileCall;
    private Call<ApiResponse> saveCall;
    private boolean loading, saving;
    private EditText etHireDate;
    private Spinner spDepartment, spPosition;
    private boolean departmentsLoaded, positionsLoaded;
    private final List<DepartmentListResponse.Item> departmentChoices = new ArrayList<>();
    private final List<PositionListResponse.Item> positionChoices = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);
        findViewById(R.id.btnBackProfile).setOnClickListener(v -> finish());
        View root = findViewById(R.id.profileRoot);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            Insets safe = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout() | WindowInsetsCompat.Type.ime());
            view.setPadding(safe.left, safe.top, safe.right, safe.bottom);
            return insets;
        });
        ViewCompat.requestApplyInsets(root);

        ivAvatar = findViewById(R.id.ivAvatar);
        etName = findViewById(R.id.etProfileName);
        etEmail = findViewById(R.id.etProfileEmail);
        etPhone = findViewById(R.id.etProfilePhone);
        etHireDate = findViewById(R.id.etProfileHireDate);
        spDepartment = findViewById(R.id.spProfileDepartment);
        spPosition = findViewById(R.id.spProfilePosition);
        btnAvatar = findViewById(R.id.btnPickAvatar);
        btnSave = findViewById(R.id.btnSaveProfile);
        profileMessage = findViewById(R.id.tvProfileMessage);
        findViewById(R.id.btnRetryProfile).setOnClickListener(v -> loadProfile());
        swLock = findViewById(R.id.swLockAccount);
        tvLockStatus = findViewById(R.id.tvLockStatus);
        swLock.setOnCheckedChangeListener((v, isChecked) -> {
            if (suppressLockEvent) return;
            setLock(isChecked);
        });

        myId = TokenManager.getInstance(this).getUserId();
        String role = TokenManager.getInstance(this).getRole();
        boolean isAdmin = "admin".equalsIgnoreCase(role) || "manager".equalsIgnoreCase(role);

        // Admin mo tu danh sach NV kem EMPLOYEE_ID -> sua duoc het. Con lai la tu sua.
        if (getIntent().hasExtra("EMPLOYEE_ID") && isAdmin) {
            targetId = getIntent().getIntExtra("EMPLOYEE_ID", myId);
            isAdminMode = true;
            setTitle("Sửa hồ sơ nhân viên");
        } else {
            targetId = myId;
            isAdminMode = false;
            setTitle("Hồ sơ của tôi");
        }
        etEmail.setEnabled(isAdminMode);
        etPhone.setEnabled(isAdminMode);
        // Cum khoa/mo chi hien khi admin (hoac sep) mo ho so nguoi khac.
        // Gat OFF -> status=0 (khoa), ON -> status=1 (mo) trong DB.
        // Dat SAU khi xac dinh isAdminMode o tren.
        findViewById(R.id.layoutLock).setVisibility(isAdminMode ? View.VISIBLE : View.GONE);
        ((TextView) findViewById(R.id.tvProfilePermissions)).setText(isAdminMode
                ? "* Trường bắt buộc. Số điện thoại có thể để trống."
                : "Bạn được sửa họ tên và ảnh đại diện. Liên hệ người quản lý để cập nhật thông tin khác.");
        ((TextView) findViewById(R.id.tvProfileHireDate)).setText("Ngày vào làm: Chưa có dữ liệu");
        etHireDate.setVisibility(isAdminMode ? View.VISIBLE : View.GONE);
        spDepartment.setVisibility(isAdminMode ? View.VISIBLE : View.GONE);
        spPosition.setVisibility(isAdminMode ? View.VISIBLE : View.GONE);
        findViewById(R.id.btnRetryProfileLookups).setOnClickListener(v -> loadLookups());
        if (isAdminMode) loadLookups();
        etHireDate.setOnClickListener(v -> {
            java.util.Calendar calendar = java.util.Calendar.getInstance();
            String current = etHireDate.getText().toString();
            if (current.matches("\\d{4}-\\d{2}-\\d{2}")) {
                String[] fields = current.split("-");
                calendar.set(Integer.parseInt(fields[0]), Integer.parseInt(fields[1])-1, Integer.parseInt(fields[2]));
            }
            android.app.DatePickerDialog picker = new android.app.DatePickerDialog(this,
                    (d, year, month, day) -> etHireDate.setText(String.format(java.util.Locale.ROOT,
                            "%04d-%02d-%02d", year, month+1, day)),
                    calendar.get(java.util.Calendar.YEAR), calendar.get(java.util.Calendar.MONTH), calendar.get(java.util.Calendar.DAY_OF_MONTH));
            picker.getDatePicker().setMaxDate(System.currentTimeMillis());
            picker.show();
        });
        etHireDate.setFocusable(false);
        loadProfile();
        btnAvatar.setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_GET_CONTENT);
            i.setType("image/*");
            startActivityForResult(Intent.createChooser(i, "Chọn ảnh"), PICK_IMAGE);
        });
        btnSave.setOnClickListener(v -> saveProfile());
    }

    // Gat cong tac: server tu chan theo quyen (admin moi phong / sep cung phong)
    private void setLock(boolean enable) {
        int newStatus = enable ? 1 : 0;
        EmployeeApi api = ApiClient.getClient(this).create(EmployeeApi.class);
        api.setStatus(new SetStatusRequest(targetId, myId, newStatus)).enqueue(new Callback<ApiResponse>() {
            @Override
            public void onResponse(Call<ApiResponse> c, Response<ApiResponse> r) {
                if (r.isSuccessful() && r.body() != null && r.body().isSuccess()) {
                    Toast.makeText(ProfileActivity.this,
                            newStatus == 0 ? "Đã khóa tài khoản!" : "Đã mở khóa!",
                            Toast.LENGTH_SHORT).show();
                    loadProfile(); // tai lai de dong bo trang thai that tu server
                } else {
                    revertLock(enable);
                    Toast.makeText(ProfileActivity.this, "Không có quyền (sếp chỉ khóa NV phòng mình)", Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(Call<ApiResponse> c, Throwable t) {
                revertLock(enable);
                Toast.makeText(ProfileActivity.this, "Lỗi kết nối: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadProfile() {
        if (loading || saving) return;
        if (targetId <= 0) {
            showProfileError("Không xác định được nhân viên. Vui lòng đăng nhập lại.");
            updateControls();
            return;
        }
        loading = true;
        showMessage("Đang tải hồ sơ…");
        findViewById(R.id.btnRetryProfile).setVisibility(View.GONE);
        updateControls();
        EmployeeApi api = ApiClient.getClient(this).create(EmployeeApi.class);
        profileCall = api.getDetail(targetId);
        profileCall.enqueue(new Callback<EmployeeDetailResponse>() {
            @Override
            public void onResponse(Call<EmployeeDetailResponse> c, Response<EmployeeDetailResponse> r) {
                if (isFinishing() || isDestroyed() || c.isCanceled()) return;
                loading = false;
                if (r.isSuccessful() && r.body() != null && r.body().isSuccess()
                        && r.body().getData() != null && r.body().getData().getId() == targetId) {
                    EmployeeDetailResponse.Detail d = r.body().getData();
                    loadedProfile = d;
                    etName.setText(ProfileValidation.text(d.getFullName()));
                    etEmail.setText(ProfileValidation.text(d.getEmail()));
                    etPhone.setText(ProfileValidation.text(d.getPhone()));
                    ((TextView) findViewById(R.id.tvProfileCode)).setText(
                            "Mã NV / Tên đăng nhập: " + ProfileValidation.display(d.getUsername()));
                    ((TextView) findViewById(R.id.tvProfileId)).setText("ID nhân viên: " + d.getId());
                    ((TextView) findViewById(R.id.tvProfileDepartment)).setText(ProfileValidation.display(d.getDeptName()));
                    ((TextView) findViewById(R.id.tvProfilePosition)).setText(ProfileValidation.display(d.getPositionName()));
                    ((TextView) findViewById(R.id.tvProfileCreatedAt)).setText(
                            "Ngày tạo tài khoản: " + ProfileValidation.display(d.getCreatedAt()));
                    ((TextView) findViewById(R.id.tvProfileHireDate)).setText(
                            "Ngày vào làm: " + ProfileValidation.display(d.getHireDate()));
                    etHireDate.setText(ProfileValidation.text(d.getHireDate()));
                    selectLookupValues();
                    currentStatus = d.getStatus();
                    ((TextView) findViewById(R.id.tvProfileStatus)).setText("Trạng thái tài khoản: "
                            + (currentStatus == 1 ? "Đang hoạt động" : currentStatus == 0 ? "Đã khóa" : "Chưa xác định"));
                    suppressLockEvent = true;
                    swLock.setChecked(currentStatus == 1);
                    suppressLockEvent = false;
                    tvLockStatus.setText(currentStatus == 1
                            ? "Đang hoạt động - gạt TẮT để khóa"
                            : "ĐÃ BỊ KHÓA - gạt BẬT để mở lại");
                    if (d.getAvatarUrl() != null && !d.getAvatarUrl().isEmpty()) {
                        Glide.with(ProfileActivity.this)
                                .load(ApiClient.baseUrl() + d.getAvatarUrl())
                                .placeholder(R.mipmap.ic_launcher)
                                .error(R.mipmap.ic_launcher)
                                .into(ivAvatar);
                    } else {
                        Glide.with(ProfileActivity.this).clear(ivAvatar);
                        ivAvatar.setImageResource(R.mipmap.ic_launcher);
                    }
                    showMessage(null);
                } else {
                    showProfileError(apiError(r, "Không tải được hồ sơ"));
                }
                updateControls();
            }

            @Override
            public void onFailure(Call<EmployeeDetailResponse> c, Throwable t) {
                if (isFinishing() || isDestroyed() || c.isCanceled()) return;
                loading = false;
                showProfileError("Không tải được hồ sơ. Kiểm tra kết nối và thử lại.");
                updateControls();
            }
        });
    }

    private void saveProfile() {
        if (loadedProfile == null || loading || saving) return;
        String name = etName.getText().toString().trim();
        String email = etEmail.getText().toString().trim();
        String phone = etPhone.getText().toString().trim();
        boolean emailChanged = isAdminMode && ProfileValidation.changed(email, loadedProfile.getEmail());
        boolean phoneChanged = isAdminMode && ProfileValidation.changed(phone, loadedProfile.getPhone());
        etName.setError(ProfileValidation.nameError(name));
        etEmail.setError(emailChanged ? ProfileValidation.emailError(email) : null);
        etPhone.setError(phoneChanged ? ProfileValidation.phoneError(phone) : null);
        String date = isAdminMode ? etHireDate.getText().toString().trim() : null;
        if (isAdminMode) etHireDate.setError(ProfileValidation.hireDateError(date));
        if (etName.getError() != null || etEmail.getError() != null || etPhone.getError() != null
                || (isAdminMode && etHireDate.getError() != null)) {
            EditText invalid = etName.getError() != null ? etName : etEmail.getError() != null ? etEmail
                    : etPhone.getError() != null ? etPhone : etHireDate;
            invalid.requestFocus();
            showMessage("Vui lòng kiểm tra các trường được đánh dấu.");
            return;
        }
        if (isAdminMode && (!departmentsLoaded || !positionsLoaded)) {
            showMessage("Cần tải phòng ban và chức vụ trước khi lưu. Bấm Tải lại nếu gặp lỗi.");
            return;
        }
        saving = true;
        showMessage("Đang lưu hồ sơ…");
        updateControls();
        EmployeeApi api = ApiClient.getClient(this).create(EmployeeApi.class);
        // Gson omits nulls: only edited contact fields are sent, preserving legacy data.
        Integer departmentId = isAdminMode ? departmentChoices.get(spDepartment.getSelectedItemPosition()).getId() : null;
        Integer positionId = isAdminMode ? positionChoices.get(spPosition.getSelectedItemPosition()).getId() : null;
        boolean dateChanged = isAdminMode && ProfileValidation.changed(date, loadedProfile.getHireDate());
        UpdateEmployeeRequest req = new UpdateEmployeeRequest(targetId, myId, name,
                emailChanged ? email : null, phoneChanged ? phone : null,
                isAdminMode && departmentId != loadedProfile.getDepartmentId() ? departmentId : null,
                isAdminMode && positionId != loadedProfile.getPositionId() ? positionId : null,
                dateChanged ? date : null);
        saveCall = api.updateEmployee(req);
        saveCall.enqueue(new Callback<ApiResponse>() {
            @Override
            public void onResponse(Call<ApiResponse> c, Response<ApiResponse> r) {
                if (isFinishing() || isDestroyed() || c.isCanceled()) return;
                saving = false;
                if (r.isSuccessful() && r.body() != null && r.body().isSuccess()) {
                    Toast.makeText(ProfileActivity.this, "Đã lưu hồ sơ thành công!", Toast.LENGTH_SHORT).show();
                    setResult(RESULT_OK);
                    finish();
                } else {
                    showMessage(r.body() != null && r.body().getMessage() != null
                            ? r.body().getMessage() : apiError(r, "Lưu hồ sơ thất bại"));
                }
                updateControls();
            }

            @Override
            public void onFailure(Call<ApiResponse> c, Throwable t) {
                if (isFinishing() || isDestroyed() || c.isCanceled()) return;
                saving = false;
                showMessage("Chưa xác nhận được kết quả lưu do lỗi kết nối. Mở lại hồ sơ để kiểm tra trước khi thử lại.");
                updateControls();
            }
        });
    }

    private void loadLookups() {
        if (!isAdminMode) return;
        departmentsLoaded = false; positionsLoaded = false;
        findViewById(R.id.btnRetryProfileLookups).setVisibility(View.GONE);
        ((TextView)findViewById(R.id.tvProfileLookupMessage)).setText("Đang tải phòng ban và chức vụ…");
        findViewById(R.id.tvProfileLookupMessage).setVisibility(View.VISIBLE);
        updateControls();
        EmployeeApi api = ApiClient.getClient(this).create(EmployeeApi.class);
        api.getDepartments().enqueue(new Callback<DepartmentListResponse>() {
            @Override public void onResponse(Call<DepartmentListResponse> c, Response<DepartmentListResponse> r) {
                if (isFinishing()) return;
                if (!r.isSuccessful() || r.body() == null || !r.body().isSuccess() || r.body().getData() == null) {
                    lookupError(); return;
                }
                departmentChoices.clear();
                departmentChoices.add(new DepartmentListResponse.Item(0, "Không chỉ định"));
                departmentChoices.addAll(r.body().getData());
                ArrayAdapter<DepartmentListResponse.Item> adapter = new ArrayAdapter<>(ProfileActivity.this,
                        android.R.layout.simple_spinner_item, departmentChoices);
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                spDepartment.setAdapter(adapter);
                departmentsLoaded = true;
                selectLookupValues(); lookupReady();
            }
            @Override public void onFailure(Call<DepartmentListResponse> c, Throwable e) { if (!isFinishing()) lookupError(); }
        });
        api.getPositions().enqueue(new Callback<PositionListResponse>() {
            @Override public void onResponse(Call<PositionListResponse> c, Response<PositionListResponse> r) {
                if (isFinishing()) return;
                if (!r.isSuccessful() || r.body() == null || !r.body().isSuccess() || r.body().getData() == null) {
                    lookupError(); return;
                }
                positionChoices.clear();
                positionChoices.add(new PositionListResponse.Item(0, "Không chỉ định"));
                positionChoices.addAll(r.body().getData());
                ArrayAdapter<PositionListResponse.Item> adapter = new ArrayAdapter<>(ProfileActivity.this,
                        android.R.layout.simple_spinner_item, positionChoices);
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                spPosition.setAdapter(adapter);
                positionsLoaded = true;
                selectLookupValues(); lookupReady();
            }
            @Override public void onFailure(Call<PositionListResponse> c, Throwable e) { if (!isFinishing()) lookupError(); }
        });
    }
    private void selectLookupValues() {
        if (loadedProfile == null) return;
        if (departmentsLoaded) for (int i=0; i<departmentChoices.size(); i++) {
            if (departmentChoices.get(i).getId() == loadedProfile.getDepartmentId()) { spDepartment.setSelection(i); break; }
        }
        if (positionsLoaded) for (int i=0; i<positionChoices.size(); i++) {
            if (positionChoices.get(i).getId() == loadedProfile.getPositionId()) { spPosition.setSelection(i); break; }
        }
    }
    private void lookupReady() {
        if (departmentsLoaded && positionsLoaded) {
            findViewById(R.id.tvProfileLookupMessage).setVisibility(View.GONE);
            findViewById(R.id.btnRetryProfileLookups).setVisibility(View.GONE);
        }
        updateControls();
    }
    private void lookupError() {
        ((TextView)findViewById(R.id.tvProfileLookupMessage)).setText("Không tải được phòng ban / chức vụ. Bấm Tải lại.");
        findViewById(R.id.btnRetryProfileLookups).setVisibility(View.VISIBLE);
        updateControls();
    }

    private void updateControls() {
        boolean editable = loadedProfile != null && !loading && !saving;
        etName.setEnabled(editable);
        etEmail.setEnabled(editable && isAdminMode);
        etPhone.setEnabled(editable && isAdminMode);
        btnSave.setEnabled(editable);
        btnAvatar.setEnabled(editable);
        swLock.setEnabled(editable);
        etHireDate.setEnabled(editable && isAdminMode);
        spDepartment.setEnabled(editable && isAdminMode && departmentsLoaded);
        spPosition.setEnabled(editable && isAdminMode && positionsLoaded);
        findViewById(R.id.progressProfile).setVisibility(loading || saving ? View.VISIBLE : View.GONE);
    }

    private void showMessage(String message) {
        profileMessage.setText(message);
        profileMessage.setVisibility(message == null ? View.GONE : View.VISIBLE);
    }

    private void showProfileError(String message) {
        loadedProfile = null;
        showMessage(message);
        findViewById(R.id.btnRetryProfile).setVisibility(targetId > 0 ? View.VISIBLE : View.GONE);
    }

    private String apiError(Response<?> response, String fallback) {
        if (response.errorBody() != null) {
            try {
                ApiResponse error = new Gson().fromJson(response.errorBody().string(), ApiResponse.class);
                if (error != null && error.getMessage() != null && !error.getMessage().trim().isEmpty()) {
                    return error.getMessage();
                }
            } catch (Exception ignored) { /* Non-JSON HTTP errors still get a readable message. */ }
        }
        return fallback + (response.isSuccessful() ? ". Dữ liệu trả về không hợp lệ." : " (HTTP " + response.code() + ")");
    }

    @Override protected void onDestroy() {
        if (profileCall != null) profileCall.cancel();
        if (saveCall != null) saveCall.cancel();
        super.onDestroy();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_IMAGE && resultCode == RESULT_OK && data != null && data.getData() != null) {
            uploadAvatar(data.getData());
        }
    }

    // Gat that bai (mang loi / khong quyen) -> gat cong tac ve lai, tranh lech voi DB
    private void revertLock(boolean triedEnable) {
        suppressLockEvent = true;
        swLock.setChecked(!triedEnable);
        suppressLockEvent = false;
    }
    private void uploadAvatar(Uri uri) {
        String mime = getContentResolver().getType(uri);
        if (!"image/jpeg".equals(mime) && !"image/png".equals(mime) && !"image/webp".equals(mime)) {
            Toast.makeText(this, "Chỉ nhận JPG, PNG, WebP", Toast.LENGTH_LONG).show();
            return;
        }
        if (loading || saving || loadedProfile == null) return;
        String ext = "image/png".equals(mime) ? "png" : "image/webp".equals(mime) ? "webp" : "jpg";
        File temporaryFile = new File(getCacheDir(), "avatar_upload." + ext);
        try (InputStream input = getContentResolver().openInputStream(uri);
             OutputStream output = new FileOutputStream(temporaryFile)) {
            if (input == null) throw new IllegalStateException("Không đọc được ảnh");
            byte[] bytes = new byte[8192]; int n; long total = 0;
            while ((n = input.read(bytes)) != -1) {
                total += n;
                if (total > 2 * 1024 * 1024) throw new IllegalArgumentException("Ảnh tối đa 2MB");
                output.write(bytes, 0, n);
            }
            if (total == 0) throw new IllegalArgumentException("Ảnh rỗng");
            btnAvatar.setEnabled(false);
            RequestBody body = RequestBody.create(MediaType.parse(mime), temporaryFile);
            MultipartBody.Part part = MultipartBody.Part.createFormData("avatar", temporaryFile.getName(), body);
            RequestBody employeeId = RequestBody.create(MediaType.parse("text/plain"), String.valueOf(targetId));
            RequestBody requesterId = RequestBody.create(MediaType.parse("text/plain"), String.valueOf(myId));
            ApiClient.getClient(this).create(EmployeeApi.class).uploadAvatar(employeeId, requesterId, part)
                    .enqueue(new Callback<AvatarResponse>() {
                        @Override public void onResponse(Call<AvatarResponse> call, Response<AvatarResponse> response) {
                            btnAvatar.setEnabled(true);
                            temporaryFile.delete();
                            if (isFinishing() || isDestroyed()) return;
                            if (response.isSuccessful() && response.body() != null && response.body().isSuccess()
                                    && response.body().getData() != null) {
                                String newUrl = response.body().getData().getAvatarUrl();
                                Glide.with(ProfileActivity.this).load(ApiClient.baseUrl() + newUrl).into(ivAvatar);
                                Toast.makeText(ProfileActivity.this, "Đã cập nhật ảnh", Toast.LENGTH_SHORT).show();
                            } else Toast.makeText(ProfileActivity.this, "Không tải được ảnh (HTTP " + response.code() + ")", Toast.LENGTH_SHORT).show();
                        }
                        @Override public void onFailure(Call<AvatarResponse> call, Throwable t) {
                            btnAvatar.setEnabled(true); temporaryFile.delete();
                            if (!isFinishing()) Toast.makeText(ProfileActivity.this, "Lỗi kết nối. Kiểm tra ảnh trong hồ sơ trước khi thử lại.", Toast.LENGTH_LONG).show();
                        }
                    });
        } catch (Exception e) {
            temporaryFile.delete();
            Toast.makeText(this, "Không đọc được ảnh: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}
