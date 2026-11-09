package com.example.employee_management_app;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

// Bang luong: danh sach cac thang trong nam (moi dong la mot thang), cham vao
// de xem CHI TIET thang do (SalaryDetailActivity).
// Phan quyen (Tuan chot 2026-11-09): nhan vien cac phong khac chi xem duoc
// bang luong CUA CHINH MINH (API chi tra thang da chot). Phong KE TOAN (ca NV
// lan truong phong) co khu quan ly de SOAN NHAP: xem luong nhan vien khac,
// tinh luong tu dong, sua cac khoan; rieng CHOT chi Ke toan truong + Admin.
public class SalaryActivity extends AppCompatActivity {
    private final List<SalaryListResponse.Item> items = new ArrayList<>();
    private final List<EmployeeListResponse.Item> employees = new ArrayList<>();
    private SalaryAdapter adapter;
    private EmployeeApi api;
    private int myId;
    private boolean isAdmin;
    private boolean canManage;
    private boolean canFinalize;
    private int selectedEmpId;
    private int selectedYear;
    private TextView tvSalaryYear, tvSalaryOwner, tvSalaryEmpty;
    private ListView lvSalaries;
    private EditText etMonth, etYear;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_salary);
        BottomNavHelper.setup(this, R.id.nav_salary);

        api = ApiClient.getClient(this).create(EmployeeApi.class);
        myId = TokenManager.getInstance(this).getUserId();
        String role = TokenManager.getInstance(this).getRole();
        String dept = TokenManager.getInstance(this).getDepartmentName();
        isAdmin = "admin".equalsIgnoreCase(role);
        boolean isAccounting = "ACC".equalsIgnoreCase(dept);
        canManage = isAdmin || isAccounting;
        canFinalize = isAdmin || (isAccounting && "manager".equalsIgnoreCase(role));
        selectedEmpId = myId;
        selectedYear = Calendar.getInstance().get(Calendar.YEAR);

        lvSalaries = findViewById(R.id.lvSalaries);
        adapter = new SalaryAdapter(this, items);
        lvSalaries.setAdapter(adapter);
        lvSalaries.setOnItemClickListener((parent, view, position, id) -> openDetail(items.get(position)));

        tvSalaryYear = findViewById(R.id.tvSalaryYear);
        tvSalaryOwner = findViewById(R.id.tvSalaryOwner);
        tvSalaryEmpty = findViewById(R.id.tvSalaryEmpty);
        tvSalaryYear.setText("Năm " + selectedYear);
        findViewById(R.id.btnPrevYear).setOnClickListener(v -> changeYear(-1));
        findViewById(R.id.btnNextYear).setOnClickListener(v -> changeYear(1));

        etMonth = findViewById(R.id.etSalaryMonth);
        etYear = findViewById(R.id.etSalaryYear);
        Calendar now = Calendar.getInstance();
        etMonth.setText(String.valueOf(now.get(Calendar.MONTH) + 1));
        etYear.setText(String.valueOf(selectedYear));

        if (canManage) {
            findViewById(R.id.layoutSalaryAdmin).setVisibility(View.VISIBLE);
            findViewById(R.id.btnGenerateSalary).setOnClickListener(v -> generateSalaries());
            findViewById(R.id.btnSalaryOverview).setOnClickListener(v -> {
                Intent oi = new Intent(this, SalaryOverviewActivity.class);
                try {
                    oi.putExtra("month", Integer.parseInt(etMonth.getText().toString().trim()));
                    oi.putExtra("year", Integer.parseInt(etYear.getText().toString().trim()));
                } catch (NumberFormatException ignored) { }
                startActivity(oi);
            });
            setupEmployeeSpinner();
        }

        loadSalaries();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Quay lai tu man chi tiet (sau khi sua/chot) thi nap lai danh sach.
        if (adapter != null) loadSalaries();
    }

    private void changeYear(int delta) {
        int y = selectedYear + delta;
        if (y < 2000 || y > 2100) return;
        selectedYear = y;
        tvSalaryYear.setText("Năm " + selectedYear);
        if (etYear != null) etYear.setText(String.valueOf(selectedYear));
        loadSalaries();
    }

    private void setupEmployeeSpinner() {
        Spinner sp = findViewById(R.id.spSalaryEmployee);
        ArrayAdapter<EmployeeListResponse.Item> spAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_dropdown_item, employees);
        sp.setAdapter(spAdapter);
        sp.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position >= 0 && position < employees.size()) {
                    selectedEmpId = employees.get(position).getId();
                    updateOwnerLabel();
                    loadSalaries();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) { }
        });
        api.getDirectory().enqueue(new Callback<EmployeeListResponse>() {
            @Override
            public void onResponse(Call<EmployeeListResponse> c, Response<EmployeeListResponse> r) {
                if (r.isSuccessful() && r.body() != null && r.body().isSuccess() && r.body().getData() != null) {
                    employees.clear();
                    employees.addAll(r.body().getData());
                    spAdapter.notifyDataSetChanged();
                    // Mac dinh chon chinh minh neu co trong danh sach.
                    for (int i = 0; i < employees.size(); i++) {
                        if (employees.get(i).getId() == myId) { sp.setSelection(i); break; }
                    }
                }
            }

            @Override
            public void onFailure(Call<EmployeeListResponse> c, Throwable t) { }
        });
    }

    private void updateOwnerLabel() {
        if (selectedEmpId == myId) {
            tvSalaryOwner.setText("Bảng lương của tôi");
            return;
        }
        for (EmployeeListResponse.Item e : employees) {
            if (e.getId() == selectedEmpId) {
                tvSalaryOwner.setText("Bảng lương của: " + e.getFullName());
                return;
            }
        }
        tvSalaryOwner.setText("Bảng lương của nhân viên");
    }

    private void loadSalaries() {
        api.getSalaries(selectedEmpId, selectedYear).enqueue(new Callback<SalaryListResponse>() {
            @Override
            public void onResponse(Call<SalaryListResponse> c, Response<SalaryListResponse> r) {
                if (r.isSuccessful() && r.body() != null && r.body().isSuccess()
                        && r.body().getData() != null) {
                    items.clear();
                    items.addAll(r.body().getData());
                    adapter.notifyDataSetChanged();
                    tvSalaryEmpty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
                } else if (r.code() != 401) {
                    Toast.makeText(SalaryActivity.this,
                            ApiError.message(r, "Không tải được bảng lương"), Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<SalaryListResponse> c, Throwable t) {
                Toast.makeText(SalaryActivity.this, "Không tải được bảng lương", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void openDetail(SalaryListResponse.Item item) {
        Intent i = new Intent(this, SalaryDetailActivity.class);
        String p = SalaryDetailActivity.EXTRA_PREFIX;
        i.putExtra(p + "id", item.getId());
        i.putExtra(p + "employee_id", item.getEmployeeId());
        i.putExtra(p + "full_name", item.getFullName());
        i.putExtra(p + "month", item.getMonth());
        i.putExtra(p + "year", item.getYear());
        i.putExtra(p + "base_salary", item.getBaseSalary());
        i.putExtra(p + "full_base_salary", item.getFullBaseSalary());
        i.putExtra(p + "allowance", item.getAllowance());
        i.putExtra(p + "overtime_pay", item.getOvertimePay());
        i.putExtra(p + "bonus", item.getBonus());
        i.putExtra(p + "deduction", item.getDeduction());
        i.putExtra(p + "net_salary", item.getNetSalary());
        i.putExtra(p + "work_days", item.getWorkDays());
        i.putExtra(p + "standard_days", item.getStandardDays());
        i.putExtra(p + "finalized", item.isFinalized());
        i.putExtra(p + "can_manage", canManage);
        i.putExtra(p + "can_finalize", canFinalize);
        i.putExtra(p + "is_admin", isAdmin);
        startActivity(i);
    }

    private void generateSalaries() {
        int month, year;
        try {
            month = Integer.parseInt(etMonth.getText().toString().trim());
            year = Integer.parseInt(etYear.getText().toString().trim());
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Nhập tháng và năm hợp lệ", Toast.LENGTH_SHORT).show();
            return;
        }
        if (month < 1 || month > 12 || year < 2000 || year > 2100) {
            Toast.makeText(this, "Tháng phải từ 1-12, năm hợp lệ", Toast.LENGTH_SHORT).show();
            return;
        }
        api.generateSalaries(new SalaryGenerateRequest(month, year)).enqueue(new Callback<SalaryGenerateResponse>() {
            @Override
            public void onResponse(Call<SalaryGenerateResponse> c, Response<SalaryGenerateResponse> r) {
                if (r.isSuccessful() && r.body() != null && r.body().isSuccess() && r.body().getData() != null) {
                    SalaryGenerateResponse.Data d = r.body().getData();
                    String msg = "Đã tính lương tháng " + d.getMonth() + "/" + d.getYear()
                            + " cho " + d.getGenerated() + " nhân viên (ngày công chuẩn: "
                            + d.getStandardDays() + ")";
                    if (d.getSkippedFinalized() > 0) {
                        msg += ". Bỏ qua " + d.getSkippedFinalized() + " bảng lương đã chốt";
                    }
                    Toast.makeText(SalaryActivity.this, msg, Toast.LENGTH_LONG).show();
                    if (year != selectedYear) {
                        selectedYear = year;
                        tvSalaryYear.setText("Năm " + selectedYear);
                    }
                    loadSalaries();
                } else if (r.code() != 401) {
                    Toast.makeText(SalaryActivity.this,
                            ApiError.message(r, "Không tính được lương"), Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<SalaryGenerateResponse> c, Throwable t) {
                Toast.makeText(SalaryActivity.this, "Không tính được lương", Toast.LENGTH_SHORT).show();
            }
        });
    }
}
