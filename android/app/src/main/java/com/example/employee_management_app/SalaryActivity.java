package com.example.employee_management_app;

import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

// Xem luong cua toi (moi vai tro) + Tao bang luong (admin/manager).
public class SalaryActivity extends AppCompatActivity {
    private final List<SalaryListResponse.Item> items = new ArrayList<>();
    private ArrayAdapter<SalaryListResponse.Item> adapter;
    private EmployeeApi api;
    private int myId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_salary);
        BottomNavHelper.setup(this, R.id.nav_salary);

        api = ApiClient.getClient(this).create(EmployeeApi.class);
        myId = TokenManager.getInstance(this).getUserId();
        String role = TokenManager.getInstance(this).getRole();
        boolean isManager = "admin".equalsIgnoreCase(role) || "manager".equalsIgnoreCase(role);

        ListView lv = findViewById(R.id.lvSalaries);
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, items);
        lv.setAdapter(adapter);

        View form = findViewById(R.id.layoutSalaryForm);
        form.setVisibility(isManager ? View.VISIBLE : View.GONE);
        if (isManager) {
            EditText edtEmp = findViewById(R.id.edtSalEmpId);
            EditText edtMonth = findViewById(R.id.edtSalMonth);
            EditText edtYear = findViewById(R.id.edtSalYear);
            EditText edtBase = findViewById(R.id.edtSalBase);
            EditText edtAllowance = findViewById(R.id.edtSalAllowance);
            EditText edtOvertime = findViewById(R.id.edtSalOvertime);
            EditText edtBonus = findViewById(R.id.edtSalBonus);
            EditText edtDeduction = findViewById(R.id.edtSalDeduction);
            Button btnCreate = findViewById(R.id.btnCreateSalary);
            edtYear.setText(String.valueOf(Calendar.getInstance().get(Calendar.YEAR)));
            btnCreate.setOnClickListener(v -> {
                try {
                    int empId = Integer.parseInt(edtEmp.getText().toString().trim());
                    int month = Integer.parseInt(edtMonth.getText().toString().trim());
                    int year = Integer.parseInt(edtYear.getText().toString().trim());
                    if (empId <= 0) { edtEmp.setError("Mã nhân viên không hợp lệ"); return; }
                    if (month < 1 || month > 12) { edtMonth.setError("Tháng phải từ 1 đến 12"); return; }
                    if (year < 2000 || year > 2100) { edtYear.setError("Năm không hợp lệ"); return; }
                    Double base = parseMoneyStrict(edtBase);
                    Double allowance = parseMoneyStrict(edtAllowance);
                    Double overtime = parseMoneyStrict(edtOvertime);
                    Double bonus = parseMoneyStrict(edtBonus);
                    Double deduction = parseMoneyStrict(edtDeduction);
                    if (base == null || allowance == null || overtime == null || bonus == null || deduction == null) {
                        return; // truong sai da duoc setError tai cho
                    }
                    SalaryCreateRequest req = new SalaryCreateRequest(empId, month, year,
                            base, allowance, overtime, bonus, deduction);
                    btnCreate.setEnabled(false);
                    api.createSalary(req).enqueue(new Callback<ApiResponse>() {
                        @Override
                        public void onResponse(Call<ApiResponse> c, Response<ApiResponse> r) {
                            btnCreate.setEnabled(true);
                            if (r.isSuccessful() && r.body() != null && r.body().isSuccess()) {
                                Toast.makeText(SalaryActivity.this,
                                        "Đã lưu bảng lương tháng " + month + "/" + year + " cho NV #" + empId,
                                        Toast.LENGTH_SHORT).show();
                                // Danh sach duoi la luong cua CHINH MINH: chi nap lai khi vua tao cho minh.
                                if (empId == myId) loadSalaries();
                            } else {
                                Toast.makeText(SalaryActivity.this,
                                        ApiError.message(r, "Lưu bảng lương thất bại"), Toast.LENGTH_LONG).show();
                            }
                        }

                        @Override
                        public void onFailure(Call<ApiResponse> c, Throwable t) {
                            btnCreate.setEnabled(true);
                            Toast.makeText(SalaryActivity.this, "Lỗi kết nối: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    });
                } catch (NumberFormatException e) {
                    Toast.makeText(this, "Nhập mã NV, tháng, năm dạng số", Toast.LENGTH_SHORT).show();
                }
            });
        }

        loadSalaries();
    }

    // Doc so tien kieu Viet Nam: "5.000.000" = 5000000, "5000000", "10,5" = 10.5.
    // Sai dinh dang thi bao loi tai truong va tra null (truoc day nuot loi thanh 0 am tham).
    private Double parseMoneyStrict(EditText edt) {
        String s = edt.getText().toString().trim().replace(" ", "");
        if (s.isEmpty()) return 0.0;
        try {
            double v;
            if (s.matches("\\d{1,3}(\\.\\d{3})+")) {
                v = Double.parseDouble(s.replace(".", "")); // dau cham la phan cach nghin
            } else {
                v = Double.parseDouble(s.replace(",", "."));
            }
            if (v < 0) throw new NumberFormatException("negative");
            return v;
        } catch (NumberFormatException e) {
            edt.setError("Số tiền không hợp lệ (ví dụ đúng: 5.000.000)");
            edt.requestFocus();
            return null;
        }
    }

    private void loadSalaries() {
        int year = Calendar.getInstance().get(Calendar.YEAR);
        api.getSalaries(myId, year).enqueue(new Callback<SalaryListResponse>() {
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
}
