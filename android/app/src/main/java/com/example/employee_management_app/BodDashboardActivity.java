package com.example.employee_management_app;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.github.mikephil.charting.formatter.ValueFormatter;

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class BodDashboardActivity extends AppCompatActivity {

    private EmployeeApi api;
    private TextView tvTotalEmployees, tvPendingProposals, tvSalaryFund, tvAttendanceRate;
    private BarChart chartDeptEmp, chartLeavesDept, chartMovements;
    private LineChart chartSalaryHistory;
    private PieChart chartStructure, chartAttendanceStatus;

    private int curMonth, curYear;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bod_dashboard);
        applyWindowInsets();

        if (!TokenManager.getInstance(this).isExecutive()) {
            Toast.makeText(this, "Chỉ Ban Giám đốc mới có quyền truy cập màn hình Tổng quan BOD!", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        api = ApiClient.getClient(this).create(EmployeeApi.class);

        Calendar now = Calendar.getInstance();
        curMonth = now.get(Calendar.MONTH) + 1;
        curYear = now.get(Calendar.YEAR);

        findViewById(R.id.btnBodBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnBodExportExcel).setOnClickListener(v -> exportExcelReport());

        tvTotalEmployees = findViewById(R.id.tvBodTotalEmployees);
        tvPendingProposals = findViewById(R.id.tvBodPendingProposals);
        tvSalaryFund = findViewById(R.id.tvBodSalaryFund);
        tvAttendanceRate = findViewById(R.id.tvBodAttendanceRate);

        chartDeptEmp = findViewById(R.id.chartDeptEmployees);
        chartSalaryHistory = findViewById(R.id.chartSalaryFundHistory);
        chartStructure = findViewById(R.id.chartWorkforceStructure);
        chartAttendanceStatus = findViewById(R.id.chartAttendanceStatus);
        chartLeavesDept = findViewById(R.id.chartLeavesByDept);
        chartMovements = findViewById(R.id.chartMovements);

        // Click KPI Cards to drill down
        findViewById(R.id.cardTotalEmployees).setOnClickListener(v ->
                startActivity(new Intent(this, StaffListActivity.class)));

        findViewById(R.id.cardPendingProposals).setOnClickListener(v ->
                startActivity(new Intent(this, ProposalApprovalActivity.class)));

        findViewById(R.id.cardSalaryFund).setOnClickListener(v ->
                startActivity(new Intent(this, SalaryOverviewActivity.class)));

        findViewById(R.id.cardAttendanceRate).setOnClickListener(v ->
                startActivity(new Intent(this, AttendanceReportActivity.class)));

        loadAllData();
    }

    private void loadAllData() {
        loadDashboardKpi();
        loadEmployeeStats();
        loadSalaryStats();
        loadAttendanceStats();
        loadLeaveStats();
        loadMovementsStats();
    }

    private void loadDashboardKpi() {
        api.getBodDashboard().enqueue(new Callback<BodDashboardResponse>() {
            @Override
            public void onResponse(Call<BodDashboardResponse> c, Response<BodDashboardResponse> r) {
                if (isFinishing() || isDestroyed()) return;
                if (r.isSuccessful() && r.body() != null && r.body().isSuccess() && r.body().getData() != null) {
                    BodDashboardResponse.Data d = r.body().getData();
                    tvTotalEmployees.setText(String.valueOf(d.getTotalEmployees()));
                    tvPendingProposals.setText(String.valueOf(d.getPendingProposals()));
                    tvSalaryFund.setText(SalaryListResponse.Item.money(String.valueOf(Math.round(d.getTotalSalaryFund()))));
                    tvAttendanceRate.setText(d.getAttendanceRate() + "%");
                }
            }

            @Override
            public void onFailure(Call<BodDashboardResponse> c, Throwable t) {}
        });
    }

    private void loadEmployeeStats() {
        api.getBodEmployeeStats().enqueue(new Callback<BodEmployeeStatsResponse>() {
            @Override
            public void onResponse(Call<BodEmployeeStatsResponse> c, Response<BodEmployeeStatsResponse> r) {
                if (isFinishing() || isDestroyed()) return;
                if (r.isSuccessful() && r.body() != null && r.body().isSuccess() && r.body().getData() != null) {
                    BodEmployeeStatsResponse.Data d = r.body().getData();
                    renderDeptEmployeesChart(d.getByDepartment());
                    renderWorkforceStructureChart(d.getWorkforceStructure());
                }
            }

            @Override
            public void onFailure(Call<BodEmployeeStatsResponse> c, Throwable t) {}
        });
    }

    private void renderDeptEmployeesChart(List<BodEmployeeStatsResponse.DeptItem> list) {
        if (list == null || list.isEmpty()) return;
        List<BarEntry> entries = new ArrayList<>();
        List<String> labels = new ArrayList<>();

        for (int i = 0; i < list.size(); i++) {
            BodEmployeeStatsResponse.DeptItem item = list.get(i);
            entries.add(new BarEntry(i, item.getEmpCount()));
            labels.add(item.getDeptName() != null ? item.getDeptName() : "Khác");
        }

        BarDataSet set = new BarDataSet(entries, "Số nhân viên");
        set.setColor(ContextCompat.getColor(this, R.color.primary));
        set.setValueTextSize(11f);
        set.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return String.valueOf((int) value);
            }
        });

        BarData data = new BarData(set);
        data.setBarWidth(0.5f);

        chartDeptEmp.setData(data);
        chartDeptEmp.getDescription().setEnabled(false);
        chartDeptEmp.getLegend().setEnabled(false);
        chartDeptEmp.setFitBars(true);

        XAxis xAxis = chartDeptEmp.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setGranularity(1f);
        xAxis.setValueFormatter(new IndexAxisValueFormatter(labels));

        chartDeptEmp.invalidate();
    }

    private void renderWorkforceStructureChart(BodEmployeeStatsResponse.Structure s) {
        if (s == null) return;
        List<PieEntry> entries = new ArrayList<>();
        if (s.getWorking() > 0) entries.add(new PieEntry(s.getWorking(), "Đang làm"));
        if (s.getProbation() > 0) entries.add(new PieEntry(s.getProbation(), "Thử việc"));
        if (s.getInactive() > 0) entries.add(new PieEntry(s.getInactive(), "Đã nghỉ"));

        if (entries.isEmpty()) entries.add(new PieEntry(1, "Chưa có dữ liệu"));

        PieDataSet set = new PieDataSet(entries, "");
        int[] colors = {
                ContextCompat.getColor(this, R.color.success),
                ContextCompat.getColor(this, R.color.warning),
                ContextCompat.getColor(this, R.color.danger)
        };
        set.setColors(colors);
        set.setValueTextSize(12f);
        set.setValueTextColor(Color.WHITE);

        PieData data = new PieData(set);
        chartStructure.setData(data);
        chartStructure.getDescription().setEnabled(false);
        chartStructure.setHoleRadius(40f);
        chartStructure.invalidate();
    }

    private void loadSalaryStats() {
        api.getBodSalaryStats().enqueue(new Callback<BodSalaryStatsResponse>() {
            @Override
            public void onResponse(Call<BodSalaryStatsResponse> c, Response<BodSalaryStatsResponse> r) {
                if (isFinishing() || isDestroyed()) return;
                if (r.isSuccessful() && r.body() != null && r.body().isSuccess() && r.body().getData() != null) {
                    renderSalaryHistoryChart(r.body().getData());
                }
            }

            @Override
            public void onFailure(Call<BodSalaryStatsResponse> c, Throwable t) {}
        });
    }

    private void renderSalaryHistoryChart(List<BodSalaryStatsResponse.Item> list) {
        if (list == null || list.isEmpty()) return;
        List<Entry> entries = new ArrayList<>();
        List<String> labels = new ArrayList<>();

        for (int i = 0; i < list.size(); i++) {
            BodSalaryStatsResponse.Item item = list.get(i);
            entries.add(new Entry(i, (float) (item.getTotalFund() / 1_000_000.0))); // quy đổi triệu VNĐ
            labels.add(item.getLabel());
        }

        LineDataSet set = new LineDataSet(entries, "Quỹ lương (Triệu VNĐ)");
        set.setColor(ContextCompat.getColor(this, R.color.success));
        set.setCircleColor(ContextCompat.getColor(this, R.color.primary));
        set.setLineWidth(2.5f);
        set.setCircleRadius(5f);
        set.setValueTextSize(10f);

        LineData data = new LineData(set);
        chartSalaryHistory.setData(data);
        chartSalaryHistory.getDescription().setEnabled(false);

        XAxis xAxis = chartSalaryHistory.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setGranularity(1f);
        xAxis.setValueFormatter(new IndexAxisValueFormatter(labels));

        chartSalaryHistory.invalidate();
    }

    private void loadAttendanceStats() {
        api.getBodAttendanceStats(curMonth, curYear).enqueue(new Callback<BodAttendanceStatsResponse>() {
            @Override
            public void onResponse(Call<BodAttendanceStatsResponse> c, Response<BodAttendanceStatsResponse> r) {
                if (isFinishing() || isDestroyed()) return;
                if (r.isSuccessful() && r.body() != null && r.body().isSuccess() && r.body().getData() != null) {
                    BodAttendanceStatsResponse.Data d = r.body().getData();
                    renderAttendanceChart(d.getOnTime(), d.getLate());
                }
            }

            @Override
            public void onFailure(Call<BodAttendanceStatsResponse> c, Throwable t) {}
        });
    }

    private void renderAttendanceChart(int onTime, int late) {
        List<PieEntry> entries = new ArrayList<>();
        if (onTime > 0) entries.add(new PieEntry(onTime, "Đúng giờ"));
        if (late > 0) entries.add(new PieEntry(late, "Đi muộn"));

        if (entries.isEmpty()) entries.add(new PieEntry(1, "Chưa chấm công"));

        PieDataSet set = new PieDataSet(entries, "");
        int[] colors = {
                ContextCompat.getColor(this, R.color.success),
                ContextCompat.getColor(this, R.color.warning)
        };
        set.setColors(colors);
        set.setValueTextSize(12f);
        set.setValueTextColor(Color.WHITE);

        PieData data = new PieData(set);
        chartAttendanceStatus.setData(data);
        chartAttendanceStatus.getDescription().setEnabled(false);
        chartAttendanceStatus.setHoleRadius(40f);
        chartAttendanceStatus.invalidate();
    }

    private void loadLeaveStats() {
        api.getBodLeaveStats(curMonth, curYear).enqueue(new Callback<BodLeaveStatsResponse>() {
            @Override
            public void onResponse(Call<BodLeaveStatsResponse> c, Response<BodLeaveStatsResponse> r) {
                if (isFinishing() || isDestroyed()) return;
                if (r.isSuccessful() && r.body() != null && r.body().isSuccess() && r.body().getData() != null) {
                    renderLeaveStatsChart(r.body().getData());
                }
            }

            @Override
            public void onFailure(Call<BodLeaveStatsResponse> c, Throwable t) {}
        });
    }

    private void renderLeaveStatsChart(List<BodLeaveStatsResponse.Item> list) {
        if (list == null || list.isEmpty()) return;
        List<BarEntry> entries = new ArrayList<>();
        List<String> labels = new ArrayList<>();

        for (int i = 0; i < list.size(); i++) {
            BodLeaveStatsResponse.Item item = list.get(i);
            entries.add(new BarEntry(i, item.getApproved()));
            labels.add(item.getDeptName() != null ? item.getDeptName() : "Khác");
        }

        BarDataSet set = new BarDataSet(entries, "Số lượt nghỉ phép đã duyệt");
        set.setColor(ContextCompat.getColor(this, R.color.warning));
        set.setValueTextSize(11f);

        BarData data = new BarData(set);
        data.setBarWidth(0.5f);

        chartLeavesDept.setData(data);
        chartLeavesDept.getDescription().setEnabled(false);
        chartLeavesDept.setFitBars(true);

        XAxis xAxis = chartLeavesDept.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setGranularity(1f);
        xAxis.setValueFormatter(new IndexAxisValueFormatter(labels));

        chartLeavesDept.invalidate();
    }

    private void loadMovementsStats() {
        api.getBodMovements().enqueue(new Callback<BodMovementsResponse>() {
            @Override
            public void onResponse(Call<BodMovementsResponse> c, Response<BodMovementsResponse> r) {
                if (isFinishing() || isDestroyed()) return;
                if (r.isSuccessful() && r.body() != null && r.body().isSuccess() && r.body().getData() != null) {
                    renderMovementsChart(r.body().getData());
                }
            }

            @Override
            public void onFailure(Call<BodMovementsResponse> c, Throwable t) {}
        });
    }

    private void renderMovementsChart(List<BodMovementsResponse.Item> list) {
        if (list == null || list.isEmpty()) return;
        List<BarEntry> entriesHires = new ArrayList<>();
        List<String> labels = new ArrayList<>();

        for (int i = 0; i < list.size(); i++) {
            BodMovementsResponse.Item item = list.get(i);
            entriesHires.add(new BarEntry(i, new float[]{item.getHires(), item.getTerminations()}));
            labels.add(item.getLabel());
        }

        BarDataSet set = new BarDataSet(entriesHires, "Tuyển mới / Nghỉ việc");
        int[] colors = new int[]{
                ContextCompat.getColor(this, R.color.success),
                ContextCompat.getColor(this, R.color.danger)
        };
        set.setColors(colors);
        set.setStackLabels(new String[]{"Tuyển mới", "Nghỉ việc"});
        set.setValueTextSize(11f);

        BarData data = new BarData(set);
        data.setBarWidth(0.5f);

        chartMovements.setData(data);
        chartMovements.getDescription().setEnabled(false);
        chartMovements.setFitBars(true);

        XAxis xAxis = chartMovements.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setGranularity(1f);
        xAxis.setValueFormatter(new IndexAxisValueFormatter(labels));

        chartMovements.invalidate();
    }

    private void exportExcelReport() {
        Toast.makeText(this, "Đang tạo báo cáo Excel BOD...", Toast.LENGTH_SHORT).show();
        api.exportBodReport(curMonth, curYear).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> c, Response<ResponseBody> r) {
                if (isFinishing() || isDestroyed()) return;
                if (!r.isSuccessful() || r.body() == null) {
                    if (r.code() != 401) Toast.makeText(BodDashboardActivity.this, "Không xuất được báo cáo", Toast.LENGTH_SHORT).show();
                    return;
                }
                try {
                    byte[] bytes = r.body().bytes();
                    String name = String.format(Locale.US, "BaoCao_BOD_Thang_%02d_%d.csv", curMonth, curYear);
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
                        uri = androidx.core.content.FileProvider.getUriForFile(BodDashboardActivity.this, getApplicationContext().getPackageName() + ".fileprovider", targetFile);
                    }
                    promptOpenCsv(uri, name, bytes);
                } catch (Exception e) {
                    Toast.makeText(BodDashboardActivity.this, "Không lưu được file: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> c, Throwable t) {
                if (!isFinishing()) Toast.makeText(BodDashboardActivity.this, "Không xuất được file", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void promptOpenCsv(Uri uri, String name, byte[] bytes) {
        new AlertDialog.Builder(this)
                .setTitle("Xuất báo cáo BOD thành công")
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

        new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(scroll)
                .setPositiveButton("Đóng", null)
                .show();
    }

    private void applyWindowInsets() {
        View root = findViewById(R.id.bodRoot);
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
