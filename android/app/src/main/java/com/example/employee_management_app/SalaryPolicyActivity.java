package com.example.employee_management_app;

import android.content.Context;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

// Phu cap CO DINH theo chinh sach: cac khoan tu dong cong vao phu cap khi
// chay tinh luong tao dong moi (an trua, xang xe, dien thoai...). Ap dung
// toan cong ty hoac mot phong cu the. Admin + Ke toan truong duoc sua;
// nhan vien Ke toan chi xem (server buoc quyen o salary-policy-save/delete).
public class SalaryPolicyActivity extends AppCompatActivity {
    private final List<SalaryPoliciesResponse.Item> items = new ArrayList<>();
    private final List<DepartmentListResponse.Item> departments = new ArrayList<>();
    private PolicyAdapter adapter;
    private EmployeeApi api;
    private boolean canEdit;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_salary_policy);
        api = ApiClient.getClient(this).create(EmployeeApi.class);
        canEdit = getIntent().getBooleanExtra("can_edit", false);

        ListView lv = findViewById(R.id.lvPolicies);
        adapter = new PolicyAdapter(this);
        lv.setAdapter(adapter);
        lv.setOnItemClickListener((parent, view, position, id) -> {
            if (canEdit) showEditDialog(items.get(position));
            else Toast.makeText(this, "Chỉ Admin và Kế toán trưởng được sửa phụ cấp cố định", Toast.LENGTH_SHORT).show();
        });

        findViewById(R.id.btnPolicyBack).setOnClickListener(v -> finish());
        View btnAdd = findViewById(R.id.btnPolicyAdd);
        btnAdd.setVisibility(canEdit ? View.VISIBLE : View.GONE);
        btnAdd.setOnClickListener(v -> showEditDialog(null));

        ((TextView) findViewById(R.id.tvPolicyNote)).setText(canEdit
                ? "Các khoản dưới đây tự động cộng vào phụ cấp khi tính lương tạo bảng lương mới cho tháng. Dòng lương đã có sẵn thì giữ nguyên, không bị ghi đè."
                : "Các khoản dưới đây tự động cộng vào phụ cấp khi tính lương tạo bảng lương mới. Chỉ Admin và Kế toán trưởng được thay đổi.");

        loadPolicies();
        loadDepartments();
    }

    private void loadPolicies() {
        api.getSalaryPolicies().enqueue(new Callback<SalaryPoliciesResponse>() {
            @Override
            public void onResponse(Call<SalaryPoliciesResponse> c, Response<SalaryPoliciesResponse> r) {
                if (r.isSuccessful() && r.body() != null && r.body().isSuccess() && r.body().getData() != null) {
                    items.clear();
                    items.addAll(r.body().getData());
                    adapter.notifyDataSetChanged();
                } else if (r.code() != 401) {
                    Toast.makeText(SalaryPolicyActivity.this,
                            ApiError.message(r, "Không tải được danh sách phụ cấp"), Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<SalaryPoliciesResponse> c, Throwable t) {
                Toast.makeText(SalaryPolicyActivity.this, "Không tải được danh sách phụ cấp", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadDepartments() {
        api.listDepartments().enqueue(new Callback<DepartmentListResponse>() {
            @Override
            public void onResponse(Call<DepartmentListResponse> c, Response<DepartmentListResponse> r) {
                if (r.isSuccessful() && r.body() != null && r.body().isSuccess() && r.body().getData() != null) {
                    departments.clear();
                    departments.addAll(r.body().getData());
                }
            }

            @Override
            public void onFailure(Call<DepartmentListResponse> c, Throwable t) { }
        });
    }

    private void showEditDialog(SalaryPoliciesResponse.Item existing) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(48, 24, 48, 8);

        EditText etName = new EditText(this);
        etName.setHint("Tên khoản phụ cấp (vd: Ăn trưa)");
        if (existing != null) etName.setText(existing.getName());
        EditText etAmount = new EditText(this);
        etAmount.setHint("Số tiền / tháng");
        etAmount.setInputType(InputType.TYPE_CLASS_NUMBER);
        if (existing != null) etAmount.setText(String.valueOf(Math.round(SalaryListResponse.Item.num(existing.getAmount()))));

        Spinner spDept = new Spinner(this);
        List<DepartmentListResponse.Item> deptChoices = new ArrayList<>();
        deptChoices.add(new DepartmentListResponse.Item(0, "Toàn công ty"));
        deptChoices.addAll(departments);
        ArrayAdapter<DepartmentListResponse.Item> deptAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, deptChoices);
        spDept.setAdapter(deptAdapter);
        if (existing != null && existing.getDepartmentId() != null) {
            for (int k = 0; k < deptChoices.size(); k++) {
                if (deptChoices.get(k).getId() == existing.getDepartmentId()) { spDept.setSelection(k); break; }
            }
        }
        CheckBox cbActive = new CheckBox(this);
        cbActive.setText("Đang áp dụng");
        cbActive.setChecked(existing == null || existing.isActive());

        box.addView(etName);
        box.addView(etAmount);
        box.addView(spDept);
        box.addView(cbActive);

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(existing == null ? "Thêm phụ cấp cố định" : "Sửa phụ cấp cố định")
                .setView(box)
                .setPositiveButton("Lưu", (dialog, which) -> {
                    String name = etName.getText().toString().trim();
                    String rawAmount = etAmount.getText().toString().trim().replace(".", "").replace(",", "").replace(" ", "");
                    double amount;
                    try { amount = Double.parseDouble(rawAmount); } catch (NumberFormatException e) { amount = -1; }
                    if (name.isEmpty() || amount < 0) {
                        Toast.makeText(this, "Nhập tên khoản và số tiền hợp lệ", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    DepartmentListResponse.Item sel = (DepartmentListResponse.Item) spDept.getSelectedItem();
                    Integer deptId = sel != null && sel.getId() > 0 ? sel.getId() : null;
                    Integer id = existing != null ? existing.getId() : null;
                    api.saveSalaryPolicy(new SalaryPolicySaveRequest(id, name, amount, deptId, cbActive.isChecked() ? 1 : 0))
                            .enqueue(new Callback<ApiResponse>() {
                                @Override
                                public void onResponse(Call<ApiResponse> c, Response<ApiResponse> r) {
                                    if (r.isSuccessful() && r.body() != null && r.body().isSuccess()) {
                                        Toast.makeText(SalaryPolicyActivity.this, "Đã lưu khoản phụ cấp", Toast.LENGTH_SHORT).show();
                                        loadPolicies();
                                    } else if (r.code() != 401) {
                                        Toast.makeText(SalaryPolicyActivity.this,
                                                ApiError.message(r, "Không lưu được"), Toast.LENGTH_SHORT).show();
                                    }
                                }

                                @Override
                                public void onFailure(Call<ApiResponse> c, Throwable t) {
                                    Toast.makeText(SalaryPolicyActivity.this, "Không lưu được", Toast.LENGTH_SHORT).show();
                                }
                            });
                })
                .setNegativeButton("Đóng", null);
        if (existing != null) {
            builder.setNeutralButton("Xoá", (dialog, which) -> confirmDelete(existing));
        }
        builder.show();
    }

    private void confirmDelete(SalaryPoliciesResponse.Item item) {
        new AlertDialog.Builder(this)
                .setTitle("Xoá khoản phụ cấp?")
                .setMessage("Khoản \"" + item.getName() + "\" sẽ bị xoá khỏi chính sách. Các bảng lương đã tạo trước đó không thay đổi.")
                .setPositiveButton("Xoá", (dialog, which) ->
                        api.deleteSalaryPolicy(new SalaryFinalizeRequest(item.getId()))
                                .enqueue(new Callback<ApiResponse>() {
                                    @Override
                                    public void onResponse(Call<ApiResponse> c, Response<ApiResponse> r) {
                                        if (r.isSuccessful() && r.body() != null && r.body().isSuccess()) {
                                            Toast.makeText(SalaryPolicyActivity.this, "Đã xoá khoản phụ cấp", Toast.LENGTH_SHORT).show();
                                            loadPolicies();
                                        } else if (r.code() != 401) {
                                            Toast.makeText(SalaryPolicyActivity.this,
                                                    ApiError.message(r, "Không xoá được"), Toast.LENGTH_SHORT).show();
                                        }
                                    }

                                    @Override
                                    public void onFailure(Call<ApiResponse> c, Throwable t) {
                                        Toast.makeText(SalaryPolicyActivity.this, "Không xoá được", Toast.LENGTH_SHORT).show();
                                    }
                                }))
                .setNegativeButton("Huỷ", null)
                .show();
    }

    private class PolicyAdapter extends BaseAdapter {
        private final LayoutInflater inflater;

        PolicyAdapter(Context ctx) { inflater = LayoutInflater.from(ctx); }

        @Override public int getCount() { return items.size(); }
        @Override public Object getItem(int position) { return items.get(position); }
        @Override public long getItemId(int position) { return items.get(position).getId(); }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View v = convertView;
            if (v == null) v = inflater.inflate(R.layout.item_leave, parent, false);
            SalaryPoliciesResponse.Item item = items.get(position);
            TextView tvTitle = v.findViewById(R.id.tvRowTitle);
            TextView tvBadge = v.findViewById(R.id.tvRowBadge);
            TextView tvDates = v.findViewById(R.id.tvRowDates);
            TextView tvMeta = v.findViewById(R.id.tvRowMeta);

            tvTitle.setText(item.getName());
            tvDates.setText(item.getDeptName() != null && !item.getDeptName().isEmpty()
                    ? "Chỉ áp dụng cho phòng " + item.getDeptName() : "Áp dụng toàn công ty");
            tvMeta.setText(SalaryListResponse.Item.money(item.getAmount()) + " / tháng");
            if (item.isActive()) {
                tvBadge.setText("Đang áp dụng");
                tvBadge.setBackgroundResource(R.drawable.bg_badge_approved);
                tvBadge.setTextColor(ContextCompat.getColor(SalaryPolicyActivity.this, R.color.success));
            } else {
                tvBadge.setText("Tạm tắt");
                tvBadge.setBackgroundResource(R.drawable.bg_badge_rejected);
                tvBadge.setTextColor(ContextCompat.getColor(SalaryPolicyActivity.this, R.color.danger));
            }
            return v;
        }
    }
}
