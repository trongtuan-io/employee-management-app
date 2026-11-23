package com.example.employee_management_app;

import android.content.res.Configuration;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ProposalApprovalActivity extends AppCompatActivity {

    private Spinner spStatus, spType;
    private TextView tvMessage;
    private ListView lvProposals;
    private EmployeeApi api;
    private final List<ProposalItem> proposals = new ArrayList<>();
    private ArrayAdapter<ProposalItem> adapter;

    private final String[] statusKeys = {"", "pending", "approved", "rejected"};
    private final String[] statusLabels = {"Tất cả trạng thái", "Chờ duyệt", "Đã duyệt", "Đã từ chối"};

    private final String[] typeKeys = {"", "new_hire", "salary_change", "department_transfer", "promotion", "termination"};
    private final String[] typeLabels = {"Tất cả loại đề xuất", "Tuyển mới nhân viên", "Thay đổi lương cơ bản", "Chuyển phòng ban", "Thăng chức / Đổi chức vụ", "Nghỉ việc"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_proposal_approval);
        applyWindowInsets();

        findViewById(R.id.btnBackProposal).setOnClickListener(v -> finish());
        spStatus = findViewById(R.id.spProposalStatus);
        spType = findViewById(R.id.spProposalType);
        tvMessage = findViewById(R.id.tvProposalMessage);
        lvProposals = findViewById(R.id.lvProposals);

        api = ApiClient.getClient(this).create(EmployeeApi.class);

        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, proposals);
        lvProposals.setAdapter(adapter);

        spStatus.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, statusLabels));
        spType.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, typeLabels));

        AdapterView.OnItemSelectedListener listener = new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                loadProposals();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        };

        spStatus.setOnItemSelectedListener(listener);
        spType.setOnItemSelectedListener(listener);

        lvProposals.setOnItemClickListener((parent, view, position, id) -> {
            if (position < 0 || position >= proposals.size()) return;
            showDetailDialog(proposals.get(position));
        });

        loadProposals();
    }

    private void loadProposals() {
        String status = statusKeys[Math.max(0, spStatus.getSelectedItemPosition())];
        String type = typeKeys[Math.max(0, spType.getSelectedItemPosition())];

        tvMessage.setText("Đang tải danh sách đề xuất...");
        proposals.clear();
        adapter.notifyDataSetChanged();

        api.getProposals(status.isEmpty() ? null : status, type.isEmpty() ? null : type)
                .enqueue(new Callback<ProposalListResponse>() {
                    @Override
                    public void onResponse(Call<ProposalListResponse> c, Response<ProposalListResponse> r) {
                        if (isFinishing() || isDestroyed()) return;
                        if (r.isSuccessful() && r.body() != null && r.body().isSuccess() && r.body().getData() != null) {
                            proposals.addAll(r.body().getData());
                            adapter.notifyDataSetChanged();
                            tvMessage.setText(proposals.isEmpty() ? "Không có đề xuất nào" : "Tổng số: " + proposals.size() + " đề xuất");
                        } else {
                            tvMessage.setText(ApiError.message(r, "Không tải được danh sách đề xuất"));
                        }
                    }

                    @Override
                    public void onFailure(Call<ProposalListResponse> c, Throwable t) {
                        if (!isFinishing()) {
                            tvMessage.setText("Lỗi kết nối: " + t.getMessage());
                        }
                    }
                });
    }

    private void showDetailDialog(ProposalItem item) {
        StringBuilder sb = new StringBuilder();
        sb.append("Loại đề xuất: ").append(item.getTypeLabel()).append("\n");
        if (item.getEmployeeName() != null) sb.append("Nhân viên: ").append(item.getEmployeeName()).append("\n");
        if (item.getDeptName() != null) sb.append("Phòng ban: ").append(item.getDeptName()).append("\n");
        if (item.getPosTitle() != null) sb.append("Chức vụ: ").append(item.getPosTitle()).append("\n");
        if (item.getOldValue() != null) sb.append("Giá trị cũ: ").append(item.getOldValue()).append("\n");
        if (item.getNewValue() != null) sb.append("Giá trị mới (đề xuất): ").append(item.getNewValue()).append("\n");
        if (item.getReason() != null && !item.getReason().isEmpty()) sb.append("Lý do: ").append(item.getReason()).append("\n");
        sb.append("Người đề xuất (HR): ").append(item.getCreatorName() != null ? item.getCreatorName() : "HR").append("\n");
        sb.append("Trạng thái: ").append(item.getStatusLabel());
        if (item.getApproverName() != null) {
            sb.append("\nNgười duyệt: ").append(item.getApproverName());
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle("Chi tiết Đề xuất #" + item.getId())
                .setMessage(sb.toString())
                .setNegativeButton("Đóng", null);

        if ("pending".equals(item.getStatus())) {
            builder.setPositiveButton("Phê duyệt", (dlg, which) -> processProposal(item, "approved"));
            builder.setNeutralButton("Từ chối", (dlg, which) -> processProposal(item, "rejected"));
        }

        builder.show();
    }

    private void processProposal(ProposalItem item, String status) {
        api.approveProposal(new ProposalApproveRequest(item.getId(), status))
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(Call<ApiResponse> c, Response<ApiResponse> r) {
                        if (isFinishing() || isDestroyed()) return;
                        if (r.isSuccessful() && r.body() != null && r.body().isSuccess()) {
                            Toast.makeText(ProposalApprovalActivity.this,
                                    "approved".equals(status) ? "Đã phê duyệt đề xuất!" : "Đã từ chối đề xuất!",
                                    Toast.LENGTH_SHORT).show();
                            loadProposals();
                        } else {
                            String msg = ApiError.message(r, "Không thực hiện được thao tác");
                            Toast.makeText(ProposalApprovalActivity.this, msg, Toast.LENGTH_LONG).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<ApiResponse> c, Throwable t) {
                        if (!isFinishing()) {
                            Toast.makeText(ProposalApprovalActivity.this, "Lỗi kết nối: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    private void applyWindowInsets() {
        View root = findViewById(R.id.proposalRoot);
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
