package com.example.employee_management_app;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.OutputStream;
import java.util.Calendar;

import okhttp3.ResponseBody;
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
        BottomNavHelper.setup(this, R.id.nav_overview);

        tvTotalStaff = findViewById(R.id.tvTotalStaff);
        tvPendingLeaves = findViewById(R.id.tvPendingLeaves);
        tvLateToday = findViewById(R.id.tvLateToday);
        TextView tvRole = findViewById(R.id.tvAdminRole);
        tvRole.setText("Vai trò: " + TokenManager.getInstance(this).getRole());
        TextView tvWelcome = findViewById(R.id.tvAdminWelcome);
        String username = getIntent().getStringExtra("USERNAME");
        tvWelcome.setText(username != null && !username.isEmpty() ? "Xin chào " + username + "!" : "Xin chào!");
        Button btnApprove = findViewById(R.id.btnAdminApprove);
        Button btnStaff = findViewById(R.id.btnAdminStaff);
        Button btnCreateAccount = findViewById(R.id.btnAdminCreateAccount);
        // Tao tai khoan chi cho admin/manager; NV phong HR/Ke toan vao duoc man
        // Tong quan qua menu day du nhung khong thay nut nay (API cung se chan).
        String myRole = TokenManager.getInstance(this).getRole();
        if (!("admin".equalsIgnoreCase(myRole) || "manager".equalsIgnoreCase(myRole))) {
            btnCreateAccount.setVisibility(android.view.View.GONE);
        }
        Button btnPass = findViewById(R.id.btnAdminChangePass);
        Button btnLogout = findViewById(R.id.btnAdminLogout);
        Button btnExportAtt = findViewById(R.id.btnAdminExportAtt);
        btnExportAtt.setOnClickListener(v -> exportAttendanceCsv());

        loadDashboard(btnApprove);

        // Duyet nghi phep that: mo LeaveActivity (che do quan ly hien hang cho duyet)
        btnApprove.setOnClickListener(v ->
                startActivity(new Intent(this, LeaveActivity.class)));
        btnStaff.setOnClickListener(v ->
                startActivity(new Intent(this, StaffListActivity.class)));
        btnCreateAccount.setOnClickListener(v ->
                startActivity(new Intent(this, CreateAccountActivity.class)));
        btnPass.setOnClickListener(v ->
                startActivity(new Intent(this, ChangePasswordActivity.class)));
        btnLogout.setOnClickListener(v -> {
            // Thu hoi token phia server truoc, roi moi xoa phien o client.
            ApiClient.getClient(this).create(AuthApi.class).logout().enqueue(new Callback<ApiResponse>() {
                @Override public void onResponse(Call<ApiResponse> c, Response<ApiResponse> r) { }
                @Override public void onFailure(Call<ApiResponse> c, Throwable t) { }
            });
            TokenManager.getInstance(this).clearToken();
            Intent i = new Intent(this, LoginActivity.class);
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(i);
            finish();
        });
    }

    // Tai CSV bang cong thang hien tai ve thu muc Tai xuong (pham vi theo
    // phong/cong ty do server quyet dinh theo token).
    private void exportAttendanceCsv() {
        Calendar now = Calendar.getInstance();
        int month = now.get(Calendar.MONTH) + 1;
        int year = now.get(Calendar.YEAR);
        Toast.makeText(this, "Đang tạo file bảng công...", Toast.LENGTH_SHORT).show();
        ApiClient.getClient(this).create(EmployeeApi.class).exportAttendance(month, year)
                .enqueue(new Callback<ResponseBody>() {
                    @Override
                    public void onResponse(Call<ResponseBody> c, Response<ResponseBody> r) {
                        if (!r.isSuccessful() || r.body() == null) {
                            if (r.code() != 401) Toast.makeText(AdminActivity.this, "Không xuất được file", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        try {
                            byte[] bytes = r.body().bytes();
                            String name = String.format("bang_cong_%02d_%d.csv", month, year);
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                ContentValues values = new ContentValues();
                                values.put(MediaStore.Downloads.DISPLAY_NAME, name);
                                values.put(MediaStore.Downloads.MIME_TYPE, "text/csv");
                                values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                                ContentResolver resolver = getContentResolver();
                                Uri uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                                if (uri == null) throw new IllegalStateException("insert failed");
                                try (OutputStream os = resolver.openOutputStream(uri)) {
                                    if (os == null) throw new IllegalStateException("open failed");
                                    os.write(bytes);
                                }
                            } else {
                                java.io.File dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
                                if (dir == null) throw new IllegalStateException("no dir");
                                try (OutputStream os = new java.io.FileOutputStream(new java.io.File(dir, name))) {
                                    os.write(bytes);
                                }
                            }
                            Toast.makeText(AdminActivity.this,
                                    "Đã lưu " + name + " vào thư mục Tải xuống — mở bằng Excel được", Toast.LENGTH_LONG).show();
                        } catch (Exception e) {
                            Toast.makeText(AdminActivity.this, "Không lưu được file: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<ResponseBody> c, Throwable t) {
                        Toast.makeText(AdminActivity.this, "Không xuất được file", Toast.LENGTH_SHORT).show();
                    }
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
                } else {
                    Toast.makeText(AdminActivity.this,
                            ApiError.message(r, "Không tải được số liệu tổng quan"), Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<DashboardResponse> c, Throwable t) {
                Toast.makeText(AdminActivity.this, "Không tải được số liệu: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }
}
