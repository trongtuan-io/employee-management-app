package com.example.employee_management_app;

import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

// Bang luong theo Role x Phong: nhan vien chi xem luong CUA MINH da chot;
// chi admin + phong KE TOAN moi co panel tinh luong TU DONG theo ngay cong
// (salary-generate), dieu chinh ban nhap (salary-adjust); rieng CHOT bang luong
// (salary-finalize) chi admin + KE TOAN TRUONG (manager phong ACC).
public class SalaryActivity extends AppCompatActivity {
    private final List<SalaryListResponse.Item> items = new ArrayList<>();
    private final List<EmployeeListResponse.Item> employees = new ArrayList<>();
    private ArrayAdapter<SalaryListResponse.Item> adapter;
    private EmployeeApi api;
    private int myId;
    private boolean canPayroll;
    private boolean canFinalize;
    private int selectedEmpId;
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
        boolean isAdmin = "admin".equalsIgnoreCase(role);
        boolean isAccounting = "ACC".equalsIgnoreCase(dept);
        canPayroll = isAdmin || isAccounting;
        canFinalize = isAdmin || (isAccounting && "manager".equalsIgnoreCase(role));
        selectedEmpId = myId;

        ListView lv = findViewById(R.id.lvSalaries);
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, items);
        lv.setAdapter(adapter);
        lv.setOnItemClickListener((parent, view, position, id) -> onSalaryClicked(items.get(position)));

        etMonth = findViewById(R.id.etSalaryMonth);
        etYear = findViewById(R.id.etSalaryYear);
        Calendar now = Calendar.getInstance();
        etMonth.setText(String.valueOf(now.get(Calendar.MONTH) + 1));
        etYear.setText(String.valueOf(now.get(Calendar.YEAR)));

        if (canPayroll) {
            findViewById(R.id.layoutSalaryAdmin).setVisibility(View.VISIBLE);
            findViewById(R.id.btnGenerateSalary).setOnClickListener(v -> generateSalaries());
            setupEmployeeSpinner();
        }

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
                    loadSalaries();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) { }
        });
        api.getEmployees("").enqueue(new Callback<EmployeeListResponse>() {
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

    private int viewYear() {
        if (canPayroll && etYear != null) {
            try {
                int y = Integer.parseInt(etYear.getText().toString().trim());
                if (y >= 2000 && y <= 2100) return y;
            } catch (NumberFormatException ignored) { }
        }
        return Calendar.getInstance().get(Calendar.YEAR);
    }

    private void loadSalaries() {
        api.getSalaries(selectedEmpId, viewYear()).enqueue(new Callback<SalaryListResponse>() {
            @Override
            public void onResponse(Call<SalaryListResponse> c, Response<SalaryListResponse> r) {
                if (r.isSuccessful() && r.body() != null && r.body().isSuccess()
                        && r.body().getData() != null) {
                    items.clear();
                    items.addAll(r.body().getData());
                    adapter.notifyDataSetChanged();
                    if (items.isEmpty()) {
                        Toast.makeText(SalaryActivity.this, "Chưa có bảng lương nào trong năm nay", Toast.LENGTH_SHORT).show();
                    }
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

    private static double moneyOf(String raw) {
        if (raw == null) return 0;
        try { return Double.parseDouble(raw); } catch (NumberFormatException e) { return 0; }
    }

    // Chap nhan ca dang "500000" lan dang VN "500.000"
    private static double parseMoney(EditText et) {
        String s = et.getText().toString().trim().replace(".", "").replace(",", "").replace(" ", "");
        if (s.isEmpty()) return 0;
        try { return Double.parseDouble(s); } catch (NumberFormatException e) { return -1; }
    }

    private EditText moneyInput(String label, double value) {
        EditText et = new EditText(this);
        et.setHint(label);
        et.setInputType(InputType.TYPE_CLASS_NUMBER);
        et.setText(String.valueOf(Math.round(value)));
        return et;
    }

    private void onSalaryClicked(SalaryListResponse.Item item) {
        if (!canPayroll) return; // Chi Ke toan/Admin moi sua bang luong
        if (item.isFinalized()) {
            Toast.makeText(this, "Bảng lương này đã chốt, không sửa được", Toast.LENGTH_SHORT).show();
            return;
        }
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(48, 24, 48, 8);
        EditText etAllowance = moneyInput("Phụ cấp", moneyOf(item.getAllowance()));
        EditText etOvertime = moneyInput("Tiền tăng ca", moneyOf(item.getOvertimePay()));
        EditText etBonus = moneyInput("Thưởng", moneyOf(item.getBonus()));
        EditText etDeduction = moneyInput("Khấu trừ", moneyOf(item.getDeduction()));
        box.addView(etAllowance);
        box.addView(etOvertime);
        box.addView(etBonus);
        box.addView(etDeduction);

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle("Bảng lương tháng " + item.getMonth() + "/" + item.getYear())
                .setView(box)
                .setPositiveButton("Lưu", (dialog, which) -> {
                    double allowance = parseMoney(etAllowance);
                    double overtime = parseMoney(etOvertime);
                    double bonus = parseMoney(etBonus);
                    double deduction = parseMoney(etDeduction);
                    if (allowance < 0 || overtime < 0 || bonus < 0 || deduction < 0) {
                        Toast.makeText(this, "Số tiền không hợp lệ", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    api.adjustSalary(new SalaryAdjustRequest(item.getId(), allowance, overtime, bonus, deduction))
                            .enqueue(new Callback<ApiResponse>() {
                                @Override
                                public void onResponse(Call<ApiResponse> c, Response<ApiResponse> r) {
                                    if (r.isSuccessful() && r.body() != null && r.body().isSuccess()) {
                                        Toast.makeText(SalaryActivity.this, "Đã lưu, thực lĩnh đã tính lại", Toast.LENGTH_SHORT).show();
                                        loadSalaries();
                                    } else if (r.code() != 401) {
                                        Toast.makeText(SalaryActivity.this,
                                                ApiError.message(r, "Không lưu được"), Toast.LENGTH_SHORT).show();
                                    }
                                }

                                @Override
                                public void onFailure(Call<ApiResponse> c, Throwable t) {
                                    Toast.makeText(SalaryActivity.this, "Không lưu được", Toast.LENGTH_SHORT).show();
                                }
                            });
                })
                .setNegativeButton("Đóng", null);
        // Chi Ke toan truong/Admin moi thay nut Chot (NV Ke toan chi soan nhap).
        if (canFinalize) {
            builder.setNeutralButton("Chốt bảng lương", (dialog, which) -> confirmFinalize(item));
        }
        builder.show();
    }

    private void confirmFinalize(SalaryListResponse.Item item) {
        new AlertDialog.Builder(this)
                .setTitle("Chốt bảng lương?")
                .setMessage("Sau khi chốt, bảng lương tháng " + item.getMonth() + "/" + item.getYear()
                        + " sẽ bị khoá: không sửa thưởng/phụ cấp và không bị tính lại khi chạy tính lương tự động.")
                .setPositiveButton("Chốt", (dialog, which) -> api.finalizeSalary(new SalaryFinalizeRequest(item.getId()))
                        .enqueue(new Callback<ApiResponse>() {
                            @Override
                            public void onResponse(Call<ApiResponse> c, Response<ApiResponse> r) {
                                if (r.isSuccessful() && r.body() != null && r.body().isSuccess()) {
                                    Toast.makeText(SalaryActivity.this, "Đã chốt bảng lương", Toast.LENGTH_SHORT).show();
                                    loadSalaries();
                                } else if (r.code() != 401) {
                                    Toast.makeText(SalaryActivity.this,
                                            ApiError.message(r, "Không chốt được"), Toast.LENGTH_SHORT).show();
                                }
                            }

                            @Override
                            public void onFailure(Call<ApiResponse> c, Throwable t) {
                                Toast.makeText(SalaryActivity.this, "Không chốt được", Toast.LENGTH_SHORT).show();
                            }
                        }))
                .setNegativeButton("Huỷ", null)
                .show();
    }
}
