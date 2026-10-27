package com.example.employee_management_app;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
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
    private Spinner spReportType, spDepartment, spRole;
    private Button btnLoadReport, btnExportExcel;
    private RecyclerView recyclerViewReport;
    private AttendanceReportAdapter adapter;
    private TokenManager tokenManager;
    private AttendanceApi attendanceApi;
    private EmployeeApi employeeApi;

    private boolean isMonthlyMode = false; // false = Theo ngày, true = Theo tháng

    private final List<String> deptQueryValues = new ArrayList<>();
    private final List<String> deptDisplayNames = new ArrayList<>();
    private ArrayAdapter<String> deptSpinnerAdapter;
    private String currentSelectedDept = "";

    private final List<String> roleQueryValues = new ArrayList<>();
    private final List<String> roleDisplayNames = new ArrayList<>();
    private ArrayAdapter<String> roleSpinnerAdapter;
    private String currentSelectedRole = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_attendance_report);

        // 1. Ánh xạ View
        spReportType = findViewById(R.id.spReportType);
        etSearchDate = findViewById(R.id.etSearchDate);
        spDepartment = findViewById(R.id.spDepartment);
        spRole = findViewById(R.id.spRole);
        btnLoadReport = findViewById(R.id.btnLoadReport);
        btnExportExcel = findViewById(R.id.btnExportExcel);
        recyclerViewReport = findViewById(R.id.recyclerViewReport);

        tokenManager = TokenManager.getInstance(this);
        attendanceApi = ApiClient.getClient(this).create(AttendanceApi.class);
        employeeApi = ApiClient.getClient(this).create(EmployeeApi.class);

        recyclerViewReport.setLayoutManager(new LinearLayoutManager(this));
        adapter = new AttendanceReportAdapter(new ArrayList<>());
        recyclerViewReport.setAdapter(adapter);

        // Spinner Loại Báo Cáo (Theo ngày / Theo tháng)
        List<String> reportTypeNames = new ArrayList<>();
        reportTypeNames.add("Theo ngày");
        reportTypeNames.add("Theo tháng");
        ArrayAdapter<String> reportTypeAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, reportTypeNames);
        reportTypeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spReportType.setAdapter(reportTypeAdapter);

        // Spinner Adapter cho danh sách Phòng ban
        deptSpinnerAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, deptDisplayNames);
        deptSpinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spDepartment.setAdapter(deptSpinnerAdapter);

        // Spinner Adapter cho Cấp bậc / Vai trò (Tất cả, Nhân viên, Sếp)
        setupRoleFilter();
        roleSpinnerAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, roleDisplayNames);
        roleSpinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spRole.setAdapter(roleSpinnerAdapter);

        // 2. Tích hợp chọn ngày/tháng bằng Lịch (DatePickerDialog)
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
                        if (isMonthlyMode) {
                            String formattedMonth = String.format(Locale.getDefault(), "%04d-%02d",
                                    selectedYear, selectedMonth + 1);
                            etSearchDate.setText(formattedMonth);
                        } else {
                            String formattedDate = String.format(Locale.getDefault(), "%04d-%02d-%02d",
                                    selectedYear, selectedMonth + 1, selectedDay);
                            etSearchDate.setText(formattedDate);
                        }
                        loadReport(etSearchDate.getText().toString().trim(), currentSelectedDept, currentSelectedRole);
                    },
                    year, month, day
            );
            datePickerDialog.show();
        });

        // 3. Sự kiện chuyển đổi Loại Báo Cáo (Theo ngày / Theo tháng)
        spReportType.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                isMonthlyMode = (position == 1);
                if (isMonthlyMode) {
                    etSearchDate.setHint("Chọn tháng (YYYY-MM)");
                    String currentMonth = new SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(new Date());
                    etSearchDate.setText(currentMonth);
                } else {
                    etSearchDate.setHint("Chọn ngày (YYYY-MM-DD)");
                    String currentDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
                    etSearchDate.setText(currentDate);
                }
                loadReport(etSearchDate.getText().toString().trim(), currentSelectedDept, currentSelectedRole);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) { }
        });

        // 4. Khởi tạo danh sách phòng ban theo Phân quyền (Admin vs Manager/Sếp)
        setupDepartmentFilter();

        // 5. Sự kiện khi thay đổi Spinner Phòng ban
        spDepartment.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position >= 0 && position < deptQueryValues.size()) {
                    currentSelectedDept = deptQueryValues.get(position);
                    String date = etSearchDate.getText().toString().trim();
                    loadReport(date, currentSelectedDept, currentSelectedRole);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) { }
        });

        // 6. Sự kiện khi thay đổi Spinner Cấp bậc
        spRole.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position >= 0 && position < roleQueryValues.size()) {
                    currentSelectedRole = roleQueryValues.get(position);
                    String date = etSearchDate.getText().toString().trim();
                    loadReport(date, currentSelectedDept, currentSelectedRole);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) { }
        });

        // 7. Bấm nút Xem Báo Cáo
        btnLoadReport.setOnClickListener(v -> {
            String date = etSearchDate.getText().toString().trim();
            loadReport(date, currentSelectedDept, currentSelectedRole);
        });

        // 8. Bấm nút Xuất Excel
        btnExportExcel.setOnClickListener(v -> {
            String dateOrMonthStr = etSearchDate.getText().toString().trim();
            if (dateOrMonthStr.isEmpty() || !dateOrMonthStr.contains("-")) {
                Toast.makeText(this, "Vui lòng chọn thời gian hợp lệ để xuất báo cáo!", Toast.LENGTH_SHORT).show();
                return;
            }

            try {
                String exportUrl;
                if (isMonthlyMode) {
                    String[] parts = dateOrMonthStr.split("-");
                    String year = parts[0];
                    String month = parts[1];
                    exportUrl = ApiClient.getBaseUrl() + "attendances.php?action=export&month=" + month + "&year=" + year;
                } else {
                    exportUrl = ApiClient.getBaseUrl() + "attendances.php?action=export&date=" + Uri.encode(dateOrMonthStr);
                }

                if (currentSelectedDept != null && !currentSelectedDept.isEmpty()) {
                    exportUrl += "&dept=" + Uri.encode(currentSelectedDept);
                }
                if (currentSelectedRole != null && !currentSelectedRole.isEmpty()) {
                    exportUrl += "&role=" + Uri.encode(currentSelectedRole);
                }

                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(exportUrl));
                startActivity(browserIntent);
            } catch (Exception e) {
                Toast.makeText(this, "Không thể mở liên kết xuất Excel: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setupRoleFilter() {
        roleDisplayNames.clear();
        roleQueryValues.clear();

        roleDisplayNames.add("Tất cả cấp bậc");
        roleQueryValues.add("");

        roleDisplayNames.add("Nhân viên");
        roleQueryValues.add("staff");

        roleDisplayNames.add("Sếp / Trưởng phòng");
        roleQueryValues.add("manager");
    }

    private void setupDepartmentFilter() {
        String role = tokenManager.getRole();
        if ("admin".equalsIgnoreCase(role)) {
            // Admin: Xem được tất cả phòng ban
            spDepartment.setEnabled(true);
            deptDisplayNames.clear();
            deptQueryValues.clear();

            deptDisplayNames.add("Tất cả phòng ban");
            deptQueryValues.add("");

            employeeApi.getDepartments().enqueue(new Callback<DepartmentListResponse>() {
                @Override
                public void onResponse(Call<DepartmentListResponse> call, Response<DepartmentListResponse> response) {
                    if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                        List<DepartmentListResponse.Item> items = response.body().getData();
                        if (items != null) {
                            for (DepartmentListResponse.Item item : items) {
                                deptDisplayNames.add(item.getName());
                                deptQueryValues.add(String.valueOf(item.getId()));
                            }
                        }
                    }
                    deptSpinnerAdapter.notifyDataSetChanged();
                }

                @Override
                public void onFailure(Call<DepartmentListResponse> call, Throwable t) {
                    deptSpinnerAdapter.notifyDataSetChanged();
                }
            });
        } else {
            // Sếp (Manager): Chỉ xem được nhân viên phòng ban của mình
            spDepartment.setEnabled(false); // Khóa không cho chọn phòng khác
            deptDisplayNames.clear();
            deptQueryValues.clear();

            int userId = tokenManager.getUserId();
            employeeApi.getDetail(userId).enqueue(new Callback<EmployeeDetailResponse>() {
                @Override
                public void onResponse(Call<EmployeeDetailResponse> call, Response<EmployeeDetailResponse> response) {
                    if (response.isSuccessful() && response.body() != null && response.body().isSuccess() && response.body().getData() != null) {
                        EmployeeDetailResponse.Detail detail = response.body().getData();
                        String managerDeptName = detail.getDeptName() != null ? detail.getDeptName() : "Phòng ban của tôi";
                        String managerDeptId = String.valueOf(detail.getDepartmentId());

                        deptDisplayNames.add(managerDeptName);
                        deptQueryValues.add(managerDeptId);
                        currentSelectedDept = managerDeptId;

                        deptSpinnerAdapter.notifyDataSetChanged();
                        loadReport(etSearchDate.getText().toString().trim(), currentSelectedDept, currentSelectedRole);
                    } else {
                        deptDisplayNames.add("Phòng ban của tôi");
                        deptQueryValues.add("");
                        deptSpinnerAdapter.notifyDataSetChanged();
                    }
                }

                @Override
                public void onFailure(Call<EmployeeDetailResponse> call, Throwable t) {
                    deptDisplayNames.add("Phòng ban của tôi");
                    deptQueryValues.add("");
                    deptSpinnerAdapter.notifyDataSetChanged();
                }
            });
        }
    }

    private void loadReport(String dateOrMonthStr, String dept, String role) {
        String token = "Bearer " + tokenManager.getToken();

        Callback<ApiResponse<List<AttendanceResponse>>> callback = new Callback<ApiResponse<List<AttendanceResponse>>>() {
            @Override
            public void onResponse(Call<ApiResponse<List<AttendanceResponse>>> call, Response<ApiResponse<List<AttendanceResponse>>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    ApiResponse<List<AttendanceResponse>> apiResponse = response.body();
                    if (apiResponse.isSuccess() && apiResponse.getData() != null) {
                        List<AttendanceResponse> data = apiResponse.getData();
                        if (!data.isEmpty()) {
                            adapter.updateData(data);
                        } else {
                            Toast.makeText(AdminAttendanceReportActivity.this, "Không có dữ liệu báo cáo trong khoảng thời gian này", Toast.LENGTH_SHORT).show();
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
        };

        if (isMonthlyMode) {
            if (dateOrMonthStr != null && dateOrMonthStr.contains("-")) {
                String[] parts = dateOrMonthStr.split("-");
                String year = parts[0];
                String month = parts[1];
                attendanceApi.getAttendanceMonthlyReport(token, month, year, dept, role).enqueue(callback);
            } else {
                Toast.makeText(this, "Vui lòng chọn tháng hợp lệ (YYYY-MM)", Toast.LENGTH_SHORT).show();
            }
        } else {
            attendanceApi.getAttendanceReport(token, dateOrMonthStr, dept, role).enqueue(callback);
        }
    }
}
