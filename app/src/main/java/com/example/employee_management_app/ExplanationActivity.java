package com.example.employee_management_app;

import android.app.DatePickerDialog;
import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

// Giai trinh quen cham cong: nhan vien tao don (ngay + gio de xuat), sep phong
// / HR duyet o tab Cho duyet; don da xu ly o lai de truy vet giong don nghi.
public class ExplanationActivity extends AppCompatActivity {
    private static final int MODE_MINE = 0;
    private static final int MODE_QUEUE = 1;

    private final List<ExplanationListResponse.Item> items = new ArrayList<>();
    private ExplAdapter adapter;
    private EmployeeApi api;
    private boolean canSeeQueue;
    private int myId;
    private int mode = MODE_MINE;
    private TextView tvTabMine, tvTabQueue, tvSummary, tvEmpty;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_explanation);
        api = ApiClient.getClient(this).create(EmployeeApi.class);
        TokenManager tm = TokenManager.getInstance(this);
        String role = tm.getRole();
        String dept = tm.getDepartmentName();
        boolean isManager = "admin".equalsIgnoreCase(role) || "manager".equalsIgnoreCase(role);
        canSeeQueue = isManager || "HR".equalsIgnoreCase(dept);
        myId = tm.getUserId();

        tvTabMine = findViewById(R.id.tvExplTabMine);
        tvTabQueue = findViewById(R.id.tvExplTabQueue);
        tvSummary = findViewById(R.id.tvExplSummary);
        tvEmpty = findViewById(R.id.tvExplEmpty);
        ListView lv = findViewById(R.id.lvExplanations);
        adapter = new ExplAdapter(this);
        lv.setAdapter(adapter);
        lv.setOnItemClickListener((parent, view, position, id) -> {
            ExplanationListResponse.Item item = items.get(position);
            if (mode == MODE_QUEUE && "pending".equals(item.getStatus())) showApproveDialog(item);
            else showDetailDialog(item);
        });

        if (!canSeeQueue) findViewById(R.id.layoutExplTabs).setVisibility(View.GONE);
        tvTabMine.setOnClickListener(v -> switchMode(MODE_MINE));
        tvTabQueue.setOnClickListener(v -> switchMode(MODE_QUEUE));
        findViewById(R.id.btnExplBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnCreateExplanation).setOnClickListener(v -> showCreateDialog());
        load();
    }

    private void switchMode(int newMode) {
        if (mode == newMode) return;
        mode = newMode;
        boolean mine = mode == MODE_MINE;
        tvTabMine.setBackgroundResource(mine ? R.drawable.bg_tab_active : R.drawable.bg_tab_inactive);
        tvTabMine.setTextColor(ContextCompat.getColor(this, mine ? R.color.white : R.color.text_primary));
        tvTabQueue.setBackgroundResource(!mine ? R.drawable.bg_tab_active : R.drawable.bg_tab_inactive);
        tvTabQueue.setTextColor(ContextCompat.getColor(this, !mine ? R.color.white : R.color.text_primary));
        load();
    }

    private void load() {
        Call<ExplanationListResponse> call = mode == MODE_QUEUE ? api.getExplanationQueue() : api.getMyExplanations(myId);
        call.enqueue(new Callback<ExplanationListResponse>() {
            @Override
            public void onResponse(Call<ExplanationListResponse> c, Response<ExplanationListResponse> r) {
                if (r.isSuccessful() && r.body() != null && r.body().isSuccess() && r.body().getData() != null) {
                    items.clear();
                    items.addAll(r.body().getData());
                    adapter.notifyDataSetChanged();
                    tvSummary.setText(mode == MODE_QUEUE
                            ? "Đơn giải trình trong phạm vi duyệt · Tổng: " + items.size()
                            : "Đơn giải trình của tôi · Tổng: " + items.size());
                    tvEmpty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
                } else if (r.code() != 401) {
                    Toast.makeText(ExplanationActivity.this, ApiError.message(r, "Không tải được danh sách"), Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<ExplanationListResponse> c, Throwable t) {
                Toast.makeText(ExplanationActivity.this, "Không tải được danh sách", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showCreateDialog() {
        Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.dialog_explanation_create);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        EditText edtDate = dialog.findViewById(R.id.edtExplDate);
        Spinner spKind = dialog.findViewById(R.id.spExplKind);
        EditText edtIn = dialog.findViewById(R.id.edtExplIn);
        EditText edtOut = dialog.findViewById(R.id.edtExplOut);
        EditText edtReason = dialog.findViewById(R.id.edtExplReason);
        String[] kindLabels = {"Quên chấm công vào", "Quên chấm công ra", "Quên cả vào và ra"};
        String[] kindValues = {"checkin", "checkout", "both"};
        spKind.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, kindLabels));
        edtDate.setOnClickListener(v -> pickDate(edtDate));
        dialog.findViewById(R.id.btnExplCancel).setOnClickListener(v -> dialog.dismiss());
        Button btnSubmit = dialog.findViewById(R.id.btnExplSubmit);
        btnSubmit.setOnClickListener(v -> {
            String date = edtDate.getText().toString().trim();
            String in = edtIn.getText().toString().trim();
            String out = edtOut.getText().toString().trim();
            String reason = edtReason.getText().toString().trim();
            String kind = kindValues[Math.max(0, spKind.getSelectedItemPosition())];
            if (date.isEmpty()) {
                Toast.makeText(this, "Chọn ngày cần giải trình", Toast.LENGTH_SHORT).show();
                return;
            }
            if (("checkin".equals(kind) || "both".equals(kind)) && !in.matches("^([01]\\d|2[0-3]):[0-5]\\d$")) {
                Toast.makeText(this, "Điền giờ vào đề xuất dạng HH:MM", Toast.LENGTH_SHORT).show();
                return;
            }
            if (("checkout".equals(kind) || "both".equals(kind)) && !out.matches("^([01]\\d|2[0-3]):[0-5]\\d$")) {
                Toast.makeText(this, "Điền giờ ra đề xuất dạng HH:MM", Toast.LENGTH_SHORT).show();
                return;
            }
            btnSubmit.setEnabled(false);
            api.createExplanation(new ExplanationCreateRequest(date, kind,
                    in.isEmpty() ? null : in, out.isEmpty() ? null : out, reason))
                    .enqueue(new Callback<ApiResponse>() {
                        @Override
                        public void onResponse(Call<ApiResponse> c, Response<ApiResponse> r) {
                            btnSubmit.setEnabled(true);
                            if (r.isSuccessful() && r.body() != null && r.body().isSuccess()) {
                                Toast.makeText(ExplanationActivity.this, "Đã gửi đơn giải trình", Toast.LENGTH_SHORT).show();
                                dialog.dismiss();
                                if (mode != MODE_MINE) switchMode(MODE_MINE);
                                else load();
                            } else {
                                Toast.makeText(ExplanationActivity.this, ApiError.message(r, "Gửi thất bại"), Toast.LENGTH_LONG).show();
                            }
                        }

                        @Override
                        public void onFailure(Call<ApiResponse> c, Throwable t) {
                            btnSubmit.setEnabled(true);
                            Toast.makeText(ExplanationActivity.this, "Lỗi kết nối: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    });
        });
        dialog.show();
    }

    private void pickDate(EditText target) {
        Calendar cal = Calendar.getInstance();
        String cur = target.getText().toString().trim();
        if (!cur.isEmpty()) {
            try {
                Date d = new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(cur);
                if (d != null) cal.setTime(d);
            } catch (ParseException ignored) { }
        }
        new DatePickerDialog(this, (view, y, m, d) ->
                target.setText(String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, d)),
                cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void showDetailDialog(ExplanationListResponse.Item item) {
        new AlertDialog.Builder(this)
                .setTitle("Chi tiết đơn giải trình")
                .setMessage(describe(item))
                .setPositiveButton("Đóng", null)
                .show();
    }

    private void showApproveDialog(ExplanationListResponse.Item item) {
        new AlertDialog.Builder(this)
                .setTitle("Duyệt đơn giải trình")
                .setMessage(describe(item) + "\n\nDuyệt xong, giờ đề xuất sẽ được bổ sung vào bảng công của nhân viên.")
                .setPositiveButton("Duyệt", (d, w) -> approve(item, "approved"))
                .setNegativeButton("Từ chối", (d, w) -> approve(item, "rejected"))
                .setNeutralButton("Huỷ", null)
                .show();
    }

    private void approve(ExplanationListResponse.Item item, String status) {
        api.approveExplanation(new LeaveApproveRequest(item.getId(), status))
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(Call<ApiResponse> c, Response<ApiResponse> r) {
                        if (r.isSuccessful() && r.body() != null && r.body().isSuccess()) {
                            Toast.makeText(ExplanationActivity.this,
                                    "approved".equals(status) ? "Đã duyệt và bổ sung vào bảng công" : "Đã từ chối đơn",
                                    Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(ExplanationActivity.this, ApiError.message(r, "Thao tác thất bại"), Toast.LENGTH_LONG).show();
                        }
                        load();
                    }

                    @Override
                    public void onFailure(Call<ApiResponse> c, Throwable t) {
                        Toast.makeText(ExplanationActivity.this, "Lỗi kết nối: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    static String kindLabel(String kind) {
        if ("checkin".equals(kind)) return "Quên chấm công vào";
        if ("checkout".equals(kind)) return "Quên chấm công ra";
        return "Quên cả vào và ra";
    }

    private String describe(ExplanationListResponse.Item item) {
        StringBuilder sb = new StringBuilder();
        if (item.getFullName() != null && !item.getFullName().isEmpty()) {
            sb.append("Nhân viên: ").append(item.getFullName());
            if (item.getDeptName() != null && !item.getDeptName().isEmpty()) sb.append(" · Phòng ").append(item.getDeptName());
            sb.append("\n");
        }
        sb.append("Loại: ").append(kindLabel(item.getKind()));
        sb.append("\nNgày: ").append(LeaveActivity.formatDate(item.getWorkDate()));
        if (item.getProposedIn() != null) sb.append("\nGiờ vào đề xuất: ").append(item.getProposedIn().substring(0, 5));
        if (item.getProposedOut() != null) sb.append("\nGiờ ra đề xuất: ").append(item.getProposedOut().substring(0, 5));
        sb.append("\nTrạng thái: ").append(statusLabel(item.getStatus()));
        String processed = processedText(item);
        if (!processed.isEmpty()) sb.append("\n").append(processed);
        if (item.getReason() != null && !item.getReason().isEmpty()) sb.append("\nLý do: ").append(item.getReason());
        return sb.toString();
    }

    static String statusLabel(String status) {
        if ("approved".equals(status)) return "Đã duyệt";
        if ("rejected".equals(status)) return "Bị từ chối";
        return "Chờ duyệt";
    }

    static String processedText(ExplanationListResponse.Item item) {
        String verb;
        if ("approved".equals(item.getStatus())) verb = "Đã duyệt bởi ";
        else if ("rejected".equals(item.getStatus())) verb = "Đã từ chối bởi ";
        else return "";
        String who = item.getApproverName() != null && !item.getApproverName().isEmpty()
                ? item.getApproverName() : "quản lý";
        String when = "";
        String at = item.getProcessedAt();
        if (at != null && at.length() >= 16) {
            when = " · " + LeaveActivity.formatDate(at.substring(0, 10)) + " " + at.substring(11, 16);
        }
        return verb + who + when;
    }

    private class ExplAdapter extends BaseAdapter {
        private final LayoutInflater inflater;

        ExplAdapter(Context ctx) { inflater = LayoutInflater.from(ctx); }

        @Override public int getCount() { return items.size(); }
        @Override public Object getItem(int position) { return items.get(position); }
        @Override public long getItemId(int position) { return items.get(position).getId(); }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View v = convertView;
            if (v == null) v = inflater.inflate(R.layout.item_explanation, parent, false);
            ExplanationListResponse.Item item = items.get(position);
            TextView tvTitle = v.findViewById(R.id.tvExplRowTitle);
            TextView tvBadge = v.findViewById(R.id.tvExplRowBadge);
            TextView tvDetail = v.findViewById(R.id.tvExplRowDetail);
            TextView tvMeta = v.findViewById(R.id.tvExplRowMeta);

            if (mode == MODE_QUEUE && item.getFullName() != null && !item.getFullName().isEmpty()) {
                tvTitle.setText(item.getFullName());
            } else {
                tvTitle.setText(kindLabel(item.getKind()));
            }
            String detail = "Ngày " + LeaveActivity.formatDate(item.getWorkDate());
            if (item.getProposedIn() != null) detail += " · Vào " + item.getProposedIn().substring(0, 5);
            if (item.getProposedOut() != null) detail += " · Ra " + item.getProposedOut().substring(0, 5);
            if (mode == MODE_QUEUE) detail = kindLabel(item.getKind()) + "\n" + detail;
            tvDetail.setText(detail);

            StringBuilder meta = new StringBuilder();
            if (mode == MODE_QUEUE && item.getDeptName() != null && !item.getDeptName().isEmpty()) {
                meta.append("Phòng ").append(item.getDeptName()).append(" · ");
            }
            if (item.getReason() != null && !item.getReason().isEmpty()) meta.append("Lý do: ").append(item.getReason());
            String processed = processedText(item);
            if (mode == MODE_QUEUE && !processed.isEmpty()) {
                if (meta.length() > 0) meta.append("\n");
                meta.append(processed);
            }
            tvMeta.setText(meta.toString());
            tvMeta.setVisibility(meta.length() == 0 ? View.GONE : View.VISIBLE);

            tvBadge.setText(statusLabel(item.getStatus()));
            if ("approved".equals(item.getStatus())) {
                tvBadge.setBackgroundResource(R.drawable.bg_badge_approved);
                tvBadge.setTextColor(ContextCompat.getColor(ExplanationActivity.this, R.color.success));
            } else if ("rejected".equals(item.getStatus())) {
                tvBadge.setBackgroundResource(R.drawable.bg_badge_rejected);
                tvBadge.setTextColor(ContextCompat.getColor(ExplanationActivity.this, R.color.danger));
            } else {
                tvBadge.setBackgroundResource(R.drawable.bg_badge_pending);
                tvBadge.setTextColor(ContextCompat.getColor(ExplanationActivity.this, R.color.warning));
            }
            return v;
        }
    }
}
