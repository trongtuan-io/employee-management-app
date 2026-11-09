package com.example.employee_management_app;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

// Bang luong TONG HOP toan cong ty theo thang (chi Admin + phong Ke toan vao
// duoc; server require_payroll quyet). Moi nhan vien mot dong kem ngay cong,
// thuc linh va trang thai chot; cham vao dong da tinh de mo chi tiet thang do.
public class SalaryOverviewActivity extends AppCompatActivity {
    private final List<SalaryOverviewResponse.Item> items = new ArrayList<>();
    private OverviewAdapter adapter;
    private EmployeeApi api;
    private EditText etMonth, etYear;
    private TextView tvSummary, tvTotal;
    private boolean isAdmin;
    private boolean canFinalize;
    private int curMonth, curYear;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_salary_overview);

        api = ApiClient.getClient(this).create(EmployeeApi.class);
        TokenManager tm = TokenManager.getInstance(this);
        String role = tm.getRole();
        String dept = tm.getDepartmentName();
        isAdmin = "admin".equalsIgnoreCase(role);
        canFinalize = isAdmin || ("ACC".equalsIgnoreCase(dept) && "manager".equalsIgnoreCase(role));

        etMonth = findViewById(R.id.etOverviewMonth);
        etYear = findViewById(R.id.etOverviewYear);
        tvSummary = findViewById(R.id.tvOverviewSummary);
        tvTotal = findViewById(R.id.tvOverviewTotal);

        curMonth = getIntent().getIntExtra("month", 0);
        curYear = getIntent().getIntExtra("year", 0);
        if (curMonth < 1 || curMonth > 12 || curYear < 2000) {
            Calendar now = Calendar.getInstance();
            curMonth = now.get(Calendar.MONTH) + 1;
            curYear = now.get(Calendar.YEAR);
        }
        etMonth.setText(String.valueOf(curMonth));
        etYear.setText(String.valueOf(curYear));

        ListView lv = findViewById(R.id.lvOverview);
        adapter = new OverviewAdapter(this);
        lv.setAdapter(adapter);
        lv.setOnItemClickListener((parent, view, position, id) -> {
            SalaryOverviewResponse.Item item = items.get(position);
            if (item.hasSalary()) openDetail(item);
            else Toast.makeText(this, "Nhân viên này chưa được tính lương tháng " + curMonth + "/" + curYear, Toast.LENGTH_SHORT).show();
        });

        findViewById(R.id.btnOverviewBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnOverviewLoad).setOnClickListener(v -> {
            int m, y;
            try {
                m = Integer.parseInt(etMonth.getText().toString().trim());
                y = Integer.parseInt(etYear.getText().toString().trim());
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Nhập tháng và năm hợp lệ", Toast.LENGTH_SHORT).show();
                return;
            }
            if (m < 1 || m > 12 || y < 2000 || y > 2100) {
                Toast.makeText(this, "Tháng phải từ 1-12, năm hợp lệ", Toast.LENGTH_SHORT).show();
                return;
            }
            curMonth = m;
            curYear = y;
            loadOverview();
        });

        loadOverview();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Quay lai tu man chi tiet (sau khi sua/chot) thi tai lai so lieu.
        if (!items.isEmpty()) loadOverview();
    }

    private void loadOverview() {
        api.getSalaryOverview(curMonth, curYear).enqueue(new Callback<SalaryOverviewResponse>() {
            @Override
            public void onResponse(Call<SalaryOverviewResponse> c, Response<SalaryOverviewResponse> r) {
                if (r.isSuccessful() && r.body() != null && r.body().isSuccess() && r.body().getData() != null) {
                    items.clear();
                    items.addAll(r.body().getData());
                    adapter.notifyDataSetChanged();
                    updateTotals();
                } else if (r.code() != 401) {
                    Toast.makeText(SalaryOverviewActivity.this,
                            ApiError.message(r, "Không tải được bảng lương tổng hợp"), Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<SalaryOverviewResponse> c, Throwable t) {
                Toast.makeText(SalaryOverviewActivity.this, "Không tải được bảng lương tổng hợp", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateTotals() {
        int calculated = 0, finalized = 0;
        double totalNet = 0;
        for (SalaryOverviewResponse.Item it : items) {
            if (it.hasSalary()) {
                calculated++;
                totalNet += SalaryListResponse.Item.num(it.getNetSalary());
                if (it.isFinalized()) finalized++;
            }
        }
        tvSummary.setText("Tháng " + curMonth + "/" + curYear + " · Đã tính " + calculated + "/" + items.size()
                + " nhân viên · Đã chốt " + finalized + ". Chạm vào dòng đã tính lương để xem chi tiết, sửa các khoản hoặc chốt.");
        tvTotal.setText("Tổng thực lĩnh: " + SalaryListResponse.Item.money(String.valueOf(Math.round(totalNet))));
    }

    private void openDetail(SalaryOverviewResponse.Item item) {
        Intent i = new Intent(this, SalaryDetailActivity.class);
        String p = SalaryDetailActivity.EXTRA_PREFIX;
        i.putExtra(p + "id", item.getSalaryId());
        i.putExtra(p + "employee_id", item.getEmployeeId());
        i.putExtra(p + "full_name", item.getFullName());
        i.putExtra(p + "month", curMonth);
        i.putExtra(p + "year", curYear);
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
        i.putExtra(p + "can_manage", true);
        i.putExtra(p + "can_finalize", canFinalize);
        i.putExtra(p + "is_admin", isAdmin);
        startActivity(i);
    }

    private class OverviewAdapter extends BaseAdapter {
        private final LayoutInflater inflater;

        OverviewAdapter(Context ctx) { inflater = LayoutInflater.from(ctx); }

        @Override public int getCount() { return items.size(); }
        @Override public Object getItem(int position) { return items.get(position); }
        @Override public long getItemId(int position) { return items.get(position).getEmployeeId(); }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View v = convertView;
            if (v == null) v = inflater.inflate(R.layout.item_leave, parent, false);
            SalaryOverviewResponse.Item item = items.get(position);
            TextView tvTitle = v.findViewById(R.id.tvRowTitle);
            TextView tvBadge = v.findViewById(R.id.tvRowBadge);
            TextView tvDates = v.findViewById(R.id.tvRowDates);
            TextView tvMeta = v.findViewById(R.id.tvRowMeta);

            tvTitle.setText(item.getFullName());
            StringBuilder line2 = new StringBuilder();
            if (item.getDeptName() != null && !item.getDeptName().isEmpty()) {
                line2.append("Phòng ").append(item.getDeptName()).append(" · ");
            }
            line2.append("Lương cơ bản: ").append(SalaryListResponse.Item.money(item.getFullBaseSalary()));
            tvDates.setText(line2.toString());

            if (item.hasSalary()) {
                tvMeta.setText("Ngày công: " + SalaryListResponse.Item.daysText(item.getWorkDays())
                        + "/" + SalaryListResponse.Item.daysText(item.getStandardDays())
                        + " · Thực lĩnh: " + SalaryListResponse.Item.money(item.getNetSalary()));
                tvBadge.setText(item.isFinalized() ? "Đã chốt" : "Bản nháp");
                tvBadge.setBackgroundResource(item.isFinalized() ? R.drawable.bg_badge_approved : R.drawable.bg_badge_pending);
                tvBadge.setTextColor(ContextCompat.getColor(SalaryOverviewActivity.this,
                        item.isFinalized() ? R.color.success : R.color.warning));
            } else {
                tvMeta.setText("Chưa tính lương tháng này");
                tvBadge.setText("Chưa tính");
                tvBadge.setBackgroundResource(R.drawable.bg_badge_rejected);
                tvBadge.setTextColor(ContextCompat.getColor(SalaryOverviewActivity.this, R.color.danger));
            }
            return v;
        }
    }
}
