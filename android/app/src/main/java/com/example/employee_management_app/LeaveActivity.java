package com.example.employee_management_app;

import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

// Xin nghi phep (nhan vien) + Duyet don nghi (admin/manager).
// Truoc day nut nay chi hien Toast "(ban D lam tiep)" o EmployeeActivity/AdminActivity.
public class LeaveActivity extends AppCompatActivity {
    private final List<LeaveListResponse.Item> items = new ArrayList<>();
    private ArrayAdapter<LeaveListResponse.Item> adapter;
    private EmployeeApi api;
    private boolean isManager;
    private int myId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_leave);
        BottomNavHelper.setup(this, R.id.nav_leave);

        api = ApiClient.getClient(this).create(EmployeeApi.class);
        String role = TokenManager.getInstance(this).getRole();
        isManager = "admin".equalsIgnoreCase(role) || "manager".equalsIgnoreCase(role);
        myId = TokenManager.getInstance(this).getUserId();

        TextView tvTitle = findViewById(R.id.tvLeaveTitle);
        View form = findViewById(R.id.layoutLeaveForm);
        EditText edtStart = findViewById(R.id.edtLeaveStart);
        EditText edtEnd = findViewById(R.id.edtLeaveEnd);
        EditText edtReason = findViewById(R.id.edtLeaveReason);
        Button btnSubmit = findViewById(R.id.btnSubmitLeave);
        ListView lv = findViewById(R.id.lvLeaves);
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, items);
        lv.setAdapter(adapter);

        if (isManager) {
            // Quan ly: an form xin nghi, hien danh sach don cho duyet, bam vao 1 don de duyet/tu choi
            tvTitle.setText("Đơn nghỉ phép chờ duyệt (bấm vào đơn để duyệt)");
            form.setVisibility(View.GONE);
            lv.setOnItemClickListener((parent, view, position, id) ->
                    showApproveDialog(items.get(position)));
        } else {
            tvTitle.setText("Đơn nghỉ phép của tôi");
        }

        btnSubmit.setOnClickListener(v -> {
            String start = edtStart.getText().toString().trim();
            String end = edtEnd.getText().toString().trim();
            String reason = edtReason.getText().toString().trim();
            if (start.isEmpty() || end.isEmpty()) {
                Toast.makeText(this, "Nhập ngày bắt đầu và ngày kết thúc (YYYY-MM-DD)", Toast.LENGTH_SHORT).show();
                return;
            }
            // Validate ngay dung dinh dang va thu tu (server cung kiem tra lai).
            java.text.SimpleDateFormat fmt = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US);
            fmt.setLenient(false);
            try {
                java.util.Date dStart = fmt.parse(start);
                java.util.Date dEnd = fmt.parse(end);
                if (dStart == null || dEnd == null
                        || !fmt.format(dStart).equals(start) || !fmt.format(dEnd).equals(end)) {
                    throw new java.text.ParseException("bad date", 0);
                }
                if (dEnd.before(dStart)) {
                    Toast.makeText(this, "Ngày kết thúc không được trước ngày bắt đầu", Toast.LENGTH_SHORT).show();
                    return;
                }
            } catch (java.text.ParseException e) {
                Toast.makeText(this, "Ngày phải đúng định dạng YYYY-MM-DD và là ngày có thật", Toast.LENGTH_SHORT).show();
                return;
            }
            btnSubmit.setEnabled(false); // tranh bam 2 lan gui trung don
            api.createLeave(new LeaveCreateRequest(myId, start, end, reason))
                    .enqueue(new Callback<ApiResponse>() {
                        @Override
                        public void onResponse(Call<ApiResponse> c, Response<ApiResponse> r) {
                            btnSubmit.setEnabled(true);
                            if (r.isSuccessful() && r.body() != null && r.body().isSuccess()) {
                                Toast.makeText(LeaveActivity.this, "Đã gửi đơn nghỉ phép", Toast.LENGTH_SHORT).show();
                                loadLeaves();
                            } else {
                                Toast.makeText(LeaveActivity.this,
                                        ApiError.message(r, "Gửi đơn thất bại"), Toast.LENGTH_LONG).show();
                            }
                        }

                        @Override
                        public void onFailure(Call<ApiResponse> c, Throwable t) {
                            btnSubmit.setEnabled(true);
                            Toast.makeText(LeaveActivity.this, "Lỗi kết nối: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    });
        });

        loadLeaves();
    }

    private void loadLeaves() {
        Call<LeaveListResponse> call = isManager ? api.getPendingLeaves() : api.getMyLeaves(myId);
        call.enqueue(new Callback<LeaveListResponse>() {
            @Override
            public void onResponse(Call<LeaveListResponse> c, Response<LeaveListResponse> r) {
                if (r.isSuccessful() && r.body() != null && r.body().isSuccess()
                        && r.body().getData() != null) {
                    items.clear();
                    items.addAll(r.body().getData());
                    adapter.notifyDataSetChanged();
                    if (items.isEmpty()) {
                        Toast.makeText(LeaveActivity.this,
                                isManager ? "Không có đơn nào chờ duyệt" : "Bạn chưa có đơn nghỉ phép nào",
                                Toast.LENGTH_SHORT).show();
                    }
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

    private void showApproveDialog(LeaveListResponse.Item item) {
        new AlertDialog.Builder(this)
                .setTitle("Duyệt đơn nghỉ phép")
                .setMessage(item.toString())
                .setPositiveButton("Duyệt", (d, w) -> approve(item, "approved"))
                .setNegativeButton("Từ chối", (d, w) -> approve(item, "rejected"))
                .setNeutralButton("Huỷ", null)
                .show();
    }

    private void approve(LeaveListResponse.Item item, String status) {
        // Gỡ đơn khỏi danh sách ngay de khong bam duyet lan 2 trong luc cho server.
        items.remove(item);
        adapter.notifyDataSetChanged();
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
}
