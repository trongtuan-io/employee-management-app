package com.example.employee_management_app;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

// Man hinh danh rieng cho NHAN VIEN: xem ho so + cham cong that
public class EmployeeActivity extends AppCompatActivity {
    private TextView tvName, tvInfo;
    private int employeeId = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_employee);

        tvName = findViewById(R.id.tvEmpName);
        tvInfo = findViewById(R.id.tvEmpInfo);
        TextView tvRole = findViewById(R.id.tvEmpRole);
        tvRole.setText("Vai trò: " + TokenManager.getInstance(this).getRole());
        Button btnIn = findViewById(R.id.btnCheckIn);
        Button btnOut = findViewById(R.id.btnCheckOut);
        Button btnLeave = findViewById(R.id.btnMyLeave);
        Button btnProfile = findViewById(R.id.btnEmpProfile);
        Button btnPass = findViewById(R.id.btnEmpChangePass);
        Button btnLogout = findViewById(R.id.btnEmpLogout);

        employeeId = TokenManager.getInstance(this).getUserId();
        loadProfile();

        btnIn.setOnClickListener(v -> doAttendance(true));
        btnOut.setOnClickListener(v -> doAttendance(false));
        btnProfile.setOnClickListener(v ->
                startActivity(new Intent(this, ProfileActivity.class)));
        // Man hinh xin nghi / xem luong: ban D lam tiep
        btnLeave.setOnClickListener(v ->
                Toast.makeText(this, "Xin nghỉ phép (bạn D làm tiếp)", Toast.LENGTH_SHORT).show());
        btnPass.setOnClickListener(v ->
                startActivity(new Intent(this, ChangePasswordActivity.class)));
        btnLogout.setOnClickListener(v -> {
            TokenManager.getInstance(this).clearToken();
            Intent i = new Intent(this, LoginActivity.class);
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(i);
            finish();
        });
    }

    // Ho so that tu API
    private void loadProfile() {
        if (employeeId == -1) return;
        EmployeeApi api = ApiClient.getClient(this).create(EmployeeApi.class);
        api.getDetail(employeeId).enqueue(new Callback<EmployeeDetailResponse>() {
            @Override
            public void onResponse(Call<EmployeeDetailResponse> c, Response<EmployeeDetailResponse> r) {
                if (r.isSuccessful() && r.body() != null && r.body().isSuccess()
                        && r.body().getData() != null) {
                    EmployeeDetailResponse.Detail d = r.body().getData();
                    tvName.setText(d.getFullName());
                    tvInfo.setText("Mã NV: " + d.getUsername()
                            + "\nPhòng: " + d.getDeptName()
                            + "\nChức vụ: " + d.getPositionName()
                            + "\nEmail: " + d.getEmail()
                            + "\nSĐT: " + d.getPhone());
                }
            }

            @Override
            public void onFailure(Call<EmployeeDetailResponse> c, Throwable t) {
                Toast.makeText(EmployeeActivity.this, "Không tải được hồ sơ", Toast.LENGTH_SHORT).show();
            }
        });
    }

    // Cham cong that: 1 ngay 1 lan, qua 8h15 tinh di muon (server xu ly)
    private void doAttendance(boolean isIn) {
        if (employeeId == -1) return;
        EmployeeApi api = ApiClient.getClient(this).create(EmployeeApi.class);
        Call<AttendanceResponse> call = isIn
                ? api.checkIn(new CheckinRequest(employeeId))
                : api.checkOut(new CheckinRequest(employeeId));
        call.enqueue(new Callback<AttendanceResponse>() {
            @Override
            public void onResponse(Call<AttendanceResponse> c, Response<AttendanceResponse> r) {
                if (r.isSuccessful() && r.body() != null && r.body().isSuccess()
                        && r.body().getData() != null) {
                    AttendanceResponse.Result d = r.body().getData();
                    String msg = isIn
                            ? "Check-in lúc " + d.getCheckIn() + " (" + d.getStatus() + ")"
                            : "Check-out lúc " + d.getCheckOut();
                    Toast.makeText(EmployeeActivity.this, msg, Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(EmployeeActivity.this,
                            isIn ? "Hôm nay đã check-in rồi" : "Chưa check-in hôm nay",
                            Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<AttendanceResponse> c, Throwable t) {
                Toast.makeText(EmployeeActivity.this, "Lỗi kết nối: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }
}
