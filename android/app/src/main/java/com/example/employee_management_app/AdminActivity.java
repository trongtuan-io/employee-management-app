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

// Man hinh danh rieng cho ADMIN: xem tong quan + dieu huong chuc nang quan tri
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
        tvRole.setText("Vai trò: " + TokenManager.getInstance(this).getRole());
        TextView tvWelcome = findViewById(R.id.tvAdminWelcome);
        String username = getIntent().getStringExtra("USERNAME");
        tvWelcome.setText("Xin chào " + (username != null ? username : "") + "!");
        Button btnApprove = findViewById(R.id.btnAdminApprove);
        Button btnStaff = findViewById(R.id.btnAdminStaff);
        Button btnCreateAccount = findViewById(R.id.btnAdminCreateAccount);
        Button btnPass = findViewById(R.id.btnAdminChangePass);
        Button btnLogout = findViewById(R.id.btnAdminLogout);

        loadDashboard(btnApprove);

        // Chuc nang cua ban D (duyet phep): man hinh chi tiet lam tiep
        btnApprove.setOnClickListener(v ->
                Toast.makeText(this, "Mở danh sách duyệt phép (bạn D làm tiếp)", Toast.LENGTH_SHORT).show());
        btnStaff.setOnClickListener(v ->
                startActivity(new Intent(this, StaffListActivity.class)));
        btnCreateAccount.setOnClickListener(v ->
                startActivity(new Intent(this, CreateAccountActivity.class)));
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

    // So lieu that tu API dashboard
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
