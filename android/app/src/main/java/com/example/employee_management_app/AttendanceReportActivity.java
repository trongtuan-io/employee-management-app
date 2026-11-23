package com.example.employee_management_app;

import android.app.DatePickerDialog;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class AttendanceReportActivity extends AppCompatActivity {
    private EditText edtDate, edtMonth, edtYear;
    private Spinner spFilterMode, spDept;
    private LinearLayout layoutDateFilter, layoutMonthFilter;
    private TextView tvStatus;
    private ListView lvReport;
    private EmployeeApi api;
    private final List<AttendanceReportItem> reportItems = new ArrayList<>();
    private ArrayAdapter<AttendanceReportItem> adapter;
    private final List<DepartmentListResponse.Item> deptChoices = new ArrayList<>();
    private ArrayAdapter<DepartmentListResponse.Item> deptAdapter;

    private final String[] filterModes = {"Lọc theo Ngày", "Lọc theo Tháng"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_attendance_report);
        applyWindowInsets();

        findViewById(R.id.btnBackReport).setOnClickListener(v -> finish());
        spFilterMode = findViewById(R.id.spReportFilterMode);
        spDept = findViewById(R.id.spReportDept);
        layoutDateFilter = findViewById(R.id.layoutDateFilter);
        layoutMonthFilter = findViewById(R.id.layoutMonthFilter);
        edtDate = findViewById(R.id.edtReportDate);
        edtMonth = findViewById(R.id.edtReportMonth);
        edtYear = findViewById(R.id.edtReportYear);
        tvStatus = findViewById(R.id.tvReportStatus);
        lvReport = findViewById(R.id.lvAttendanceReport);

        api = ApiClient.getClient(this).create(EmployeeApi.class);

        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, reportItems);
        lvReport.setAdapter(adapter);

        Calendar cal = Calendar.getInstance();
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.getTime());
        edtDate.setText(today);
        edtMonth.setText(String.valueOf(cal.get(Calendar.MONTH) + 1));
        edtYear.setText(String.valueOf(cal.get(Calendar.YEAR)));

        edtDate.setOnClickListener(v -> pickDate());

        spFilterMode.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, filterModes));
        spFilterMode.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position == 1) { // Theo Thang
                    layoutDateFilter.setVisibility(View.GONE);
                    layoutMonthFilter.setVisibility(View.VISIBLE);
                } else { // Theo Ngay
                    layoutDateFilter.setVisibility(View.VISIBLE);
                    layoutMonthFilter.setVisibility(View.GONE);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        findViewById(R.id.btnLoadReport).setOnClickListener(v -> loadReport());
        findViewById(R.id.btnLoadMonthReport).setOnClickListener(v -> loadReport());
        findViewById(R.id.btnExportReportCsv).setOnClickListener(v -> exportCsv());

        setupDepartments();
    }

    private void pickDate() {
        Calendar cal = Calendar.getInstance();
        String cur = edtDate.getText().toString().trim();
        if (!cur.isEmpty()) {
            try {
                java.util.Date d = new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(cur);
                if (d != null) cal.setTime(d);
            } catch (Exception ignored) { }
        }
        new DatePickerDialog(this, (view, y, m, d) ->
                edtDate.setText(String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, d)),
                cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void setupDepartments() {
        boolean isExec = TokenManager.getInstance(this).isExecutive();
        int myDeptId = TokenManager.getInstance(this).getDepartmentId();

        deptAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, deptChoices);
        deptAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spDept.setAdapter(deptAdapter);

        api.getDepartments().enqueue(new Callback<DepartmentListResponse>() {
            @Override
            public void onResponse(Call<DepartmentListResponse> c, Response<DepartmentListResponse> r) {
                if (isFinishing() || isDestroyed()) return;
                if (r.isSuccessful() && r.body() != null && r.body().isSuccess() && r.body().getData() != null) {
                    deptChoices.clear();
                    if (isExec) {
                        deptChoices.add(new DepartmentListResponse.Item(0, "Tất cả phòng ban"));
                    }
                    deptChoices.addAll(r.body().getData());
                    deptAdapter.notifyDataSetChanged();

                    if (!isExec && myDeptId > 0) {
                        for (int i = 0; i < deptChoices.size(); i++) {
                            if (deptChoices.get(i).getId() == myDeptId) {
                                spDept.setSelection(i);
                                break;
                            }
                        }
                        spDept.setEnabled(false);
                    }
                }
                loadReport();
            }

            @Override
            public void onFailure(Call<DepartmentListResponse> c, Throwable t) {
                loadReport();
            }
        });
    }

    private void loadReport() {
        String dateParam = null;
        Integer monthParam = null;
        Integer yearParam = null;

        if (spFilterMode.getSelectedItemPosition() == 1) { // Theo Thang
            String mStr = edtMonth.getText().toString().trim();
            String yStr = edtYear.getText().toString().trim();
            if (!mStr.isEmpty()) {
                try { monthParam = Integer.parseInt(mStr); } catch (Exception ignored) {}
            }
            if (!yStr.isEmpty()) {
                try { yearParam = Integer.parseInt(yStr); } catch (Exception ignored) {}
            }
            if (monthParam == null || monthParam < 1 || monthParam > 12) {
                Toast.makeText(this, "Vui lòng nhập tháng từ 1 đến 12", Toast.LENGTH_SHORT).show();
                return;
            }
            if (yearParam == null || yearParam < 2000) {
                Toast.makeText(this, "Vui lòng nhập năm hợp lệ (vd: 2026)", Toast.LENGTH_SHORT).show();
                return;
            }
        } else { // Theo Ngay
            dateParam = edtDate.getText().toString().trim();
            if (dateParam.isEmpty()) {
                dateParam = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Calendar.getInstance().getTime());
            }
        }

        Integer deptId = null;
        int pos = spDept.getSelectedItemPosition();
        if (pos >= 0 && pos < deptChoices.size()) {
            int id = deptChoices.get(pos).getId();
            if (id > 0) deptId = id;
        }

        tvStatus.setText("Đang tải báo cáo...");
        reportItems.clear();
        adapter.notifyDataSetChanged();

        api.getAttendanceReport(dateParam, monthParam, yearParam, deptId).enqueue(new Callback<AttendanceReportResponse>() {
            @Override
            public void onResponse(Call<AttendanceReportResponse> c, Response<AttendanceReportResponse> r) {
                if (isFinishing() || isDestroyed()) return;
                if (r.isSuccessful() && r.body() != null && r.body().isSuccess() && r.body().getData() != null) {
                    reportItems.addAll(r.body().getData());
                    adapter.notifyDataSetChanged();
                    tvStatus.setText(reportItems.isEmpty() ? "Không có dữ liệu chấm công" : "Tổng: " + reportItems.size() + " bản ghi");
                } else {
                    tvStatus.setText(ApiError.message(r, "Không tải được báo cáo"));
                }
            }

            @Override
            public void onFailure(Call<AttendanceReportResponse> c, Throwable t) {
                if (!isFinishing()) {
                    tvStatus.setText("Lỗi kết nối: " + t.getMessage());
                }
            }
        });
    }

    private void exportCsv() {
        int month = Calendar.getInstance().get(Calendar.MONTH) + 1;
        int year = Calendar.getInstance().get(Calendar.YEAR);
        if (spFilterMode.getSelectedItemPosition() == 1) {
            try {
                int m = Integer.parseInt(edtMonth.getText().toString().trim());
                int y = Integer.parseInt(edtYear.getText().toString().trim());
                if (m >= 1 && m <= 12) month = m;
                if (y >= 2000) year = y;
            } catch (Exception ignored) {}
        }
        final int finalMonth = month;
        final int finalYear = year;
        Toast.makeText(this, "Đang tạo file bảng công tháng " + month + "/" + year + "...", Toast.LENGTH_SHORT).show();
        api.exportAttendance(finalMonth, finalYear).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> c, Response<ResponseBody> r) {
                if (isFinishing() || isDestroyed()) return;
                if (!r.isSuccessful() || r.body() == null) {
                    if (r.code() != 401) Toast.makeText(AttendanceReportActivity.this, "Không xuất được file", Toast.LENGTH_SHORT).show();
                    return;
                }
                try {
                    byte[] bytes = r.body().bytes();
                    String name = String.format(Locale.US, "bang_cong_%02d_%d.csv", finalMonth, finalYear);
                    Uri uri;
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
                        uri = androidx.core.content.FileProvider.getUriForFile(AttendanceReportActivity.this, getApplicationContext().getPackageName() + ".fileprovider", targetFile);
                    }
                    promptOpenCsv(uri, name, bytes);
                } catch (Exception e) {
                    Toast.makeText(AttendanceReportActivity.this, "Không lưu được file: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> c, Throwable t) {
                if (!isFinishing()) Toast.makeText(AttendanceReportActivity.this, "Không xuất được file", Toast.LENGTH_SHORT).show();
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
                .setNeutralButton("Xem nhanh", (dialog, which) -> showCsvPreviewDialog("Xem trước " + name, bytes))
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

    private void applyWindowInsets() {
        View root = findViewById(R.id.reportRoot);
        int left = root.getPaddingLeft(), top = root.getPaddingTop();
        int right = root.getPaddingRight(), bottom = root.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            Insets safe = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout() | WindowInsetsCompat.Type.ime());
            view.setPadding(left + safe.left, top + safe.top, right + safe.right, bottom + safe.bottom);
            return insets;
        });
        boolean dark = (getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView())
                .setAppearanceLightStatusBars(!dark);
        ViewCompat.requestApplyInsets(root);
    }
}
