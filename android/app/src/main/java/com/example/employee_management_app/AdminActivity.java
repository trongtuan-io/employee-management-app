package com.example.employee_management_app;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.View;
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
        String accessRole = TokenManager.getInstance(this).getRole();
        if (!"admin".equalsIgnoreCase(accessRole) && !"manager".equalsIgnoreCase(accessRole)) {
            startActivity(new Intent(this, HomeActivity.class));
            finish();
            return;
        }
        setContentView(R.layout.activity_admin);
        findViewById(R.id.btnBackAdmin).setOnClickListener(v -> finish());
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
        if (btnCreateAccount != null) {
            boolean hrManager = "manager".equalsIgnoreCase(myRole)
                    && "HR".equalsIgnoreCase(TokenManager.getInstance(this).getDepartmentName());
            btnCreateAccount.setVisibility(("admin".equalsIgnoreCase(myRole) || hrManager) ? View.VISIBLE : View.GONE);
        }
        Button btnPass = findViewById(R.id.btnAdminChangePass);
        Button btnLogout = findViewById(R.id.btnAdminLogout);
        Button btnExportAtt = findViewById(R.id.btnAdminExportAtt);
        boolean isExec = TokenManager.getInstance(this).isExecutive();
        boolean isPayroll = "admin".equalsIgnoreCase(myRole) || "ACC".equalsIgnoreCase(TokenManager.getInstance(this).getDepartmentName());
        Button btnProposals = findViewById(R.id.btnAdminProposals);
        if (btnProposals != null) {
            btnProposals.setVisibility(isExec ? android.view.View.VISIBLE : android.view.View.GONE);
            btnProposals.setOnClickListener(v ->
                    startActivity(new Intent(this, ProposalApprovalActivity.class)));
        }
        Button btnHrHub = findViewById(R.id.btnAdminHrHub);
        if (btnHrHub != null) {
            btnHrHub.setVisibility(HrUi.hr(this) ? android.view.View.VISIBLE : android.view.View.GONE);
            btnHrHub.setOnClickListener(v ->
                    startActivity(new Intent(this, HrHubActivity.class)));
        }
        Button btnSalaryOverview = findViewById(R.id.btnAdminSalaryOverview);
        if (btnSalaryOverview != null) {
            btnSalaryOverview.setVisibility(isExec || isPayroll ? android.view.View.VISIBLE : android.view.View.GONE);
            btnSalaryOverview.setOnClickListener(v ->
                    startActivity(new Intent(this, SalaryOverviewActivity.class)));
        }
        Button btnBodDashboard = findViewById(R.id.btnAdminBodDashboard);
        if (btnBodDashboard != null) {
            btnBodDashboard.setVisibility(isExec ? android.view.View.VISIBLE : android.view.View.GONE);
            btnBodDashboard.setOnClickListener(v ->
                    startActivity(new Intent(this, BodDashboardActivity.class)));
        }
        Button btnGps = findViewById(R.id.btnAdminGps);
        if (btnGps != null) {
            btnGps.setVisibility(isExec ? android.view.View.VISIBLE : android.view.View.GONE);
            btnGps.setOnClickListener(v -> showGpsSettingsDialog());
        }
        Button btnLeaveQuota = findViewById(R.id.btnAdminLeaveQuota);
        if (btnLeaveQuota != null) {
            btnLeaveQuota.setVisibility(isExec ? android.view.View.VISIBLE : android.view.View.GONE);
            btnLeaveQuota.setOnClickListener(v -> showLeaveQuotaDialog());
        }
        btnExportAtt.setOnClickListener(v ->
                startActivity(new Intent(this, AttendanceReportActivity.class)));

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
                            Uri uri = null;
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                ContentValues values = new ContentValues();
                                values.put(MediaStore.Downloads.DISPLAY_NAME, name);
                                values.put(MediaStore.Downloads.MIME_TYPE, "text/csv");
                                values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                                ContentResolver resolver = getContentResolver();
                                uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                                if (uri == null) throw new IllegalStateException("insert failed");
                                try (OutputStream os = resolver.openOutputStream(uri)) {
                                    if (os == null) throw new IllegalStateException("open failed");
                                    os.write(bytes);
                                }
                            } else {
                                java.io.File dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
                                if (dir == null) throw new IllegalStateException("no dir");
                                java.io.File targetFile = new java.io.File(dir, name);
                                try (OutputStream os = new java.io.FileOutputStream(targetFile)) {
                                    os.write(bytes);
                                }
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                                    uri = androidx.core.content.FileProvider.getUriForFile(AdminActivity.this, getApplicationContext().getPackageName() + ".fileprovider", targetFile);
                                } else {
                                    uri = Uri.fromFile(targetFile);
                                }
                            }
                            final Uri finalUri = uri;
                            runOnUiThread(() -> promptOpenCsv(finalUri, name, bytes));
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

    private void promptOpenCsv(Uri uri, String name, byte[] bytes) {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Xuất file thành công")
                .setMessage("Đã lưu " + name + " vào thư mục Tải xuống.")
                .setPositiveButton("Mở bằng Excel/Sheets", (dialog, which) -> {
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.setDataAndType(uri, "text/csv");
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    try {
                        startActivity(Intent.createChooser(intent, "Mở file bằng"));
                    } catch (Exception ex) {
                        Toast.makeText(this, "Máy ảo chưa cài ứng dụng đọc file (Google Sheets/Excel)", Toast.LENGTH_LONG).show();
                    }
                })
                .setNeutralButton("Xem nhanh", (dialog, which) -> {
                    showCsvPreviewDialog("Xem trước " + name, bytes);
                })
                .setNegativeButton("Đóng", null)
                .show();
    }

    private void showCsvPreviewDialog(String title, byte[] bytes) {
        String csvContent = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        android.widget.TextView tv = new android.widget.TextView(this);
        tv.setText(csvContent);
        tv.setTypeface(android.graphics.Typeface.MONOSPACE);
        tv.setTextSize(12);
        tv.setPadding(32, 32, 32, 32);
        scroll.addView(tv);

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(title)
                .setView(scroll)
                .setPositiveButton("Đóng", null)
                .show();
    }

    private void showGpsSettingsDialog() {
        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(40, 20, 40, 20);

        final android.widget.EditText edtLat = new android.widget.EditText(this);
        edtLat.setHint("Vĩ độ GPS (Latitude)");
        edtLat.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL | android.text.InputType.TYPE_NUMBER_FLAG_SIGNED);

        final android.widget.EditText edtLng = new android.widget.EditText(this);
        edtLng.setHint("Kinh độ GPS (Longitude)");
        edtLng.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL | android.text.InputType.TYPE_NUMBER_FLAG_SIGNED);

        final android.widget.EditText edtRadius = new android.widget.EditText(this);
        edtRadius.setHint("Bán kính chấm công (mét)");
        edtRadius.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);

        layout.addView(edtLat);
        layout.addView(edtLng);
        layout.addView(edtRadius);

        EmployeeApi api = ApiClient.getClient(this).create(EmployeeApi.class);
        api.getSettings().enqueue(new Callback<EmployeeApi.SettingsResponse>() {
            @Override
            public void onResponse(Call<EmployeeApi.SettingsResponse> c, Response<EmployeeApi.SettingsResponse> r) {
                if (r.isSuccessful() && r.body() != null && r.body().getData() != null) {
                    java.util.Map<String, String> m = r.body().getData();
                    if (m.containsKey("office_lat")) edtLat.setText(m.get("office_lat"));
                    if (m.containsKey("office_lng")) edtLng.setText(m.get("office_lng"));
                    if (m.containsKey("office_radius")) edtRadius.setText(m.get("office_radius"));
                }
            }
            @Override public void onFailure(Call<EmployeeApi.SettingsResponse> c, Throwable t) {}
        });

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Cấu hình vị trí & bán kính GPS (BOD)")
                .setView(layout)
                .setPositiveButton("Lưu vị trí GPS", (dialog, which) -> {
                    java.util.Map<String, String> map = new java.util.HashMap<>();
                    map.put("office_lat", edtLat.getText().toString().trim());
                    map.put("office_lng", edtLng.getText().toString().trim());
                    map.put("office_radius", edtRadius.getText().toString().trim());

                    api.saveSettings(map).enqueue(new Callback<ApiResponse>() {
                        @Override
                        public void onResponse(Call<ApiResponse> c, Response<ApiResponse> r) {
                            if (r.isSuccessful() && r.body() != null && r.body().isSuccess()) {
                                Toast.makeText(AdminActivity.this, "Đã cập nhật mốc vị trí GPS văn phòng thành công!", Toast.LENGTH_SHORT).show();
                            } else {
                                Toast.makeText(AdminActivity.this, ApiError.message(r, "Không lưu được cấu hình GPS"), Toast.LENGTH_SHORT).show();
                            }
                        }
                        @Override public void onFailure(Call<ApiResponse> c, Throwable t) {
                            Toast.makeText(AdminActivity.this, "Lỗi kết nối", Toast.LENGTH_SHORT).show();
                        }
                    });
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    private void showLeaveQuotaDialog() {
        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(40, 20, 40, 20);

        final android.widget.EditText edtQuota = new android.widget.EditText(this);
        edtQuota.setHint("Hạn mức phép năm (ngày)");
        edtQuota.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);

        layout.addView(edtQuota);

        EmployeeApi api = ApiClient.getClient(this).create(EmployeeApi.class);
        api.getSettings().enqueue(new Callback<EmployeeApi.SettingsResponse>() {
            @Override
            public void onResponse(Call<EmployeeApi.SettingsResponse> c, Response<EmployeeApi.SettingsResponse> r) {
                if (r.isSuccessful() && r.body() != null && r.body().getData() != null) {
                    java.util.Map<String, String> m = r.body().getData();
                    if (m.containsKey("annual_leave_quota")) edtQuota.setText(m.get("annual_leave_quota"));
                }
            }
            @Override public void onFailure(Call<EmployeeApi.SettingsResponse> c, Throwable t) {}
        });

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Cấu hình hạn mức Phép năm (BOD)")
                .setView(layout)
                .setPositiveButton("Lưu hạn mức", (dialog, which) -> {
                    java.util.Map<String, String> map = new java.util.HashMap<>();
                    map.put("annual_leave_quota", edtQuota.getText().toString().trim());

                    api.saveSettings(map).enqueue(new Callback<ApiResponse>() {
                        @Override
                        public void onResponse(Call<ApiResponse> c, Response<ApiResponse> r) {
                            if (r.isSuccessful() && r.body() != null && r.body().isSuccess()) {
                                Toast.makeText(AdminActivity.this, "Đã cập nhật hạn mức phép năm toàn công ty thành công!", Toast.LENGTH_SHORT).show();
                            } else {
                                Toast.makeText(AdminActivity.this, ApiError.message(r, "Không lưu được hạn mức"), Toast.LENGTH_SHORT).show();
                            }
                        }
                        @Override public void onFailure(Call<ApiResponse> c, Throwable t) {
                            Toast.makeText(AdminActivity.this, "Lỗi kết nối", Toast.LENGTH_SHORT).show();
                        }
                    });
                })
                .setNegativeButton("Hủy", null)
                .show();
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
