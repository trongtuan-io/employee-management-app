package com.example.employee_management_app;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

// Chi tiet bang luong MOT thang: luong co ban goc, ngay cong, luong theo ngay cong,
// phu cap/tang ca/thuong/khau tru va THUC LINH. Nhan vien phong khac chi mo duoc
// chi tiet cua chinh minh (danh sach da loc san); phong Ke toan co nut Sua cac
// khoan (ban nhap), CHOT chi Ke toan truong/Admin, Mo lai chi Admin.
public class SalaryDetailActivity extends AppCompatActivity {
    public static final String EXTRA_PREFIX = "salary_";

    private EmployeeApi api;
    private int salaryId;
    private int employeeId;
    private int year;
    private boolean canManage;
    private boolean canFinalize;
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
        canFinalize = i.getBooleanExtra(EXTRA_PREFIX + "can_finalize", false);
        isAdmin = i.getBooleanExtra(EXTRA_PREFIX + "is_admin", false);

        // Hien thi truc tiep tu extras; cac thao tac Sua/Chot xong se tai lai tu API.
        showFromExtras(i);

        findViewById(R.id.btnBackDetail).setOnClickListener(v -> finish());
        findViewById(R.id.btnAdjustSalary).setOnClickListener(v -> showAdjustDialog());
        findViewById(R.id.btnFinalizeSalary).setOnClickListener(v -> confirmFinalize());
        findViewById(R.id.btnUnfinalizeSalary).setOnClickListener(v -> confirmUnfinalize());
        findViewById(R.id.btnPaidSalary).setOnClickListener(v -> confirmPaidToggle());
        findViewById(R.id.btnSalaryHistory).setOnClickListener(v -> showHistoryDialog());
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
        ((TextView) findViewById(R.id.tvDtlInsuranceBase)).setText(
                SalaryListResponse.Item.money(i.getStringExtra(EXTRA_PREFIX + "insurance_base")));
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
        String ins = i.getStringExtra(EXTRA_PREFIX + "insurance_amount");
        ((TextView) findViewById(R.id.tvDtlInsurance)).setText(
                (SalaryListResponse.Item.num(ins) > 0 ? "− " : "") + SalaryListResponse.Item.money(ins));
        String tax = i.getStringExtra(EXTRA_PREFIX + "tax_amount");
        ((TextView) findViewById(R.id.tvDtlTax)).setText(
                (SalaryListResponse.Item.num(tax) > 0 ? "− " : "") + SalaryListResponse.Item.money(tax));
        ((TextView) findViewById(R.id.tvDtlNet)).setText(
                SalaryListResponse.Item.money(i.getStringExtra(EXTRA_PREFIX + "net_salary")));
        boolean paid = i.getBooleanExtra(EXTRA_PREFIX + "paid", false);
        TextView paidTv = findViewById(R.id.tvDtlPaidStatus);
        if (paid) {
            String paidAt = i.getStringExtra(EXTRA_PREFIX + "paid_at");
            paidTv.setText("✓ Đã trả lương" + (paidAt != null && paidAt.length() >= 10
                    ? " · " + paidAt.substring(8, 10) + "/" + paidAt.substring(5, 7) + "/" + paidAt.substring(0, 4) : ""));
            paidTv.setTextColor(ContextCompat.getColor(this, R.color.success));
        } else {
            paidTv.setText(finalized ? "Chưa trả lương" : "Chưa trả lương (chốt xong mới trả được)");
            paidTv.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
        }
        updateActions(finalized, paid);
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

    private void updateActions(boolean finalized, boolean paid) {
        View actions = findViewById(R.id.layoutDetailActions);
        if (!canManage) {
            actions.setVisibility(View.GONE);
            return;
        }
        actions.setVisibility(View.VISIBLE);
        findViewById(R.id.btnAdjustSalary).setVisibility(finalized ? View.GONE : View.VISIBLE);
        // Nut Chot chi hien voi Ke toan truong/Admin (NV Ke toan chi soan nhap).
        findViewById(R.id.btnFinalizeSalary).setVisibility(!finalized && canFinalize ? View.VISIBLE : View.GONE);
        findViewById(R.id.btnUnfinalizeSalary).setVisibility(finalized && isAdmin ? View.VISIBLE : View.GONE);
        // Tra luong: chi khi da chot, cung nhom quyen voi Chot.
        android.widget.Button btnPaid = findViewById(R.id.btnPaidSalary);
        btnPaid.setVisibility(finalized && canFinalize ? View.VISIBLE : View.GONE);
        btnPaid.setText(paid ? "Huỷ đánh dấu đã trả lương" : "Đánh dấu đã trả lương");
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
                            ni.putExtra(EXTRA_PREFIX + "insurance_base", it.getInsuranceBase());
                            ni.putExtra(EXTRA_PREFIX + "insurance_amount", it.getInsuranceAmount());
                            ni.putExtra(EXTRA_PREFIX + "tax_amount", it.getTaxAmount());
                            ni.putExtra(EXTRA_PREFIX + "paid", it.isPaid());
                            ni.putExtra(EXTRA_PREFIX + "paid_at", it.getPaidAt());
                            ni.putExtra(EXTRA_PREFIX + "insurance_salary", it.getInsuranceSalary());
                            ni.putExtra(EXTRA_PREFIX + "dependents", it.getDependents());
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

    // Nhan CO DINH phia tren o nhap: chi dung hint thi khi o co san gia tri "0"
    // nhan se bien mat, nguoi dung khong biet o nao la khoan nao.
    private View labeledInput(String label, EditText et) {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = 24;
        wrap.setLayoutParams(lp);
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextSize(13);
        tv.setTypeface(null, android.graphics.Typeface.BOLD);
        wrap.addView(tv);
        wrap.addView(et);
        return wrap;
    }

    private void showAdjustDialog() {
        if (currentExtras == null) return;
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(48, 24, 48, 8);
        EditText etAllowance = moneyInput("Phụ cấp", moneyOf(currentExtras.getStringExtra(EXTRA_PREFIX + "allowance")));
        EditText etOvertime = moneyInput("Tiền tăng ca", moneyOf(currentExtras.getStringExtra(EXTRA_PREFIX + "overtime_pay")));
        EditText etBonus = moneyInput("Thưởng", moneyOf(currentExtras.getStringExtra(EXTRA_PREFIX + "bonus")));
        EditText etDeduction = moneyInput("Khấu trừ khác", moneyOf(currentExtras.getStringExtra(EXTRA_PREFIX + "deduction")));
        // Luong dong BH rieng trong ho so (de trong = dong theo luong co ban).
        EditText etInsSalary = new EditText(this);
        etInsSalary.setHint("Lương đóng bảo hiểm (để trống = theo lương cơ bản)");
        etInsSalary.setInputType(InputType.TYPE_CLASS_NUMBER);
        String insSal = currentExtras.getStringExtra(EXTRA_PREFIX + "insurance_salary");
        if (insSal != null && moneyOf(insSal) > 0) etInsSalary.setText(String.valueOf(Math.round(moneyOf(insSal))));
        EditText etDependents = new EditText(this);
        etDependents.setHint("Số người phụ thuộc (giảm trừ thuế)");
        etDependents.setInputType(InputType.TYPE_CLASS_NUMBER);
        etDependents.setText(String.valueOf(currentExtras.getIntExtra(EXTRA_PREFIX + "dependents", 0)));
        box.addView(labeledInput("Phụ cấp (đồng)", etAllowance));
        box.addView(labeledInput("Tiền tăng ca (đồng)", etOvertime));
        box.addView(labeledInput("Thưởng (đồng)", etBonus));
        box.addView(labeledInput("Khấu trừ khác (đồng)", etDeduction));
        box.addView(labeledInput("Lương đóng bảo hiểm (đồng) — để trống = theo lương cơ bản", etInsSalary));
        box.addView(labeledInput("Số người phụ thuộc — mỗi người giảm trừ thuế 4,4 triệu/tháng", etDependents));
        ScrollView scroll = new ScrollView(this);
        scroll.addView(box);

        new AlertDialog.Builder(this)
                .setTitle("Sửa các khoản cộng/trừ")
                .setView(scroll)
                .setPositiveButton("Lưu", (dialog, which) -> {
                    double allowance = parseMoney(etAllowance);
                    double overtime = parseMoney(etOvertime);
                    double bonus = parseMoney(etBonus);
                    double deduction = parseMoney(etDeduction);
                    if (allowance < 0 || overtime < 0 || bonus < 0 || deduction < 0) {
                        Toast.makeText(this, "Số tiền không hợp lệ", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    Double insSalary = null;
                    if (!etInsSalary.getText().toString().trim().isEmpty()) {
                        double v = parseMoney(etInsSalary);
                        if (v < 0) {
                            Toast.makeText(this, "Lương đóng bảo hiểm không hợp lệ", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        insSalary = v;
                    }
                    int dependents = 0;
                    try {
                        dependents = Integer.parseInt(etDependents.getText().toString().trim());
                    } catch (NumberFormatException e) {
                        Toast.makeText(this, "Số người phụ thuộc không hợp lệ", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (dependents < 0 || dependents > 20) {
                        Toast.makeText(this, "Số người phụ thuộc không hợp lệ", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    api.adjustSalary(new SalaryAdjustRequest(salaryId, allowance, overtime, bonus, deduction,
                            insSalary, dependents))
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

    private void confirmPaidToggle() {
        if (currentExtras == null) return;
        boolean paid = currentExtras.getBooleanExtra(EXTRA_PREFIX + "paid", false);
        new AlertDialog.Builder(this)
                .setTitle(paid ? "Huỷ đánh dấu đã trả lương?" : "Đánh dấu đã trả lương?")
                .setMessage(paid
                        ? "Dòng lương này sẽ quay về trạng thái đã chốt, chưa trả."
                        : "Xác nhận công ty đã chuyển khoản lương tháng này cho nhân viên.")
                .setPositiveButton(paid ? "Huỷ đánh dấu" : "Đã trả", (dialog, which) ->
                        api.paidSalary(new SalaryPaidRequest(salaryId, paid ? 0 : 1))
                                .enqueue(new Callback<ApiResponse>() {
                                    @Override
                                    public void onResponse(Call<ApiResponse> c, Response<ApiResponse> r) {
                                        if (r.isSuccessful() && r.body() != null && r.body().isSuccess()) {
                                            Toast.makeText(SalaryDetailActivity.this,
                                                    paid ? "Đã huỷ đánh dấu trả lương" : "Đã đánh dấu trả lương",
                                                    Toast.LENGTH_SHORT).show();
                                            reload();
                                        } else if (r.code() != 401) {
                                            Toast.makeText(SalaryDetailActivity.this,
                                                    ApiError.message(r, "Không cập nhật được"), Toast.LENGTH_SHORT).show();
                                        }
                                    }

                                    @Override
                                    public void onFailure(Call<ApiResponse> c, Throwable t) {
                                        Toast.makeText(SalaryDetailActivity.this, "Không cập nhật được", Toast.LENGTH_SHORT).show();
                                    }
                                }))
                .setNegativeButton("Đóng", null)
                .show();
    }

    private static String fieldLabel(String field) {
        if (field == null) return "";
        switch (field) {
            case "allowance": return "Phụ cấp";
            case "overtime_pay": return "Tăng ca";
            case "bonus": return "Thưởng";
            case "deduction": return "Khấu trừ khác";
            case "insurance_amount": return "Bảo hiểm";
            case "tax_amount": return "Thuế TNCN";
            case "insurance_salary": return "Lương đóng BH";
            case "dependents": return "Người phụ thuộc";
            case "base_salary": return "Lương theo công";
            case "created": return "Tạo bảng lương";
            case "finalized": return "Chốt bảng lương";
            case "paid": return "Trả lương";
            default: return field;
        }
    }

    private static String historyValue(String field, String value) {
        if (value == null || value.isEmpty()) return "—";
        if ("finalized".equals(field) || "paid".equals(field)) {
            return "1".equals(value) ? "Có" : "Chưa";
        }
        if ("dependents".equals(field)) return value;
        return SalaryListResponse.Item.money(value);
    }

    private void showHistoryDialog() {
        api.getSalaryHistory(salaryId).enqueue(new Callback<SalaryHistoryResponse>() {
            @Override
            public void onResponse(Call<SalaryHistoryResponse> c, Response<SalaryHistoryResponse> r) {
                if (!r.isSuccessful() || r.body() == null || !r.body().isSuccess() || r.body().getData() == null) {
                    if (r.code() != 401) Toast.makeText(SalaryDetailActivity.this, "Không tải được lịch sử", Toast.LENGTH_SHORT).show();
                    return;
                }
                java.util.List<SalaryHistoryResponse.Item> items = r.body().getData();
                if (items.isEmpty()) {
                    new AlertDialog.Builder(SalaryDetailActivity.this)
                            .setTitle("Lịch sử thay đổi")
                            .setMessage("Chưa có thay đổi nào được ghi lại.")
                            .setPositiveButton("Đóng", null)
                            .show();
                    return;
                }
                String[] lines = new String[items.size()];
                for (int k = 0; k < items.size(); k++) {
                    SalaryHistoryResponse.Item h = items.get(k);
                    String when = h.getCreatedAt() != null && h.getCreatedAt().length() >= 16
                            ? h.getCreatedAt().substring(8, 10) + "/" + h.getCreatedAt().substring(5, 7)
                            + " " + h.getCreatedAt().substring(11, 16) : "";
                    lines[k] = fieldLabel(h.getField()) + ": " + historyValue(h.getField(), h.getOldValue())
                            + " → " + historyValue(h.getField(), h.getNewValue())
                            + "\n" + (h.getActorName() == null ? "" : h.getActorName()) + " · " + when;
                }
                new AlertDialog.Builder(SalaryDetailActivity.this)
                        .setTitle("Lịch sử thay đổi")
                        .setItems(lines, null)
                        .setPositiveButton("Đóng", null)
                        .show();
            }

            @Override
            public void onFailure(Call<SalaryHistoryResponse> c, Throwable t) {
                Toast.makeText(SalaryDetailActivity.this, "Không tải được lịch sử", Toast.LENGTH_SHORT).show();
            }
        });
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
