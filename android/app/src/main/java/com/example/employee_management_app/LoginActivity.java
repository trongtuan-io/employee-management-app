package com.example.employee_management_app;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class LoginActivity extends AppCompatActivity {
    private EditText etUsername;
    private EditText etPassword;
    private Button btnLogin;
    private boolean passwordVisible;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Con phien dang nhap (token 7 ngay) thi vao thang Trang chu,
        // khoi phai go lai mat khau moi lan mo app. Token het han se bi
        // ApiClient bat 401 va dua ve lai man nay.
        TokenManager tm = TokenManager.getInstance(this);
        if (tm.getToken() != null && !tm.getToken().isEmpty() && tm.getUserId() > 0) {
            startActivity(new Intent(this, HomeActivity.class));
            finish();
            return;
        }

        setContentView(R.layout.activity_login);

        etUsername = findViewById(R.id.etUsername);
        etPassword = findViewById(R.id.etPassword);
        btnLogin = findViewById(R.id.btnLogin);

        // Icon con mat Hien/An mat khau: doi inputType + icon, giu nguyen vi tri con tro.
        ImageButton btnTogglePassword = findViewById(R.id.btnTogglePassword);
        btnTogglePassword.setOnClickListener(v -> {
            passwordVisible = !passwordVisible;
            int cursor = etPassword.getSelectionEnd();
            etPassword.setInputType(passwordVisible
                    ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                    : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            if (cursor >= 0) etPassword.setSelection(cursor);
            btnTogglePassword.setImageResource(passwordVisible
                    ? R.drawable.ic_visibility_off : R.drawable.ic_visibility);
            btnTogglePassword.setContentDescription(passwordVisible ? "Ẩn mật khẩu" : "Hiện mật khẩu");
        });

        findViewById(R.id.tvForgotPassword).setOnClickListener(v ->
                startActivity(new Intent(LoginActivity.this, ForgotPasswordActivity.class)));

        btnLogin.setOnClickListener(v -> {
            String user = etUsername.getText().toString().trim();
            // Khong trim mat khau: khoang trang dau/cuoi la mot phan cua mat khau.
            String pass = etPassword.getText().toString();

            if (user.isEmpty() || pass.isEmpty()) {
                Toast.makeText(LoginActivity.this, "Vui lòng nhập Username và Password", Toast.LENGTH_SHORT).show();
                return;
            }

            btnLogin.setEnabled(false);
            AuthApi authApi = ApiClient.getClient(LoginActivity.this).create(AuthApi.class);
            authApi.login(new LoginRequest(user, pass)).enqueue(new Callback<LoginResponse>() {
                @Override
                public void onResponse(Call<LoginResponse> call, Response<LoginResponse> response) {
                    btnLogin.setEnabled(true);
                    if (response.isSuccessful() && response.body() != null && response.body().isSuccess()
                            && response.body().getData() != null) {
                        LoginResponse loginResponse = response.body();
                        String role = loginResponse.getData().getRole() != null
                                ? loginResponse.getData().getRole() : "staff";
                        // Luu ca phien trong mot lan ghi (token + id + role + phong ban).
                        TokenManager.getInstance(LoginActivity.this).saveSession(
                                loginResponse.getToken(), loginResponse.getData().getId(), role,
                                loginResponse.getData().getDepartmentName(),
                                loginResponse.getData().getDepartmentId(),
                                loginResponse.getData().canApproveBod());
                        boolean isManagerRole = role.equalsIgnoreCase("admin") || role.equalsIgnoreCase("manager");
                        Toast.makeText(LoginActivity.this,
                                isManagerRole ? "Chào mừng Quản trị viên!" : "Chào mừng Nhân viên!",
                                Toast.LENGTH_SHORT).show();
                        Intent intent = new Intent(LoginActivity.this, HomeActivity.class);
                        intent.putExtra("USERNAME", loginResponse.getData().getUsername());
                        intent.putExtra("ROLE", role);
                        startActivity(intent);
                        finish();
                    } else if (response.body() != null && response.body().getMessage() != null) {
                        Toast.makeText(LoginActivity.this, response.body().getMessage(), Toast.LENGTH_SHORT).show();
                    } else {
                        // Loi HTTP (401 sai mat khau, 403 bi khoa, 429 qua nhieu lan): hien dung loi server.
                        Toast.makeText(LoginActivity.this,
                                ApiError.message(response, "Đăng nhập thất bại"), Toast.LENGTH_LONG).show();
                    }
                }

                @Override
                public void onFailure(Call<LoginResponse> call, Throwable t) {
                    btnLogin.setEnabled(true);
                    Toast.makeText(LoginActivity.this, "Lỗi kết nối: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
        });
    }
}
