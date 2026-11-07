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
    private final List<DepartmentListResponse.Item> deptChoices = new ArrayList<>();
    private final List<PositionListResponse.Item> positionChoices = new ArrayList<>();

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

        etUsername = findViewById(R.id.etNewUsername);
        etPassword = findViewById(R.id.etNewPassword);
        etFullName = findViewById(R.id.etNewFullName);
        etPhone = findViewById(R.id.etNewPhone);
        etEmail = findViewById(R.id.etNewEmail);
        spDept = findViewById(R.id.spNewDept);
        spPosition = findViewById(R.id.spNewPosition);
        spRole = findViewById(R.id.spNewRole);
        Button btnCreate = findViewById(R.id.btnDoCreateAccount);

        String[] roleNames = {"Nhân viên (staff)", "Quản lý (manager)", "Quản trị (admin)"};
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
        btnCreate.setOnClickListener(v -> doCreate());
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
    private void loadPositions(int deptId) {
        EmployeeApi api = ApiClient.getClient(this).create(EmployeeApi.class);
        Call<PositionListResponse> call = deptId > 0 ? api.getPositions(deptId) : api.getPositions();
        call.enqueue(new Callback<PositionListResponse>() {
            @Override
            public void onResponse(Call<PositionListResponse> c, Response<PositionListResponse> r) {
                if (r.isSuccessful() && r.body() != null && r.body().isSuccess() && r.body().getData() != null) {
                    positionChoices.clear();
                    positionChoices.add(new PositionListResponse.Item(0, "Không chỉ định"));
                    positionChoices.addAll(r.body().getData());
                    ArrayAdapter<PositionListResponse.Item> a = new ArrayAdapter<>(
                            CreateAccountActivity.this,
                            android.R.layout.simple_spinner_dropdown_item, positionChoices);
                    spPosition.setAdapter(a);
                }
            }

            @Override
            public void onFailure(Call<PositionListResponse> c, Throwable t) {
                Toast.makeText(CreateAccountActivity.this,
                        "Lỗi kết nối khi tải chức vụ: " + t.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void doCreate() {
        String username = etUsername.getText().toString().trim();
        String password = etPassword.getText().toString().trim();
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
                email.isEmpty() ? null : email,
                roleId, deptId, posId);

        AuthApi api = ApiClient.getClient(this).create(AuthApi.class);
        api.register(req).enqueue(new Callback<ApiResponse>() {
            @Override
            public void onResponse(Call<ApiResponse> c, Response<ApiResponse> r) {
                if (r.isSuccessful() && r.body() != null && r.body().isSuccess()) {
                    Toast.makeText(CreateAccountActivity.this,
                            "Tạo tài khoản thành công!", Toast.LENGTH_SHORT).show();
                    finish();
                } else {
                    String msg = (r.body() != null && r.body().getMessage() != null)
                            ? r.body().getMessage() : "Tạo thất bại (trùng username?)";
                    Toast.makeText(CreateAccountActivity.this, msg, Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(Call<ApiResponse> c, Throwable t) {
                Toast.makeText(CreateAccountActivity.this,
                        "Lỗi kết nối: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }
}
