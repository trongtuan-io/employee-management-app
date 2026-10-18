package com.example.employee_management_app;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ChangePasswordActivity extends AppCompatActivity {
    private EditText etOldPassword, etNewPassword, etConfirmPassword;
    private Button btnChangePassword;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_change_password);

        etOldPassword = findViewById(R.id.etOldPassword);
        etNewPassword = findViewById(R.id.etNewPassword);
        etConfirmPassword = findViewById(R.id.etConfirmPassword);
        btnChangePassword = findViewById(R.id.btnChangePassword);

        btnChangePassword.setOnClickListener(v -> changePassword());
    }

    private void changePassword() {
        String oldPass = etOldPassword.getText().toString().trim();
        String newPass = etNewPassword.getText().toString().trim();
        String confirmPass = etConfirmPassword.getText().toString().trim();

        if (oldPass.isEmpty() || newPass.isEmpty() || confirmPass.isEmpty()) {
            Toast.makeText(this, "Vui lòng nhập đầy đủ thông tin", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!newPass.equals(confirmPass)) {
            Toast.makeText(this, "Mật khẩu xác nhận không khớp!", Toast.LENGTH_SHORT).show();
            return;
        }

        int userId = TokenManager.getInstance(this).getUserId();
        if (userId == -1) {
            Toast.makeText(this, "Phiên đăng nhập hết hạn, vui lòng đăng nhập lại", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        AuthApi authApi = ApiClient.getClient(this).create(AuthApi.class);
        authApi.changePassword(new ChangePasswordRequest(userId, oldPass, newPass)).enqueue(new Callback<ApiResponse>() {
            @Override
            public void onResponse(Call<ApiResponse> call, Response<ApiResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    ApiResponse apiResponse = response.body();
                    if (apiResponse.isSuccess()) {
                        Toast.makeText(ChangePasswordActivity.this, "Đổi mật khẩu thành công!", Toast.LENGTH_SHORT).show();
                        finish(); // Đóng màn hình, quay về màn hình trước
                    } else {
                        Toast.makeText(ChangePasswordActivity.this, apiResponse.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                } else {
                    // Đọc message thân thiện từ JSON {"success":false,"error":"..."} thay vì hiện raw JSON
                    String msg = "Lỗi từ máy chủ: HTTP " + response.code();
                    try {
                        String errorBody = response.errorBody().string();
                        try {
                            org.json.JSONObject obj = new org.json.JSONObject(errorBody);
                            if (obj.has("error")) msg = obj.getString("error");
                            else if (obj.has("message")) msg = obj.getString("message");
                        } catch (Exception ignored) { msg = errorBody; }
                    } catch (Exception ignored) { }
                    Toast.makeText(ChangePasswordActivity.this, msg, Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(Call<ApiResponse> call, Throwable t) {
                Toast.makeText(ChangePasswordActivity.this, "Lỗi kết nối: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }
}
