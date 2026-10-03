package com.example.employee_management_app;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ForgotPasswordActivity extends AppCompatActivity {
    private LinearLayout step1, step2, step3;
    private EditText etEmail, etCode, etNewPass, etConfirmPass;
    private TextView tvHint2;
    private String email, resetToken;
    private boolean isEmailChannel = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_forgot_password);

        step1 = findViewById(R.id.step1);
        step2 = findViewById(R.id.step2);
        step3 = findViewById(R.id.step3);
        etEmail = findViewById(R.id.etForgotEmail);
        etCode = findViewById(R.id.etVerifyCode);
        etNewPass = findViewById(R.id.etForgotNewPass);
        etConfirmPass = findViewById(R.id.etForgotConfirmPass);
        tvHint2 = findViewById(R.id.tvHint2);
        Button btnSend = findViewById(R.id.btnSendCode);
        Button btnVerify = findViewById(R.id.btnVerifyCode);
        Button btnReset = findViewById(R.id.btnResetPass);
        Button btnChannelEmail = findViewById(R.id.btnChannelEmail);
        Button btnChannelSms = findViewById(R.id.btnChannelSms);

        // Chon kenh: Email hoac SMS (so dien thoai phai co trong DB)
        btnChannelEmail.setOnClickListener(v -> {
            isEmailChannel = true;
            etEmail.setText("");
            etEmail.setHint("Email đăng ký (phải có trong hệ thống)");
            etEmail.setInputType(android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        });
        btnChannelSms.setOnClickListener(v -> {
            isEmailChannel = false;
            etEmail.setText("");
            etEmail.setHint("Số điện thoại đăng ký (vd 0975120205)");
            etEmail.setInputType(android.text.InputType.TYPE_CLASS_PHONE);
        });

        btnSend.setOnClickListener(v -> sendCode());
        btnVerify.setOnClickListener(v -> verifyCode());
        btnReset.setOnClickListener(v -> resetPassword());
    }

    private void showStep(int n) {
        step1.setVisibility(n == 1 ? View.VISIBLE : View.GONE);
        step2.setVisibility(n == 2 ? View.VISIBLE : View.GONE);
        step3.setVisibility(n == 3 ? View.VISIBLE : View.GONE);
    }

    // Buoc 1: nhap email HOAC sdt -> server doi chieu DB roi gui ma (mail/SMS)
    private void sendCode() {
        email = etEmail.getText().toString().trim();
        if (email.isEmpty()) {
            Toast.makeText(this, isEmailChannel ? "Vui lòng nhập email" : "Vui lòng nhập số điện thoại", Toast.LENGTH_SHORT).show();
            return;
        }
        AuthApi api = ApiClient.getClient(this).create(AuthApi.class);
        api.forgotPassword(new ForgotRequest(email, isEmailChannel)).enqueue(new Callback<ForgotResponse>() {
            @Override
            public void onResponse(Call<ForgotResponse> c, Response<ForgotResponse> r) {
                if (r.isSuccessful() && r.body() != null && r.body().isSuccess()) {
                    tvHint2.setText("Mã đã gửi tới " + email + " (hiệu lực 10 phút)");
                    showStep(2);
                    Toast.makeText(ForgotPasswordActivity.this, "Đã gửi mã xác nhận!", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(ForgotPasswordActivity.this,
                            isEmailChannel ? "Email không tồn tại trong hệ thống" : "Số điện thoại không tồn tại trong hệ thống",
                            Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<ForgotResponse> c, Throwable t) {
                Toast.makeText(ForgotPasswordActivity.this, "Lỗi kết nối: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    // Buoc 2: nhap ma 6 so -> doi lay reset_token
    private void verifyCode() {
        String code = etCode.getText().toString().trim();
        if (code.length() != 6) {
            Toast.makeText(this, "Mã gồm 6 số", Toast.LENGTH_SHORT).show();
            return;
        }
        AuthApi api = ApiClient.getClient(this).create(AuthApi.class);
        api.verifyCode(new VerifyRequest(email, code, isEmailChannel)).enqueue(new Callback<VerifyResponse>() {
            @Override
            public void onResponse(Call<VerifyResponse> c, Response<VerifyResponse> r) {
                if (r.isSuccessful() && r.body() != null && r.body().isSuccess()
                        && r.body().getData() != null) {
                    resetToken = r.body().getData().getResetToken();
                    showStep(3);
                } else {
                    Toast.makeText(ForgotPasswordActivity.this, "Mã sai hoặc hết hạn", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<VerifyResponse> c, Throwable t) {
                Toast.makeText(ForgotPasswordActivity.this, "Lỗi kết nối: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    // Buoc 3: dat mat khau moi -> xong ve man login
    private void resetPassword() {
        String p1 = etNewPass.getText().toString().trim();
        String p2 = etConfirmPass.getText().toString().trim();
        if (p1.length() < 6) {
            Toast.makeText(this, "Mật khẩu ít nhất 6 ký tự", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!p1.equals(p2)) {
            Toast.makeText(this, "Xác nhận không khớp!", Toast.LENGTH_SHORT).show();
            return;
        }
        AuthApi api = ApiClient.getClient(this).create(AuthApi.class);
        api.resetPassword(new ResetRequest(resetToken, p1)).enqueue(new Callback<ApiResponse>() {
            @Override
            public void onResponse(Call<ApiResponse> c, Response<ApiResponse> r) {
                if (r.isSuccessful() && r.body() != null && r.body().isSuccess()) {
                    Toast.makeText(ForgotPasswordActivity.this, "Đổi mật khẩu xong, đăng nhập lại!", Toast.LENGTH_SHORT).show();
                    finish();
                } else {
                    Toast.makeText(ForgotPasswordActivity.this, "Phiên hết hạn, làm lại từ đầu", Toast.LENGTH_SHORT).show();
                    showStep(1);
                }
            }

            @Override
            public void onFailure(Call<ApiResponse> c, Throwable t) {
                Toast.makeText(ForgotPasswordActivity.this, "Lỗi kết nối: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }
}
