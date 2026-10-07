package com.example.employee_management_app;

import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

// Man hinh ADMIN tao tai khoan cho nhan vien.
// Chi role admin/manager duoc mo (giong AdminActivity). Goi AuthApi.register().
public class CreateAccountActivity extends AppCompatActivity {

    private EditText etUsername, etPassword, etFullName, etPhone, etEmail, etDeptId, etPositionId;
    private Spinner spRole;

    // Map spinner -> role_id backend.
    // Mac dinh pho bien: 1 = admin, 2 = manager, 3 = staff.
    // Neu backend cua ban khac, doi trong switch o doCreate() cho khop.

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
        etDeptId = findViewById(R.id.etNewDeptId);
        etPositionId = findViewById(R.id.etNewPositionId);
        spRole = findViewById(R.id.spNewRole);
        Button btnCreate = findViewById(R.id.btnDoCreateAccount);

        String[] roleNames = {"Nhân viên (staff)", "Quản lý (manager)", "Quản trị (admin)"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, roleNames);
        spRole.setAdapter(adapter);
        spRole.setSelection(0);

        btnCreate.setOnClickListener(v -> doCreate());
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

        Integer deptId = parseNullableInt(etDeptId.getText().toString().trim());
        Integer posId = parseNullableInt(etPositionId.getText().toString().trim());

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

    private Integer parseNullableInt(String s) {
        if (s == null || s.isEmpty()) return null;
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
