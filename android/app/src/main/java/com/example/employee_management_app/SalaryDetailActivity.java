package com.example.employee_management_app;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

// Chi tiet bang luong MOT thang: luong co ban goc, ngay cong, luong theo ngay cong,
// phu cap/tang ca/thuong/khau tru va THUC LINH. Nhan vien chi mo duoc chi tiet
// cua chinh minh (danh sach da loc san); Admin/Ke toan truong co them nut
// Sua cac khoan + Chot (ban nhap) va Mo lai (da chot, chi Admin).
public class SalaryDetailActivity extends AppCompatActivity {
    public static final String EXTRA_PREFIX = "salary_";

    private EmployeeApi api;
    private int salaryId;
    private int employeeId;
    private int year;
    private boolean canManage;
    private boolean isAdmin;
    private Intent currentExtras;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_salary_detail);
        api = ApiClient.getClient(this).create(EmployeeApi.class);

        Intent i = getIntent();
        salaryId = i.getIntExtra(EXTRA_PREFIX + "id", 0);
        employeeId = i.getIntExtra(EXTRA_PREFIX + "employee_id", 0);
        year = i.getIntExtra(EXTRA_PREFIX + "year", 0);
        canManage = i.getBooleanExtra(EXTRA_PREFIX + "can_manage", false);
        isAdmin = i.getBooleanExtra(EXTRA_PREFIX + "is_admin", false);

        // Hien thi truc tiep tu extras; cac thao tac Sua/Chot xong se tai lai tu API.
        showFromExtras(i);

        findViewById(R.id.btnBackDetail).setOnClickListener(v -> finish());
        findViewById(R.id.btnAdjustSalary).setOnClickListener(v -> showAdjustDialog());
        findViewById(R.id.btnFinalizeSalary).setOnClickListener(v -> confirmFinalize());
        findViewById(R.id.btnUnfinalizeSalary).setOnClickListener(v -> confirmUnfinalize());
    }

    private void showFromExtras(Intent i) {
        String fullName = i.getStringExtra(EXTRA_PREFIX + "full_name");
        int month = i.getIntExtra(EXTRA_PREFIX + "month", 0);
        boolean finalized = i.getBooleanExtra(EXTRA_PREFIX + "finalized", false);
        ((TextView) findViewById(R.id.tvDetailTitle)).setText("Bảng lương tháng " + month + "/" + year);
        ((TextView) findViewById(R.id.tvDetailName)).setText(
                fullName == null || fullName.isEmpty() ? "" : "Nhân viên: " + fullName);
        setBadge(finalized);
        ((TextView) findViewById(R.id.tvDtlFullBase)).setText(
                SalaryListResponse.Item.money(i.getStringExtra(EXTRA_PREFIX + "full_base_salary")));
        String wd = i.getStringExtra(EXTRA_PREFIX + "work_days");
        String sd = i.getStringExtra(EXTRA_PREFIX + "standard_days");
        ((TextView) findViewById(R.id.tvDtlWorkDays)).setText(
                (wd == null ? "" : SalaryListResponse.Item.daysText(wd)) + "/"
                        + (sd == null ? "" : SalaryListResponse.Item.daysText(sd)) + " ngày");
        ((TextView) findViewById(R.id.tvDtlEarned)).setText(
                SalaryListResponse.Item.money(i.getStringExtra(EXTRA_PREFIX + "base_salary")));
        ((TextView) findViewById(R.id.tvDtlAllowance)).setText(
                SalaryListResponse.Item.money(i.getStringExtra(EXTRA_PREFIX + "allowance")));
        ((TextView) findViewById(R.id.tvDtlOvertime)).setText(
                SalaryListResponse.Item.money(i.getStringExtra(EXTRA_PREFIX + "overtime_pay")));
        ((TextView) findViewById(R.id.tvDtlBonus)).setText(
                SalaryListResponse.Item.money(i.getStringExtra(EXTRA_PREFIX + "bonus")));
        String ded = i.getStringExtra(EXTRA_PREFIX + "deduction");
        ((TextView) findViewById(R.id.tvDtlDeduction)).setText(
                (SalaryListResponse.Item.num(ded) > 0 ? "− " : "") + SalaryListResponse.Item.money(ded));
        ((TextView) findViewById(R.id.tvDtlNet)).setText(
                SalaryListResponse.Item.money(i.getStringExtra(EXTRA_PREFIX + "net_salary")));
        updateActions(finalized);
        // Giu lai cac khoan hien tai de dialog Sua dung gia tri dung.
        currentExtras = i;
    }

    private void setBadge(boolean finalized) {
        TextView badge = findViewById(R.id.tvDetailBadge);
        if (finalized) {
            badge.setText("Đã chốt");
            badge.setBackgroundResource(R.drawable.bg_badge_approved);
            badge.setTextColor(ContextCompat.getColor(this, R.color.success));
        } else {
            badge.setText("Bản nháp — chờ Kế toán chốt");
            badge.setBackgroundResource(R.drawable.bg_badge_pending);
            badge.setTextColor(ContextCompat.getColor(this, R.color.warning));
        }
    }

    private void updateActions(boolean finalized) {
        View actions = findViewById(R.id.layoutDetailActions);
        if (!canManage) {
            actions.setVisibility(View.GONE);
            return;
        }
        actions.setVisibility(View.VISIBLE);
        findViewById(R.id.btnAdjustSalary).setVisibility(finalized ? View.GONE : View.VISIBLE);
        findViewById(R.id.btnFinalizeSalary).setVisibility(finalized ? View.GONE : View.VISIBLE);
        findViewById(R.id.btnUnfinalizeSalary).setVisibility(finalized && isAdmin ? View.VISIBLE : View.GONE);
    }

    private void reload() {
        api.getSalaries(employeeId, year).enqueue(new Callback<SalaryListResponse>() {
            @Override
            public void onResponse(Call<SalaryListResponse> c, Response<SalaryListResponse> r) {
                if (r.isSuccessful() && r.body() != null && r.body().isSuccess() && r.body().getData() != null) {
                    for (SalaryListResponse.Item it : r.body().getData()) {
                        if (it.getId() == salaryId) {
                            Intent ni = new Intent();
                            ni.putExtra(EXTRA_PREFIX + "full_name", it.getFullName());
                            ni.putExtra(EXTRA_PREFIX + "month", it.getMonth());
                            ni.putExtra(EXTRA_PREFIX + "finalized", it.isFinalized());
                            ni.putExtra(EXTRA_PREFIX + "full_base_salary", it.getFullBaseSalary());
                            ni.putExtra(EXTRA_PREFIX + "work_days", it.getWorkDays());
                            ni.putExtra(EXTRA_PREFIX + "standard_days", it.getStandardDays());
                            ni.putExtra(EXTRA_PREFIX + "base_salary", it.getBaseSalary());
                            ni.putExtra(EXTRA_PREFIX + "allowance", it.getAllowance());
                            ni.putExtra(EXTRA_PREFIX + "overtime_pay", it.getOvertimePay());
                            ni.putExtra(EXTRA_PREFIX + "bonus", it.getBonus());
                            ni.putExtra(EXTRA_PREFIX + "deduction", it.getDeduction());
                            ni.putExtra(EXTRA_PREFIX + "net_salary", it.getNetSalary());
                            showFromExtras(ni);
                            break;
                        }
                    }
                }
            }

            @Override
            public void onFailure(Call<SalaryListResponse> c, Throwable t) { }
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

    private void showAdjustDialog() {
        if (currentExtras == null) return;
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(48, 24, 48, 8);
        EditText etAllowance = moneyInput("Phụ cấp", moneyOf(currentExtras.getStringExtra(EXTRA_PREFIX + "allowance")));
        EditText etOvertime = moneyInput("Tiền tăng ca", moneyOf(currentExtras.getStringExtra(EXTRA_PREFIX + "overtime_pay")));
        EditText etBonus = moneyInput("Thưởng", moneyOf(currentExtras.getStringExtra(EXTRA_PREFIX + "bonus")));
        EditText etDeduction = moneyInput("Khấu trừ", moneyOf(currentExtras.getStringExtra(EXTRA_PREFIX + "deduction")));
        box.addView(etAllowance);
        box.addView(etOvertime);
        box.addView(etBonus);
        box.addView(etDeduction);

        new AlertDialog.Builder(this)
                .setTitle("Sửa các khoản cộng/trừ")
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
                    api.adjustSalary(new SalaryAdjustRequest(salaryId, allowance, overtime, bonus, deduction))
                            .enqueue(new Callback<ApiResponse>() {
                                @Override
                                public void onResponse(Call<ApiResponse> c, Response<ApiResponse> r) {
                                    if (r.isSuccessful() && r.body() != null && r.body().isSuccess()) {
                                        Toast.makeText(SalaryDetailActivity.this, "Đã lưu, thực lĩnh đã tính lại", Toast.LENGTH_SHORT).show();
                                        reload();
                                    } else if (r.code() != 401) {
                                        Toast.makeText(SalaryDetailActivity.this,
                                                ApiError.message(r, "Không lưu được"), Toast.LENGTH_SHORT).show();
                                    }
                                }

                                @Override
                                public void onFailure(Call<ApiResponse> c, Throwable t) {
                                    Toast.makeText(SalaryDetailActivity.this, "Không lưu được", Toast.LENGTH_SHORT).show();
                                }
                            });
                })
                .setNegativeButton("Đóng", null)
                .show();
    }

    private void confirmFinalize() {
        new AlertDialog.Builder(this)
                .setTitle("Chốt bảng lương?")
                .setMessage("Sau khi chốt, bảng lương này sẽ bị khoá: không sửa được các khoản cộng/trừ, không bị tính lại khi chạy tính lương tự động, và nhân viên sẽ xem được.")
                .setPositiveButton("Chốt", (dialog, which) -> api.finalizeSalary(new SalaryFinalizeRequest(salaryId))
                        .enqueue(new Callback<ApiResponse>() {
                            @Override
                            public void onResponse(Call<ApiResponse> c, Response<ApiResponse> r) {
                                if (r.isSuccessful() && r.body() != null && r.body().isSuccess()) {
                                    Toast.makeText(SalaryDetailActivity.this, "Đã chốt bảng lương", Toast.LENGTH_SHORT).show();
                                    reload();
                                } else if (r.code() != 401) {
                                    Toast.makeText(SalaryDetailActivity.this,
                                            ApiError.message(r, "Không chốt được"), Toast.LENGTH_SHORT).show();
                                }
                            }

                            @Override
                            public void onFailure(Call<ApiResponse> c, Throwable t) {
                                Toast.makeText(SalaryDetailActivity.this, "Không chốt được", Toast.LENGTH_SHORT).show();
                            }
                        }))
                .setNegativeButton("Huỷ", null)
                .show();
    }

    private void confirmUnfinalize() {
        new AlertDialog.Builder(this)
                .setTitle("Mở lại bảng lương?")
                .setMessage("Bảng lương đã chốt sẽ quay về bản nháp để sửa lại. Nhân viên sẽ không còn xem được tháng này cho tới khi chốt lại.")
                .setPositiveButton("Mở lại", (dialog, which) -> api.unfinalizeSalary(new SalaryFinalizeRequest(salaryId))
                        .enqueue(new Callback<ApiResponse>() {
                            @Override
                            public void onResponse(Call<ApiResponse> c, Response<ApiResponse> r) {
                                if (r.isSuccessful() && r.body() != null && r.body().isSuccess()) {
                                    Toast.makeText(SalaryDetailActivity.this, "Đã mở lại bảng lương", Toast.LENGTH_SHORT).show();
                                    reload();
                                } else if (r.code() != 401) {
                                    Toast.makeText(SalaryDetailActivity.this,
                                            ApiError.message(r, "Không mở lại được"), Toast.LENGTH_SHORT).show();
                                }
                            }

                            @Override
                            public void onFailure(Call<ApiResponse> c, Throwable t) {
                                Toast.makeText(SalaryDetailActivity.this, "Không mở lại được", Toast.LENGTH_SHORT).show();
                            }
                        }))
                .setNegativeButton("Huỷ", null)
                .show();
    }
}
