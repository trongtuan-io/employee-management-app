package com.example.employee_management_app;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class AdminAttendanceReportActivity extends AppCompatActivity {

    private EditText etSearchDate;
    private Button btnLoadReport, btnExportExcel;
    private RecyclerView recyclerViewReport;
    private AttendanceReportAdapter adapter;
    private TokenManager tokenManager;
    private AttendanceApi attendanceApi;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_attendance_report);

        // 1. Ánh xạ View
        etSearchDate = findViewById(R.id.etSearchDate);
        btnLoadReport = findViewById(R.id.btnLoadReport);
        btnExportExcel = findViewById(R.id.btnExportExcel);
        recyclerViewReport = findViewById(R.id.recyclerViewReport);

        tokenManager = TokenManager.getInstance(this);
        attendanceApi = ApiClient.getClient(this).create(AttendanceApi.class);

        recyclerViewReport.setLayoutManager(new LinearLayoutManager(this));
        adapter = new AttendanceReportAdapter(new ArrayList<>());
        recyclerViewReport.setAdapter(adapter);

        // 2. Tích hợp chọn ngày bằng Lịch (DatePickerDialog)
        etSearchDate.setFocusable(false);
        etSearchDate.setClickable(true);
        etSearchDate.setOnClickListener(v -> {
            Calendar calendar = Calendar.getInstance();
            int year = calendar.get(Calendar.YEAR);
            int month = calendar.get(Calendar.MONTH);
            int day = calendar.get(Calendar.DAY_OF_MONTH);

            DatePickerDialog datePickerDialog = new DatePickerDialog(
                    AdminAttendanceReportActivity.this,
                    (view, selectedYear, selectedMonth, selectedDay) -> {
                        String formattedDate = String.format(Locale.getDefault(), "%04d-%02d-%02d",
                                selectedYear, selectedMonth + 1, selectedDay);
                        etSearchDate.setText(formattedDate);
                        loadReport(formattedDate, "");
                    },
                    year, month, day
            );
            datePickerDialog.show();
        });

        // 3. Mặc định hiển thị báo cáo của ngày hiện tại
        String currentDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        etSearchDate.setText(currentDate);
        loadReport(currentDate, "");

        // 4. Bấm nút Xem Báo Cáo
        btnLoadReport.setOnClickListener(v -> {
            String date = etSearchDate.getText().toString().trim();
            loadReport(date, "");
        });

        // 5. Bấm nút Xuất Excel
        btnExportExcel.setOnClickListener(v -> {
            String date = etSearchDate.getText().toString().trim();
            if (date.isEmpty() || !date.contains("-")) {
                Toast.makeText(this, "Vui lòng chọn ngày hợp lệ để xuất báo cáo!", Toast.LENGTH_SHORT).show();
                return;
            }

            try {
                // Tách Tháng và Năm từ chuỗi YYYY-MM-DD
                String[] parts = date.split("-");
                String year = parts[0];
                String month = parts[1];

                // URL xuất file Excel từ Server PHP
                String exportUrl = ApiClient.getBaseUrl() + "attendances.php?action=export&month=" + month + "&year=" + year;

                // Mở trình duyệt để hệ thống tự động tải file CSV/Excel về thiết bị
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(exportUrl));
                startActivity(browserIntent);
            } catch (Exception e) {
                Toast.makeText(this, "Không thể mở liên kết xuất Excel: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadReport(String date, String dept) {
        String token = "Bearer " + tokenManager.getToken();
        attendanceApi.getAttendanceReport(token, date, dept).enqueue(new Callback<ApiResponse<List<AttendanceResponse>>>() {
            @Override
            public void onResponse(Call<ApiResponse<List<AttendanceResponse>>> call, Response<ApiResponse<List<AttendanceResponse>>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    ApiResponse<List<AttendanceResponse>> apiResponse = response.body();
                    if (apiResponse.isSuccess() && apiResponse.getData() != null) {
                        List<AttendanceResponse> data = apiResponse.getData();
                        if (!data.isEmpty()) {
                            adapter.updateData(data);
                        } else {
                            Toast.makeText(AdminAttendanceReportActivity.this, "Không có dữ liệu báo cáo trong ngày", Toast.LENGTH_SHORT).show();
                            adapter.updateData(new ArrayList<>());
                        }
                    } else {
                        Toast.makeText(AdminAttendanceReportActivity.this, apiResponse.getMessage() != null ? apiResponse.getMessage() : "Lỗi tải dữ liệu báo cáo", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(AdminAttendanceReportActivity.this, "Lỗi tải dữ liệu báo cáo", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<List<AttendanceResponse>>> call, Throwable t) {
                Toast.makeText(AdminAttendanceReportActivity.this, "Lỗi kết nối: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }
}