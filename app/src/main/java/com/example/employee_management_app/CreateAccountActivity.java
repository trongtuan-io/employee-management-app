package com.example.employee_management_app;

import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

// Man hinh ADMIN tao tai khoan cho nhan vien.
// Chi role admin/manager duoc mo (giong AdminActivity). Goi AuthApi.register().
// Phong ban va Chuc vu chon bang SPINNER lay tu server (khong nhap ID bang tay nua);
// doi phong ban thi danh sach chuc vu tu loc theo phong do (+ chuc vu dung chung).
// Server van ep quy tac: manager chi tao staff cho phong minh, chuc vu phai thuoc phong.
public class CreateAccountActivity extends AppCompatActivity {

    private EditText etUsername, etPassword, etFullName, etPhone, etEmail;
    private Spinner spRole, spDept, spPosition;
    private Button btnCreate;
    private final List<DepartmentListResponse.Item> deptChoices = new ArrayList<>();
    private final List<PositionListResponse.Item> positionChoices = new ArrayList<>();
    private Call<PositionListResponse> positionsCall;
    private int positionsForDept = -1;
    private boolean isManagerRole;
    private int myId;

    // Map spinner -> role_id backend: 1 = admin, 2 = manager, 3 = staff.

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_account);
        setTitle("Tạo tài khoản nhân viên");

        String role = TokenManager.getInstance(this).getRole();
        if (!role.equalsIgnoreCase("admin") && !role.equalsIgnoreCase("manager")) {
            Toast.makeText(this, "Chỉ admin mới được tạo tài khoản", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        isManagerRole = role.equalsIgnoreCase("manager");
        myId = TokenManager.getInstance(this).getUserId();

        etUsername = findViewById(R.id.etNewUsername);
        etPassword = findViewById(R.id.etNewPassword);
        etFullName = findViewById(R.id.etNewFullName);
        etPhone = findViewById(R.id.etNewPhone);
        etEmail = findViewById(R.id.etNewEmail);
        spDept = findViewById(R.id.spNewDept);
        spPosition = findViewById(R.id.spNewPosition);
        spRole = findViewById(R.id.spNewRole);
        btnCreate = findViewById(R.id.btnDoCreateAccount);

        // Manager chi duoc tao tai khoan staff (server cung ep vay) - chi hien dung lua chon do.
        String[] roleNames = isManagerRole
                ? new String[]{"Nhân viên (staff)"}
                : new String[]{"Nhân viên (staff)", "Quản lý (manager)", "Quản trị (admin)"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, roleNames);
        spRole.setAdapter(adapter);
        spRole.setSelection(0);

        spDept.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                if (pos >= 0 && pos < deptChoices.size()) {
                    loadPositions(deptChoices.get(pos).getId());
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        loadDepartments();
        if (isManagerRole) lockDepartmentToMine();
        btnCreate.setOnClickListener(v -> doCreate());
    }

    // Manager: khoa spinner Phong ve phong cua chinh minh (server cung ep nhu vay).
    private void lockDepartmentToMine() {
        if (myId <= 0) return;
        EmployeeApi api = ApiClient.getClient(this).create(EmployeeApi.class);
        api.getDetail(myId).enqueue(new Callback<EmployeeDetailResponse>() {
            @Override
            public void onResponse(Call<EmployeeDetailResponse> c, Response<EmployeeDetailResponse> r) {
                if (isFinishing() || isDestroyed()) return;
                if (r.isSuccessful() && r.body() != null && r.body().getData() != null) {
                    Integer myDept = r.body().getData().getDepartmentId();
                    if (myDept != null) {
                        for (int i = 0; i < deptChoices.size(); i++) {
                            if (deptChoices.get(i).getId() == myDept) {
                                spDept.setSelection(i);
                                break;
                            }
                        }
                        spDept.setEnabled(false);
                    }
                }
            }

            @Override
            public void onFailure(Call<EmployeeDetailResponse> c, Throwable t) { }
        });
    }

    private void loadDepartments() {
        EmployeeApi api = ApiClient.getClient(this).create(EmployeeApi.class);
        api.getDepartments().enqueue(new Callback<DepartmentListResponse>() {
            @Override
            public void onResponse(Call<DepartmentListResponse> c, Response<DepartmentListResponse> r) {
                if (r.isSuccessful() && r.body() != null && r.body().isSuccess() && r.body().getData() != null) {
                    deptChoices.clear();
                    deptChoices.add(new DepartmentListResponse.Item(0, "Không chỉ định"));
                    deptChoices.addAll(r.body().getData());
                    ArrayAdapter<DepartmentListResponse.Item> a = new ArrayAdapter<>(
                            CreateAccountActivity.this,
                            android.R.layout.simple_spinner_dropdown_item, deptChoices);
                    spDept.setAdapter(a);
                    loadPositions(0);
                } else {
                    Toast.makeText(CreateAccountActivity.this,
                            "Không tải được danh sách phòng ban", Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(Call<DepartmentListResponse> c, Throwable t) {
                Toast.makeText(CreateAccountActivity.this,
                        "Lỗi kết nối khi tải phòng ban: " + t.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    // deptId = 0 -> tat ca chuc vu; > 0 -> chuc vu cua phong do + chuc vu dung chung.
    // Huy call cu + bo qua response ve tre cua phong truoc (tranh spinner chua chuc vu sai phong).
    private void loadPositions(int deptId) {
        if (positionsCall != null) positionsCall.cancel();
        positionsForDept = deptId;
        positionChoices.clear();
        positionChoices.add(new PositionListResponse.Item(0, "Đang tải..."));
        ArrayAdapter<PositionListResponse.Item> loading = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_dropdown_item, positionChoices);
        spPosition.setAdapter(loading);
        spPosition.setEnabled(false);
        EmployeeApi api = ApiClient.getClient(this).create(EmployeeApi.class);
        positionsCall = deptId > 0 ? api.getPositions(deptId) : api.getPositions();
        positionsCall.enqueue(new Callback<PositionListResponse>() {
            @Override
            public void onResponse(Call<PositionListResponse> c, Response<PositionListResponse> r) {
                if (isFinishing() || isDestroyed() || deptId != positionsForDept) return; // response cu, bo qua
                spPosition.setEnabled(true);
                if (r.isSuccessful() && r.body() != null && r.body().isSuccess() && r.body().getData() != null) {
                    positionChoices.clear();
                    positionChoices.add(new PositionListResponse.Item(0, "Không chỉ định"));
                    positionChoices.addAll(r.body().getData());
                    ArrayAdapter<PositionListResponse.Item> a = new ArrayAdapter<>(
                            CreateAccountActivity.this,
                            android.R.layout.simple_spinner_dropdown_item, positionChoices);
                    spPosition.setAdapter(a);
                } else {
                    positionChoices.clear();
                    positionChoices.add(new PositionListResponse.Item(0, "Không tải được chức vụ"));
                    spPosition.setAdapter(new ArrayAdapter<>(CreateAccountActivity.this,
                            android.R.layout.simple_spinner_dropdown_item, positionChoices));
                }
            }

            @Override
            public void onFailure(Call<PositionListResponse> c, Throwable t) {
                if (isFinishing() || isDestroyed() || deptId != positionsForDept || c.isCanceled()) return;
                spPosition.setEnabled(true);
                positionChoices.clear();
                positionChoices.add(new PositionListResponse.Item(0, "Không tải được chức vụ"));
                spPosition.setAdapter(new ArrayAdapter<>(CreateAccountActivity.this,
                        android.R.layout.simple_spinner_dropdown_item, positionChoices));
                Toast.makeText(CreateAccountActivity.this,
                        "Lỗi kết nối khi tải chức vụ: " + t.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void doCreate() {
        String username = etUsername.getText().toString().trim();
        String password = etPassword.getText().toString(); // khong trim mat khau
        String fullName = etFullName.getText().toString().trim();
        String phone = etPhone.getText().toString().trim();
        String email = etEmail.getText().toString().trim();

        if (username.isEmpty() || password.isEmpty() || fullName.isEmpty()) {
            Toast.makeText(this, "Nhập đủ tên đăng nhập, mật khẩu, họ tên", Toast.LENGTH_SHORT).show();
            return;
        }
        if (password.length() < 6) {
            Toast.makeText(this, "Mật khẩu tối thiểu 6 ký tự", Toast.LENGTH_SHORT).show();
            return;
        }
        // Server bat buoc email hop le (truoc day app cho bo trong roi bao loi sai nguyen nhan).
        if (email.isEmpty() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            etEmail.setError("Email bắt buộc và phải đúng định dạng");
            etEmail.requestFocus();
            return;
        }

        // Spinner: 0 = staff(3), 1 = manager(2), 2 = admin(1)
        int roleId;
        switch (spRole.getSelectedItemPosition()) {
            case 1: roleId = 2; break;
            case 2: roleId = 1; break;
            default: roleId = 3; break;
        }

        Integer deptId = null;
        int deptPos = spDept.getSelectedItemPosition();
        if (deptPos >= 0 && deptPos < deptChoices.size() && deptChoices.get(deptPos).getId() > 0) {
            deptId = deptChoices.get(deptPos).getId();
        }
        Integer posId = null;
        int posPos = spPosition.getSelectedItemPosition();
        if (posPos >= 0 && posPos < positionChoices.size() && positionChoices.get(posPos).getId() > 0) {
            posId = positionChoices.get(posPos).getId();
        }

        RegisterRequest req = new RegisterRequest(username, password, fullName,
                phone.isEmpty() ? null : phone,
                email,
                roleId, deptId, posId);

        btnCreate.setEnabled(false); // tranh bam dup tao 2 tai khoan
        AuthApi api = ApiClient.getClient(this).create(AuthApi.class);
        api.register(req).enqueue(new Callback<ApiResponse>() {
            @Override
            public void onResponse(Call<ApiResponse> c, Response<ApiResponse> r) {
                btnCreate.setEnabled(true);
                if (r.isSuccessful() && r.body() != null && r.body().isSuccess()) {
                    Toast.makeText(CreateAccountActivity.this,
                            "Tạo tài khoản thành công!", Toast.LENGTH_SHORT).show();
                    finish();
                } else {
                    String msg = (r.body() != null && r.body().getMessage() != null)
                            ? r.body().getMessage() : ApiError.message(r, "Tạo tài khoản thất bại");
                    Toast.makeText(CreateAccountActivity.this, msg, Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(Call<ApiResponse> c, Throwable t) {
                btnCreate.setEnabled(true);
                Toast.makeText(CreateAccountActivity.this,
                        "Lỗi kết nối: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }
}
