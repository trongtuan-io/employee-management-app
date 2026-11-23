package com.example.employee_management_app;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

// Bang luong TONG HOP toan cong ty theo thang (chi Admin + phong Ke toan vao
// duoc; server require_payroll quyet). Danh sach nhom theo PHONG BAN kem tong
// quy luong tung phong, chan trang co tong cong ty + so sanh thang truoc,
// xuat CSV va quan ly phu cap co dinh. Cham dong da tinh de mo chi tiet.
public class SalaryOverviewActivity extends AppCompatActivity {
    private final List<SalaryOverviewResponse.Item> items = new ArrayList<>();
    private final List<Object> rows = new ArrayList<>();
    private OverviewAdapter adapter;
    private EmployeeApi api;
    private EditText etMonth, etYear;
    private TextView tvSummary, tvTotal, tvCompare;
    private Button btnAction;
    private boolean isAdmin;
    private boolean canFinalize;
    private boolean canEditPolicies;
    private int curMonth, curYear;
    private double currentTotalNet = 0;

    // Dong tieu de nhom phong ban trong danh sach.
    private static class DeptHeader {
        final String label;
        DeptHeader(String label) { this.label = label; }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_salary_overview);

        api = ApiClient.getClient(this).create(EmployeeApi.class);
        TokenManager tm = TokenManager.getInstance(this);
        String role = tm.getRole();
        String dept = tm.getDepartmentName();
        isAdmin = tm.isExecutive();
        boolean isPayroll = "admin".equalsIgnoreCase(role) || ("ACC".equalsIgnoreCase(dept) && "manager".equalsIgnoreCase(role));
        if (!isAdmin && !isPayroll) {
            Toast.makeText(this, "Chỉ Giám đốc, Kế toán trưởng và Admin mới có quyền truy cập bảng lương toàn công ty!", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        canFinalize = isAdmin || isPayroll;
        canEditPolicies = canFinalize;

        etMonth = findViewById(R.id.etOverviewMonth);
        etYear = findViewById(R.id.etOverviewYear);
        tvSummary = findViewById(R.id.tvOverviewSummary);
        tvTotal = findViewById(R.id.tvOverviewTotal);
        tvCompare = findViewById(R.id.tvOverviewCompare);
        btnAction = findViewById(R.id.btnOverviewAction);

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
            Object row = rows.get(position);
            if (row instanceof SalaryOverviewResponse.Item) {
                SalaryOverviewResponse.Item item = (SalaryOverviewResponse.Item) row;
                if (item.hasSalary()) openDetail(item);
                else Toast.makeText(this, "Nhân viên này chưa được tính lương tháng " + curMonth + "/" + curYear, Toast.LENGTH_SHORT).show();
            }
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
        findViewById(R.id.btnOverviewExport).setOnClickListener(v -> exportCsv());
        findViewById(R.id.btnOverviewPolicies).setOnClickListener(v -> {
            Intent pi = new Intent(this, SalaryPolicyActivity.class);
            pi.putExtra("can_edit", canEditPolicies);
            startActivity(pi);
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
                    rebuildRows();
                    adapter.notifyDataSetChanged();
                    updateTotals();
                    loadPreviousMonthTotal();
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

    // Gom nhom theo phong ban (API da sap xep san theo phong), chen dong tieu
    // de kem tong thuc linh cua phong truoc moi nhom.
    private void rebuildRows() {
        rows.clear();
        Map<String, Double> deptTotals = new LinkedHashMap<>();
        Map<String, Integer> deptCounts = new LinkedHashMap<>();
        for (SalaryOverviewResponse.Item it : items) {
            String d = deptLabel(it);
            deptCounts.merge(d, 1, Integer::sum);
            if (it.hasSalary()) deptTotals.merge(d, SalaryListResponse.Item.num(it.getNetSalary()), Double::sum);
        }
        String lastDept = null;
        for (SalaryOverviewResponse.Item it : items) {
            String d = deptLabel(it);
            if (!d.equals(lastDept)) {
                lastDept = d;
                double tot = deptTotals.getOrDefault(d, 0.0);
                rows.add(new DeptHeader("Phòng " + d + " · " + deptCounts.get(d)
                        + " nhân viên · Tổng: " + SalaryListResponse.Item.money(String.valueOf(Math.round(tot)))));
            }
            rows.add(it);
        }
    }

    private static String deptLabel(SalaryOverviewResponse.Item it) {
        return it.getDeptName() == null || it.getDeptName().isEmpty() ? "Chưa xếp phòng" : it.getDeptName();
    }

    private void updateTotals() {
        int calculated = 0, finalized = 0, bodApproved = 0, paidCount = 0;
        double totalNet = 0;
        for (SalaryOverviewResponse.Item it : items) {
            if (it.hasSalary()) {
                calculated++;
                totalNet += SalaryListResponse.Item.num(it.getNetSalary());
                if (it.isFinalized()) finalized++;
                if (it.isBodApproved()) bodApproved++;
                if (it.isPaid()) paidCount++;
            }
        }
        currentTotalNet = totalNet;
        tvSummary.setText("Tháng " + curMonth + "/" + curYear + " · Đã tính " + calculated + "/" + items.size()
                + " · Trình GĐ " + finalized + " · Giám đốc duyệt " + bodApproved + " · Đã giải ngân " + paidCount);
        tvTotal.setText("Tổng thực lĩnh: " + SalaryListResponse.Item.money(String.valueOf(Math.round(totalNet))));

        boolean isExec = TokenManager.getInstance(this).isExecutive();
        if (btnAction != null) {
            if (isExec) { // Tài khoản Giám đốc (BOD) / Admin
                if (finalized > 0 && bodApproved < finalized) {
                    btnAction.setVisibility(View.VISIBLE);
                    btnAction.setEnabled(true);
                    btnAction.setText("Phê duyệt bảng lương (Giám đốc)");
                    final double finalNet = totalNet;
                    btnAction.setOnClickListener(v -> promptBodApproveSalary(finalNet));
                } else if (bodApproved > 0) {
                    btnAction.setVisibility(View.VISIBLE);
                    btnAction.setEnabled(false);
                    btnAction.setText("Đã phê duyệt bảng lương (Kế toán đang giải ngân)");
                } else {
                    btnAction.setVisibility(View.VISIBLE);
                    btnAction.setEnabled(false);
                    btnAction.setText("Kế toán chưa trình bảng lương");
                }
            } else if (canFinalize) { // Tài khoản Kế toán trưởng / Admin
                if (calculated > 0 && finalized < calculated) {
                    btnAction.setVisibility(View.VISIBLE);
                    btnAction.setEnabled(true);
                    btnAction.setText("Chốt & Trình Giám đốc duyệt");
                    btnAction.setOnClickListener(v -> promptFinalizeSalary());
                } else if (finalized > 0 && bodApproved < finalized) {
                    btnAction.setVisibility(View.VISIBLE);
                    btnAction.setEnabled(false);
                    btnAction.setText("Đã trình Giám đốc (Đang chờ duyệt)");
                } else if (bodApproved > 0 && paidCount < bodApproved) {
                    btnAction.setVisibility(View.VISIBLE);
                    btnAction.setEnabled(true);
                    btnAction.setText("Giải ngân lương (Giám đốc đã duyệt)");
                    btnAction.setOnClickListener(v -> promptDisburseSalary());
                } else if (paidCount > 0 && paidCount == calculated) {
                    btnAction.setVisibility(View.VISIBLE);
                    btnAction.setEnabled(false);
                    btnAction.setText("Đã giải ngân hoàn tất");
                } else {
                    btnAction.setVisibility(View.GONE);
                }
            } else {
                btnAction.setVisibility(View.GONE);
            }
        }
    }

    private void promptBodApproveSalary(double totalNet) {
        new AlertDialog.Builder(this)
                .setTitle("Phê duyệt bảng lương tháng " + curMonth + "/" + curYear)
                .setMessage("Bạn có chắc chắn muốn PHÊ DUYỆT bảng lương tháng " + curMonth + "/" + curYear
                        + " với tổng quỹ lương thực lĩnh là " + SalaryListResponse.Item.money(String.valueOf(Math.round(totalNet))) + "?\n\nSau khi duyệt, Kế toán trưởng sẽ có thể tiến hành giải ngân.")
                .setPositiveButton("Phê duyệt", (dialog, which) -> {
                    api.approveSalaryBod(new SalaryFinalizeRequest(curMonth, curYear)).enqueue(new Callback<ApiResponse>() {
                        @Override
                        public void onResponse(Call<ApiResponse> c, Response<ApiResponse> r) {
                            if (r.isSuccessful() && r.body() != null && r.body().isSuccess()) {
                                Toast.makeText(SalaryOverviewActivity.this, "Đã phê duyệt bảng lương tháng " + curMonth + "/" + curYear, Toast.LENGTH_SHORT).show();
                                loadOverview();
                            } else {
                                Toast.makeText(SalaryOverviewActivity.this, ApiError.message(r, "Không phê duyệt được"), Toast.LENGTH_SHORT).show();
                            }
                        }
                        @Override public void onFailure(Call<ApiResponse> c, Throwable t) {
                            Toast.makeText(SalaryOverviewActivity.this, "Lỗi kết nối", Toast.LENGTH_SHORT).show();
                        }
                    });
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    private void promptFinalizeSalary() {
        new AlertDialog.Builder(this)
                .setTitle("Chốt & Trình Giám đốc")
                .setMessage("Bạn có chắc chắn muốn chốt toàn bộ bảng lương tháng " + curMonth + "/" + curYear + " và gửi trình Giám đốc phê duyệt?")
                .setPositiveButton("Chốt & Trình GĐ", (dialog, which) -> {
                    api.finalizeSalary(new SalaryFinalizeRequest(curMonth, curYear)).enqueue(new Callback<ApiResponse>() {
                        @Override
                        public void onResponse(Call<ApiResponse> c, Response<ApiResponse> r) {
                            if (r.isSuccessful() && r.body() != null && r.body().isSuccess()) {
                                Toast.makeText(SalaryOverviewActivity.this, "Đã chốt và trình Giám đốc duyệt!", Toast.LENGTH_SHORT).show();
                                loadOverview();
                            } else {
                                Toast.makeText(SalaryOverviewActivity.this, ApiError.message(r, "Không chốt được"), Toast.LENGTH_SHORT).show();
                            }
                        }
                        @Override public void onFailure(Call<ApiResponse> c, Throwable t) {
                            Toast.makeText(SalaryOverviewActivity.this, "Lỗi kết nối", Toast.LENGTH_SHORT).show();
                        }
                    });
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    private void promptDisburseSalary() {
        new AlertDialog.Builder(this)
                .setTitle("Giải ngân lương tháng " + curMonth + "/" + curYear)
                .setMessage("Bảng lương đã được Giám đốc phê duyệt. Bạn có chắc chắn muốn tiến hành GIẢI NGÂN / ĐÁNH DẤU ĐÃ TRẢ LƯƠNG cho toàn bộ nhân viên?")
                .setPositiveButton("Giải ngân", (dialog, which) -> {
                    int count = 0;
                    for (SalaryOverviewResponse.Item it : items) {
                        if (it.hasSalary() && it.isBodApproved() && !it.isPaid()) {
                            count++;
                            api.paidSalary(new SalaryPaidRequest(it.getSalaryId(), 1)).enqueue(new Callback<ApiResponse>() {
                                @Override public void onResponse(Call<ApiResponse> c, Response<ApiResponse> r) { loadOverview(); }
                                @Override public void onFailure(Call<ApiResponse> c, Throwable t) {}
                            });
                        }
                    }
                    Toast.makeText(this, "Đã khởi chạy giải ngân " + count + " bảng lương!", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    // So sanh quy luong voi thang lien truoc (bao cao nhanh cho Ke toan truong).
    private void loadPreviousMonthTotal() {
        int pm = curMonth - 1, py = curYear;
        if (pm < 1) { pm = 12; py = curYear - 1; }
        final int fpm = pm, fpy = py;
        api.getSalaryOverview(pm, py).enqueue(new Callback<SalaryOverviewResponse>() {
            @Override
            public void onResponse(Call<SalaryOverviewResponse> c, Response<SalaryOverviewResponse> r) {
                if (r.isSuccessful() && r.body() != null && r.body().isSuccess() && r.body().getData() != null) {
                    double prev = 0;
                    boolean any = false;
                    for (SalaryOverviewResponse.Item it : r.body().getData()) {
                        if (it.hasSalary()) { prev += SalaryListResponse.Item.num(it.getNetSalary()); any = true; }
                    }
                    if (!any) {
                        tvCompare.setText("Tháng trước (" + fpm + "/" + fpy + "): chưa có dữ liệu lương.");
                        return;
                    }
                    double diff = currentTotalNet - prev;
                    String sign = diff > 0 ? "+" : "";
                    tvCompare.setText("Tháng trước (" + fpm + "/" + fpy + "): "
                            + SalaryListResponse.Item.money(String.valueOf(Math.round(prev)))
                            + " · Chênh lệch: " + sign
                            + SalaryListResponse.Item.money(String.valueOf(Math.round(diff))));
                }
            }

            @Override
            public void onFailure(Call<SalaryOverviewResponse> c, Throwable t) { }
        });
    }

    // Tai CSV bang luong thang dang xem ve thu muc Tai xuong cua may.
    private void exportCsv() {
        Toast.makeText(this, "Đang tạo file bảng lương...", Toast.LENGTH_SHORT).show();
        api.exportSalary(curMonth, curYear).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> c, Response<ResponseBody> r) {
                if (!r.isSuccessful() || r.body() == null) {
                    if (r.code() != 401) Toast.makeText(SalaryOverviewActivity.this, "Không xuất được file", Toast.LENGTH_SHORT).show();
                    return;
                }
                try {
                    byte[] bytes = r.body().bytes();
                    String name = String.format("bang_luong_%02d_%d.csv", curMonth, curYear);
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
                        uri = androidx.core.content.FileProvider.getUriForFile(SalaryOverviewActivity.this, getApplicationContext().getPackageName() + ".fileprovider", targetFile);
                    }
                    final Uri finalUri = uri;
                    runOnUiThread(() -> promptOpenCsv(finalUri, name, bytes));
                } catch (Exception e) {
                    Toast.makeText(SalaryOverviewActivity.this, "Không lưu được file: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> c, Throwable t) {
                Toast.makeText(SalaryOverviewActivity.this, "Không xuất được file", Toast.LENGTH_SHORT).show();
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
        i.putExtra(p + "insurance_base", item.getInsuranceBase());
        i.putExtra(p + "insurance_amount", item.getInsuranceAmount());
        i.putExtra(p + "tax_amount", item.getTaxAmount());
        i.putExtra(p + "paid", item.isPaid());
        i.putExtra(p + "insurance_salary", item.getInsuranceSalary());
        i.putExtra(p + "dependents", item.getDependents());
        i.putExtra(p + "net_salary", item.getNetSalary());
        i.putExtra(p + "work_days", item.getWorkDays());
        i.putExtra(p + "standard_days", item.getStandardDays());
        i.putExtra(p + "finalized", item.isFinalized());
        i.putExtra(p + "bod_approved", item.isBodApproved());
        i.putExtra(p + "can_manage", true);
        i.putExtra(p + "can_finalize", canFinalize);
        i.putExtra(p + "is_admin", isAdmin);
        startActivity(i);
    }

    private class OverviewAdapter extends BaseAdapter {
        private final LayoutInflater inflater;

        OverviewAdapter(Context ctx) { inflater = LayoutInflater.from(ctx); }

        @Override public int getCount() { return rows.size(); }
        @Override public Object getItem(int position) { return rows.get(position); }
        @Override public long getItemId(int position) { return position; }
        @Override public int getViewTypeCount() { return 2; }

        @Override
        public int getItemViewType(int position) {
            return rows.get(position) instanceof DeptHeader ? 0 : 1;
        }

        @Override
        public boolean isEnabled(int position) {
            return rows.get(position) instanceof SalaryOverviewResponse.Item;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            Object row = rows.get(position);
            if (row instanceof DeptHeader) {
                View v = convertView;
                if (v == null || v.findViewById(R.id.tvSectionHeader) == null) {
                    v = inflater.inflate(R.layout.item_section_header, parent, false);
                }
                ((TextView) v.findViewById(R.id.tvSectionHeader)).setText(((DeptHeader) row).label);
                return v;
            }
            View v = convertView;
            if (v == null || v.findViewById(R.id.tvRowTitle) == null) {
                v = inflater.inflate(R.layout.item_leave, parent, false);
            }
            SalaryOverviewResponse.Item item = (SalaryOverviewResponse.Item) row;
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
                String meta = "Ngày công: " + SalaryListResponse.Item.daysText(item.getWorkDays())
                        + "/" + SalaryListResponse.Item.daysText(item.getStandardDays())
                        + " · Thực lĩnh: " + SalaryListResponse.Item.money(item.getNetSalary());
                double ins = SalaryListResponse.Item.num(item.getInsuranceAmount());
                double tax = SalaryListResponse.Item.num(item.getTaxAmount());
                if (ins > 0 || tax > 0) {
                    meta += "\nĐã trừ BH " + SalaryListResponse.Item.money(item.getInsuranceAmount())
                            + " · Thuế " + SalaryListResponse.Item.money(item.getTaxAmount());
                }
                tvMeta.setText(meta);
                if (item.isPaid()) {
                    tvBadge.setText("Đã giải ngân");
                    tvBadge.setBackgroundResource(R.drawable.bg_badge_approved);
                    tvBadge.setTextColor(ContextCompat.getColor(SalaryOverviewActivity.this, R.color.success));
                } else if (item.isBodApproved()) {
                    tvBadge.setText("GĐ đã duyệt");
                    tvBadge.setBackgroundResource(R.drawable.bg_badge_approved);
                    tvBadge.setTextColor(ContextCompat.getColor(SalaryOverviewActivity.this, R.color.primary));
                } else if (item.isFinalized()) {
                    tvBadge.setText("Trình Giám đốc");
                    tvBadge.setBackgroundResource(R.drawable.bg_badge_pending);
                    tvBadge.setTextColor(ContextCompat.getColor(SalaryOverviewActivity.this, R.color.warning));
                } else {
                    tvBadge.setText("Đang lập");
                    tvBadge.setBackgroundResource(R.drawable.bg_badge_rejected);
                    tvBadge.setTextColor(ContextCompat.getColor(SalaryOverviewActivity.this, R.color.text_secondary));
                }
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
