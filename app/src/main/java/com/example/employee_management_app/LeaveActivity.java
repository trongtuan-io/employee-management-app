package com.example.employee_management_app;

import android.app.DatePickerDialog;
import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
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

// Man Xin nghi phep theo mau "Xin nghi 1 buoi": the thong ke trang thai,
// loc theo trang thai + khoang ngay, badge trang thai, nut Tao yeu cau.
// Quan ly/HR them tab "Cho duyet": don cho duyet xep tren cung; don DA xu ly
// van o lai trong tab nay kem nguoi duyet + thoi diem xu ly de truy vet
// (bam vao chi xem chi tiet, khong duyet lai duoc).
public class LeaveActivity extends AppCompatActivity {
    private static final int MODE_MINE = 0;
    private static final int MODE_QUEUE = 1;

    private final List<LeaveListResponse.Item> allItems = new ArrayList<>();
    private final List<LeaveListResponse.Item> shownItems = new ArrayList<>();
    private LeaveAdapter adapter;
    private EmployeeApi api;
    private boolean isManager;
    private boolean canSeeQueue;
    private int myId;
    private int mode = MODE_MINE;
    private String statusFilter = "all";
    private String dateFrom = "";
    private String dateTo = "";

    private TextView tvTabMine, tvTabQueue, tvSummary, tvEmpty;
    private TextView tvCountAll, tvCountPending, tvCountApproved, tvCountRejected;
    private View cardAll, cardPending, cardApproved, cardRejected;
    private EditText edtFilterFrom, edtFilterTo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_leave);
        BottomNavHelper.setup(this, R.id.nav_leave);

        api = ApiClient.getClient(this).create(EmployeeApi.class);
        TokenManager tm = TokenManager.getInstance(this);
        String role = tm.getRole();
        String dept = tm.getDepartmentName();
        isManager = "admin".equalsIgnoreCase(role) || "manager".equalsIgnoreCase(role);
        // Hang cho duyet: quan ly thay theo phong, nhan vien HR thay de theo doi (API quyet).
        canSeeQueue = isManager || "HR".equalsIgnoreCase(dept);
        myId = tm.getUserId();

        tvTabMine = findViewById(R.id.tvTabMine);
        tvTabQueue = findViewById(R.id.tvTabQueue);
        tvSummary = findViewById(R.id.tvLeaveSummary);
        tvEmpty = findViewById(R.id.tvLeaveEmpty);
        tvCountAll = findViewById(R.id.tvCountAll);
        tvCountPending = findViewById(R.id.tvCountPending);
        tvCountApproved = findViewById(R.id.tvCountApproved);
        tvCountRejected = findViewById(R.id.tvCountRejected);
        cardAll = findViewById(R.id.cardStatAll);
        cardPending = findViewById(R.id.cardStatPending);
        cardApproved = findViewById(R.id.cardStatApproved);
        cardRejected = findViewById(R.id.cardStatRejected);
        edtFilterFrom = findViewById(R.id.edtFilterFrom);
        edtFilterTo = findViewById(R.id.edtFilterTo);

        ListView lv = findViewById(R.id.lvLeaves);
        adapter = new LeaveAdapter(this);
        lv.setAdapter(adapter);
        lv.setOnItemClickListener((parent, view, position, id) -> {
            LeaveListResponse.Item item = shownItems.get(position);
            // O tab duyet: chi don dang CHO duyet moi mo hop thoai Duyet/Tu choi;
            // don da xu ly (va don o tab cua toi) chi xem chi tiet de truy vet.
            if (mode == MODE_QUEUE && "pending".equals(item.getStatus())) showApproveDialog(item);
            else showDetailDialog(item);
        });

        if (!canSeeQueue) findViewById(R.id.layoutLeaveTabs).setVisibility(View.GONE);
        tvTabMine.setOnClickListener(v -> switchMode(MODE_MINE));
        tvTabQueue.setOnClickListener(v -> switchMode(MODE_QUEUE));

        cardAll.setOnClickListener(v -> setStatusFilter("all"));
        cardPending.setOnClickListener(v -> setStatusFilter("pending"));
        cardApproved.setOnClickListener(v -> setStatusFilter("approved"));
        cardRejected.setOnClickListener(v -> setStatusFilter("rejected"));

        edtFilterFrom.setOnClickListener(v -> pickDate(edtFilterFrom, true));
        edtFilterTo.setOnClickListener(v -> pickDate(edtFilterTo, false));
        findViewById(R.id.btnClearFilter).setOnClickListener(v -> {
            dateFrom = "";
            dateTo = "";
            edtFilterFrom.setText("");
            edtFilterTo.setText("");
            applyFilters();
        });

        findViewById(R.id.btnRefreshLeave).setOnClickListener(v -> loadLeaves());
        findViewById(R.id.btnCreateLeave).setOnClickListener(v -> showCreateDialog());

        loadLeaves();
    }

    private void switchMode(int newMode) {
        if (mode == newMode) return;
        mode = newMode;
        statusFilter = "all";
        styleTabs();
        styleCards();
        loadLeaves();
    }

    private void styleTabs() {
        boolean mine = mode == MODE_MINE;
        tvTabMine.setBackgroundResource(mine ? R.drawable.bg_tab_active : R.drawable.bg_tab_inactive);
        tvTabMine.setTextColor(ContextCompat.getColor(this, mine ? R.color.white : R.color.text_primary));
        tvTabQueue.setBackgroundResource(!mine ? R.drawable.bg_tab_active : R.drawable.bg_tab_inactive);
        tvTabQueue.setTextColor(ContextCompat.getColor(this, !mine ? R.color.white : R.color.text_primary));
    }

    private void setStatusFilter(String filter) {
        statusFilter = filter;
        styleCards();
        applyFilters();
    }

    private void styleCards() {
        cardAll.setBackgroundResource("all".equals(statusFilter) ? R.drawable.bg_stat_all_selected : R.drawable.bg_stat_all);
        cardPending.setBackgroundResource("pending".equals(statusFilter) ? R.drawable.bg_stat_pending_selected : R.drawable.bg_stat_pending);
        cardApproved.setBackgroundResource("approved".equals(statusFilter) ? R.drawable.bg_stat_approved_selected : R.drawable.bg_stat_approved);
        cardRejected.setBackgroundResource("rejected".equals(statusFilter) ? R.drawable.bg_stat_rejected_selected : R.drawable.bg_stat_rejected);
    }

    private void loadLeaves() {
        Call<LeaveListResponse> call = mode == MODE_QUEUE ? api.getPendingLeaves() : api.getMyLeaves(myId);
        call.enqueue(new Callback<LeaveListResponse>() {
            @Override
            public void onResponse(Call<LeaveListResponse> c, Response<LeaveListResponse> r) {
                if (r.isSuccessful() && r.body() != null && r.body().isSuccess()
                        && r.body().getData() != null) {
                    allItems.clear();
                    allItems.addAll(r.body().getData());
                    updateCounts();
                    applyFilters();
                } else if (r.code() != 401) {
                    Toast.makeText(LeaveActivity.this,
                            ApiError.message(r, "Không tải được danh sách đơn"), Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<LeaveListResponse> c, Throwable t) {
                Toast.makeText(LeaveActivity.this, "Không tải được danh sách đơn", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateCounts() {
        int pending = 0, approved = 0, rejected = 0;
        for (LeaveListResponse.Item it : allItems) {
            if ("pending".equals(it.getStatus())) pending++;
            else if ("approved".equals(it.getStatus())) approved++;
            else if ("rejected".equals(it.getStatus())) rejected++;
        }
        tvCountAll.setText(String.valueOf(allItems.size()));
        tvCountPending.setText(String.valueOf(pending));
        tvCountApproved.setText(String.valueOf(approved));
        tvCountRejected.setText(String.valueOf(rejected));
    }

    private void applyFilters() {
        shownItems.clear();
        for (LeaveListResponse.Item it : allItems) {
            if (!"all".equals(statusFilter) && !statusFilter.equals(it.getStatus())) continue;
            // Loc khoang ngay: don chong lấn [dateFrom, dateTo] thi giu lai.
            if (!dateFrom.isEmpty() && it.getEndDate() != null && it.getEndDate().compareTo(dateFrom) < 0) continue;
            if (!dateTo.isEmpty() && it.getStartDate() != null && it.getStartDate().compareTo(dateTo) > 0) continue;
            shownItems.add(it);
        }
        adapter.notifyDataSetChanged();
        tvSummary.setText("Danh sách yêu cầu xin nghỉ · Tổng: " + shownItems.size() + " yêu cầu");
        tvEmpty.setVisibility(shownItems.isEmpty() ? View.VISIBLE : View.GONE);
    }

    // ---- Tao yeu cau xin nghi ----
    private void showCreateDialog() {
        Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.dialog_leave_create);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        EditText edtStart = dialog.findViewById(R.id.edtLeaveStart);
        EditText edtEnd = dialog.findViewById(R.id.edtLeaveEnd);
        EditText edtReason = dialog.findViewById(R.id.edtLeaveReason);
        Button btnSubmit = dialog.findViewById(R.id.btnDialogSubmitLeave);
        dialog.findViewById(R.id.btnDialogCancelLeave).setOnClickListener(v -> dialog.dismiss());
        edtStart.setOnClickListener(v -> pickDate(edtStart, true));
        edtEnd.setOnClickListener(v -> pickDate(edtEnd, true));
        btnSubmit.setOnClickListener(v -> {
            String start = edtStart.getText().toString().trim();
            String end = edtEnd.getText().toString().trim();
            String reason = edtReason.getText().toString().trim();
            if (start.isEmpty() || end.isEmpty()) {
                Toast.makeText(this, "Chọn ngày bắt đầu và ngày kết thúc", Toast.LENGTH_SHORT).show();
                return;
            }
            if (end.compareTo(start) < 0) {
                Toast.makeText(this, "Ngày kết thúc không được trước ngày bắt đầu", Toast.LENGTH_SHORT).show();
                return;
            }
            btnSubmit.setEnabled(false); // tranh bam 2 lan gui trung don
            api.createLeave(new LeaveCreateRequest(myId, start, end, reason))
                    .enqueue(new Callback<ApiResponse>() {
                        @Override
                        public void onResponse(Call<ApiResponse> c, Response<ApiResponse> r) {
                            btnSubmit.setEnabled(true);
                            if (r.isSuccessful() && r.body() != null && r.body().isSuccess()) {
                                Toast.makeText(LeaveActivity.this, "Đã gửi yêu cầu xin nghỉ", Toast.LENGTH_SHORT).show();
                                dialog.dismiss();
                                if (mode != MODE_MINE) switchMode(MODE_MINE);
                                else loadLeaves();
                            } else {
                                Toast.makeText(LeaveActivity.this,
                                        ApiError.message(r, "Gửi yêu cầu thất bại"), Toast.LENGTH_LONG).show();
                            }
                        }

                        @Override
                        public void onFailure(Call<ApiResponse> c, Throwable t) {
                            btnSubmit.setEnabled(true);
                            Toast.makeText(LeaveActivity.this, "Lỗi kết nối: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    });
        });
        dialog.show();
    }

    private void showDetailDialog(LeaveListResponse.Item item) {
        new AlertDialog.Builder(this)
                .setTitle("Chi tiết yêu cầu xin nghỉ")
                .setMessage(describe(item))
                .setPositiveButton("Đóng", null)
                .show();
    }

    private void showApproveDialog(LeaveListResponse.Item item) {
        new AlertDialog.Builder(this)
                .setTitle("Duyệt đơn nghỉ phép")
                .setMessage(describe(item))
                .setPositiveButton("Duyệt", (d, w) -> approve(item, "approved"))
                .setNegativeButton("Từ chối", (d, w) -> approve(item, "rejected"))
                .setNeutralButton("Huỷ", null)
                .show();
    }

    private String describe(LeaveListResponse.Item item) {
        StringBuilder sb = new StringBuilder();
        if (item.getFullName() != null && !item.getFullName().isEmpty()) {
            sb.append("Nhân viên: ").append(item.getFullName());
            if (item.getDeptName() != null && !item.getDeptName().isEmpty()) {
                sb.append(" · Phòng ").append(item.getDeptName());
            }
            sb.append("\n");
        }
        sb.append("Ngày nghỉ: ").append(formatDate(item.getStartDate()));
        if (item.getEndDate() != null && !item.getEndDate().equals(item.getStartDate())) {
            sb.append(" → ").append(formatDate(item.getEndDate()));
        }
        sb.append("\nNgày tạo: ").append(formatDateTime(item.getCreatedAt()));
        sb.append("\nTrạng thái: ").append(statusLabel(item.getStatus()));
        String processed = processedText(item);
        if (!processed.isEmpty()) sb.append("\n").append(processed);
        if (item.getReason() != null && !item.getReason().isEmpty()) {
            sb.append("\nLý do: ").append(item.getReason());
        }
        return sb.toString();
    }

    private void approve(LeaveListResponse.Item item, String status) {
        api.approveLeave(new LeaveApproveRequest(item.getId(), status))
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(Call<ApiResponse> c, Response<ApiResponse> r) {
                        if (r.isSuccessful() && r.body() != null && r.body().isSuccess()) {
                            Toast.makeText(LeaveActivity.this,
                                    "approved".equals(status) ? "Đã duyệt đơn" : "Đã từ chối đơn",
                                    Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(LeaveActivity.this,
                                    ApiError.message(r, "Thao tác thất bại"), Toast.LENGTH_LONG).show();
                        }
                        loadLeaves();
                    }

                    @Override
                    public void onFailure(Call<ApiResponse> c, Throwable t) {
                        Toast.makeText(LeaveActivity.this, "Lỗi kết nối: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    // ---- Tien ich ngay thang ----
    private void pickDate(EditText target, boolean reloadAfter) {
        Calendar cal = Calendar.getInstance();
        String cur = target.getText().toString().trim();
        if (!cur.isEmpty()) {
            try {
                Date d = new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(cur);
                if (d != null) cal.setTime(d);
            } catch (ParseException ignored) { }
        }
        new DatePickerDialog(this, (view, y, m, d) -> {
            String val = String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, d);
            target.setText(val);
            if (target == edtFilterFrom) { dateFrom = val; applyFilters(); }
            else if (target == edtFilterTo) { dateTo = val; applyFilters(); }
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
    }

    static String formatDate(String ymd) {
        if (ymd == null || ymd.length() < 10) return ymd == null ? "" : ymd;
        return ymd.substring(8, 10) + "/" + ymd.substring(5, 7) + "/" + ymd.substring(0, 4);
    }

    static String formatDateTime(String dt) {
        if (dt == null || dt.length() < 10) return dt == null ? "" : dt;
        return formatDate(dt.substring(0, 10));
    }

    // "Da duyet boi <ten> · dd/MM/yyyy HH:mm" cho don da xu ly; rong neu don
    // con cho duyet hoac thieu du lieu (don xu ly tu truoc khi co cot thoi diem).
    static String processedText(LeaveListResponse.Item item) {
        String verb;
        if ("approved".equals(item.getStatus())) verb = "Đã duyệt bởi ";
        else if ("rejected".equals(item.getStatus())) verb = "Đã từ chối bởi ";
        else return "";
        String who = item.getApproverName() != null && !item.getApproverName().isEmpty()
                ? item.getApproverName() : "quản lý";
        String when = "";
        String at = item.getProcessedAt();
        if (at != null && at.length() >= 16) {
            when = " · " + formatDate(at.substring(0, 10)) + " " + at.substring(11, 16);
        }
        return verb + who + when;
    }

    static String statusLabel(String status) {
        if ("approved".equals(status)) return "Đã duyệt";
        if ("rejected".equals(status)) return "Bị từ chối";
        return "Chờ duyệt";
    }

    // ---- Adapter dong don nghi voi badge trang thai ----
    private class LeaveAdapter extends BaseAdapter {
        private final LayoutInflater inflater;

        LeaveAdapter(Context ctx) { inflater = LayoutInflater.from(ctx); }

        @Override public int getCount() { return shownItems.size(); }
        @Override public Object getItem(int position) { return shownItems.get(position); }
        @Override public long getItemId(int position) { return shownItems.get(position).getId(); }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View v = convertView;
            if (v == null) v = inflater.inflate(R.layout.item_leave, parent, false);
            LeaveListResponse.Item item = shownItems.get(position);
            TextView tvTitle = v.findViewById(R.id.tvRowTitle);
            TextView tvBadge = v.findViewById(R.id.tvRowBadge);
            TextView tvDates = v.findViewById(R.id.tvRowDates);
            TextView tvMeta = v.findViewById(R.id.tvRowMeta);

            if (mode == MODE_QUEUE && item.getFullName() != null && !item.getFullName().isEmpty()) {
                tvTitle.setText(item.getFullName());
            } else if (item.getReason() != null && !item.getReason().isEmpty()) {
                tvTitle.setText(item.getReason());
            } else {
                tvTitle.setText("Xin nghỉ phép");
            }

            String dates = "Ngày nghỉ: " + formatDate(item.getStartDate());
            if (item.getEndDate() != null && !item.getEndDate().equals(item.getStartDate())) {
                dates += " → " + formatDate(item.getEndDate());
            }
            tvDates.setText(dates);

            StringBuilder meta = new StringBuilder("Ngày tạo: " + formatDateTime(item.getCreatedAt()));
            if (mode == MODE_QUEUE && item.getDeptName() != null && !item.getDeptName().isEmpty()) {
                meta.append(" · Phòng ").append(item.getDeptName());
            }
            if (mode == MODE_QUEUE && item.getReason() != null && !item.getReason().isEmpty()) {
                meta.append("\nLý do: ").append(item.getReason());
            }
            String processed = processedText(item);
            if (mode == MODE_QUEUE && !processed.isEmpty()) {
                meta.append("\n").append(processed);
            }
            tvMeta.setText(meta.toString());

            tvBadge.setText(statusLabel(item.getStatus()));
            if ("approved".equals(item.getStatus())) {
                tvBadge.setBackgroundResource(R.drawable.bg_badge_approved);
                tvBadge.setTextColor(ContextCompat.getColor(LeaveActivity.this, R.color.success));
            } else if ("rejected".equals(item.getStatus())) {
                tvBadge.setBackgroundResource(R.drawable.bg_badge_rejected);
                tvBadge.setTextColor(ContextCompat.getColor(LeaveActivity.this, R.color.danger));
            } else {
                tvBadge.setBackgroundResource(R.drawable.bg_badge_pending);
                tvBadge.setTextColor(ContextCompat.getColor(LeaveActivity.this, R.color.warning));
            }
            return v;
        }
    }
}
