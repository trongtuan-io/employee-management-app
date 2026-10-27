package com.example.employee_management_app;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

// Màn hình Quản trị (Admin / Sếp Manager)
public class AdminActivity extends AppCompatActivity {
    private TextView tvTotalStaff, tvPendingLeaves, tvLateToday;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin);

        tvTotalStaff = findViewById(R.id.tvTotalStaff);
        tvPendingLeaves = findViewById(R.id.tvPendingLeaves);
        tvLateToday = findViewById(R.id.tvLateToday);
        TextView tvRole = findViewById(R.id.tvAdminRole);

        String currentRole = TokenManager.getInstance(this).getRole();
        tvRole.setText("Vai trò: " + currentRole);

        TextView tvWelcome = findViewById(R.id.tvAdminWelcome);
        String username = getIntent().getStringExtra("USERNAME");
        tvWelcome.setText("Xin chào " + (username != null ? username : "") + "!");

        Button btnApprove = findViewById(R.id.btnAdminApprove);
        Button btnStaff = findViewById(R.id.btnAdminStaff);
        Button btnPass = findViewById(R.id.btnAdminChangePass);
        Button btnLogout = findViewById(R.id.btnAdminLogout);

        // Nút xem báo cáo chấm công & nút chấm công cá nhân
        Button btnViewAttendanceReport = findViewById(R.id.btnViewAttendanceReport);
        Button btnAdminSelfCheckin = findViewById(R.id.btnAdminSelfCheckin);

        // PHÂN QUYỀN: Admin không cần chấm công -> Ẩn nút chấm công; Sếp (Manager) cần chấm công -> Hiện nút
        if ("admin".equalsIgnoreCase(currentRole)) {
            btnAdminSelfCheckin.setVisibility(View.GONE);
        } else {
            btnAdminSelfCheckin.setVisibility(View.VISIBLE);
        }

        loadDashboard(btnApprove);

        btnApprove.setOnClickListener(v ->
                Toast.makeText(this, "Mở danh sách duyệt phép", Toast.LENGTH_SHORT).show());
        btnStaff.setOnClickListener(v ->
                startActivity(new Intent(this, StaffListActivity.class)));
        btnPass.setOnClickListener(v ->
                startActivity(new Intent(this, ChangePasswordActivity.class)));

        // Mở trang Báo cáo chấm công
        btnViewAttendanceReport.setOnClickListener(v -> {
            Intent intent = new Intent(AdminActivity.this, AdminAttendanceReportActivity.class);
            startActivity(intent);
        });

        // Mở màn hình Chấm công cho Sếp / Manager
        btnAdminSelfCheckin.setOnClickListener(v -> {
            Intent intent = new Intent(AdminActivity.this, AttendanceActivity.class);
            startActivity(intent);
        });

        btnLogout.setOnClickListener(v -> {
            TokenManager.getInstance(this).clearToken();
            Intent i = new Intent(this, LoginActivity.class);
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(i);
            finish();
        });
    }

    // Số liệu tổng quan từ API Dashboard
    private void loadDashboard(Button btnApprove) {
        EmployeeApi api = ApiClient.getClient(this).create(EmployeeApi.class);
        api.getDashboard().enqueue(new Callback<DashboardResponse>() {
            @Override
            public void onResponse(Call<DashboardResponse> c, Response<DashboardResponse> r) {
                if (r.isSuccessful() && r.body() != null && r.body().isSuccess()
                        && r.body().getData() != null) {
                    DashboardResponse.Stats s = r.body().getData();
                    tvTotalStaff.setText(String.valueOf(s.getTotalActive()));
                    tvPendingLeaves.setText(String.valueOf(s.getPendingLeaves()));
                    tvLateToday.setText(String.valueOf(s.getLateToday()));
                    btnApprove.setText("Duyệt nghỉ phép (" + s.getPendingLeaves() + " đơn chờ)");
                }
            }

            @Override
            public void onFailure(Call<DashboardResponse> c, Throwable t) {
                Toast.makeText(AdminActivity.this, "Không tải được số liệu: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }
}
