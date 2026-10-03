package com.example.employee_management_app;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class LoginActivity extends AppCompatActivity {
    private EditText etUsername;
    private EditText etPassword;
    private Button btnLogin;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        etUsername = findViewById(R.id.etUsername);
        etPassword = findViewById(R.id.etPassword);
        btnLogin = findViewById(R.id.btnLogin);

        btnLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String user = etUsername.getText().toString().trim();
                String pass = etPassword.getText().toString().trim();

                if (user.isEmpty() || pass.isEmpty()) {
                    Toast.makeText(LoginActivity.this, "Vui lòng nhập Username và Password", Toast.LENGTH_SHORT).show();
                    return;
                }

                AuthApi authApi = ApiClient.getClient(LoginActivity.this).create(AuthApi.class);
                authApi.login(new LoginRequest(user, pass)).enqueue(new Callback<LoginResponse>() {
                    @Override
                    public void onResponse(Call<LoginResponse> call, Response<LoginResponse> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            LoginResponse loginResponse = response.body();
                            if (loginResponse.isSuccess()) {
                                Toast.makeText(LoginActivity.this, "Đăng nhập thành công!", Toast.LENGTH_SHORT).show();
                                // Go to MainActivity or Employee List screen
                                Intent intent = new Intent(LoginActivity.this, MainActivity.class);
                                
                                // Lưu JWT Token vào điện thoại
                                if (loginResponse.getToken() != null) {
                                    TokenManager.getInstance(LoginActivity.this).saveToken(loginResponse.getToken());
                                }
                                // Lưu id user để dùng cho đổi mật khẩu, xem hồ sơ...
                                if (loginResponse.getData() != null) {
                                    TokenManager.getInstance(LoginActivity.this).saveUserId(loginResponse.getData().getId());
                                }
                                
                                // Truyền thông tin sang trang chủ
                                String loggedInUser = user;
                                String role = "Unknown";
                                
                                if (loginResponse.getData() != null) {
                                    if (loginResponse.getData().getUsername() != null) {
                                        loggedInUser = loginResponse.getData().getUsername();
                                    }
                                    if (loginResponse.getData().getRole() != null) {
                                        role = loginResponse.getData().getRole();
                                    }
                                }
                                intent.putExtra("USERNAME", loggedInUser);
                                intent.putExtra("ROLE", role);
                                
                                // Kiểm tra Role trước khi vào Dashboard (Ví dụ)
                                if (role.equalsIgnoreCase("admin")) {
                                    Toast.makeText(LoginActivity.this, "Chào mừng Quản trị viên!", Toast.LENGTH_SHORT).show();
                                } else {
                                    Toast.makeText(LoginActivity.this, "Chào mừng Nhân viên!", Toast.LENGTH_SHORT).show();
                                }
                                
                                startActivity(intent);
                                finish();
                            } else {
                                Toast.makeText(LoginActivity.this, loginResponse.getMessage(), Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            Toast.makeText(LoginActivity.this, "Đăng nhập thất bại!", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<LoginResponse> call, Throwable t) {
                        Toast.makeText(LoginActivity.this, "Lỗi kết nối: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }
}
