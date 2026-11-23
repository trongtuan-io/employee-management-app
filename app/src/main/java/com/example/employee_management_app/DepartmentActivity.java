package com.example.employee_management_app;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.content.res.Configuration;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
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

/** CRUD departments, with a route to staff filtered by the selected department. */
public class DepartmentActivity extends AppCompatActivity {
    private final List<DepartmentListResponse.Item> departments = new ArrayList<>();
    private ArrayAdapter<String> adapter;
    private final List<String> names = new ArrayList<>();
    private EmployeeApi api;
    private TextView message;
    private Button addButton;
    private boolean busy;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_departments);
        applyWindowInsets();
        findViewById(R.id.btnBackDepartments).setOnClickListener(v -> finish());
        setTitle("Quản lý phòng ban");
        api = ApiClient.getClient(this).create(EmployeeApi.class);
        message = findViewById(R.id.tvDepartmentStatus);
        addButton = findViewById(R.id.btnAddDepartment);
        boolean admin = "admin".equalsIgnoreCase(TokenManager.getInstance(this).getRole());
        addButton.setVisibility(admin ? View.VISIBLE : View.GONE);
        addButton.setOnClickListener(v -> editDepartment(null));
        ListView list = findViewById(R.id.lvDepartments);
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, names);
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, position, id) -> {
            if (position >= departments.size()) return;
            DepartmentListResponse.Item item = departments.get(position);
            new AlertDialog.Builder(this).setTitle(item.getName())
                    .setItems(admin ? new String[]{"Xem nhân viên", "Sửa", "Xóa"} : new String[]{"Xem nhân viên"},
                            (dlg, which) -> {
                                if (which == 0) {
                                    Intent intent = new Intent(this, StaffListActivity.class);
                                    intent.putExtra("DEPARTMENT_ID", item.getId());
                                    intent.putExtra("DEPARTMENT_NAME", item.getName());
                                    startActivity(intent);
                                } else if (which == 1) editDepartment(item);
                                else confirmDelete(item);
                            }).show();
        });
        findViewById(R.id.btnReloadDepartments).setOnClickListener(v -> load());
        load();
    }
    // Tránh che nút phía trên bởi status bar / camera cutout trên Android mới.
    private void applyWindowInsets() {
        View root = findViewById(R.id.departmentRoot);
        int left = root.getPaddingLeft(), top = root.getPaddingTop();
        int right = root.getPaddingRight(), bottom = root.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            Insets safe = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout());
            view.setPadding(left + safe.left, top + safe.top,
                    right + safe.right, bottom + safe.bottom);
            return insets;
        });
        boolean dark = (getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView())
                .setAppearanceLightStatusBars(!dark);
        ViewCompat.requestApplyInsets(root);
    }

    @Override protected void onResume() { super.onResume(); if (api != null && !busy) load(); }
    private void load() {
        message.setText("Đang tải phòng ban…");
        api.listDepartments().enqueue(new Callback<DepartmentListResponse>() {
            @Override public void onResponse(Call<DepartmentListResponse> call, Response<DepartmentListResponse> res) {
                if (isFinishing()) return;
                if (!res.isSuccessful() || res.body() == null || !res.body().isSuccess() || res.body().getData() == null) {
                    message.setText("Lỗi tải phòng ban (HTTP " + res.code() + "). Chạm Tải lại."); return;
                }
                departments.clear(); names.clear();
                for (DepartmentListResponse.Item item : res.body().getData()) {
                    departments.add(item);
                    names.add(item.getName() + " (" + item.getMemberCount() + " NV)\n" + item.getDescription());
                }
                adapter.notifyDataSetChanged();
                message.setText(names.isEmpty() ? "Chưa có phòng ban" : "Chạm một phòng ban để xem nhân viên / sửa / xóa");
            }
            @Override public void onFailure(Call<DepartmentListResponse> call, Throwable error) {
                if (!isFinishing()) message.setText("Không kết nối được API. Chạm Tải lại.");
            }
        });
    }
    private void editDepartment(DepartmentListResponse.Item original) {
        EditText name = new EditText(this); name.setHint("Tên phòng ban"); name.setSingleLine(true);
        EditText desc = new EditText(this); desc.setHint("Mô tả");
        if (original != null) { name.setText(original.getName()); desc.setText(original.getDescription()); }
        LinearLayout content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(32, 8, 32, 8); content.addView(name); content.addView(desc);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(original == null ? "Thêm phòng ban" : "Sửa phòng ban")
                .setView(content).setNegativeButton("Hủy", null)
                .setPositiveButton("Lưu", null).create();
        dialog.setOnShowListener(v -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(click -> {
            String n = name.getText().toString().trim();
            if (n.isEmpty() || n.length() > 250) { name.setError("Tên không được trống, tối đa 250 ký tự"); return; }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
            DepartmentMutationRequest req = new DepartmentMutationRequest(
                    TokenManager.getInstance(this).getUserId(), original == null ? 0 : original.getId(), n, desc.getText().toString().trim());
            Call<ApiResponse> call = original == null ? api.createDepartment(req) : api.updateDepartment(req);
            call.enqueue(new Callback<ApiResponse>() {
                @Override public void onResponse(Call<ApiResponse> c, Response<ApiResponse> res) {
                    if (res.isSuccessful() && res.body() != null && res.body().isSuccess()) {
                        dialog.dismiss(); load();
                    } else {
                        name.setError("Không lưu được: HTTP " + res.code() + " (trùng tên / thiếu quyền)");
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                    }
                }
                @Override public void onFailure(Call<ApiResponse> c, Throwable t) {
                    name.setError("Không kết nối API"); dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                }
            });
        }));
        dialog.show();
    }
    private void confirmDelete(DepartmentListResponse.Item item) {
        new AlertDialog.Builder(this).setTitle("Xóa phòng ban?")
                .setMessage("Chỉ xóa được phòng không còn nhân viên. Không xóa dữ liệu nhân viên.")
                .setNegativeButton("Hủy", null)
                .setPositiveButton("Xóa", (d, w) -> api.deleteDepartment(new DepartmentMutationRequest(
                        TokenManager.getInstance(this).getUserId(), item.getId(), null, null))
                        .enqueue(new Callback<ApiResponse>() {
                            @Override public void onResponse(Call<ApiResponse> c, Response<ApiResponse> res) {
                                if (!res.isSuccessful() || res.body() == null || !res.body().isSuccess())
                                    Toast.makeText(DepartmentActivity.this, "Không thể xóa (phòng đang có người / thiếu quyền)", Toast.LENGTH_LONG).show();
                                load();
                            }
                            @Override public void onFailure(Call<ApiResponse> c, Throwable e) {
                                Toast.makeText(DepartmentActivity.this, "Mất kết nối", Toast.LENGTH_SHORT).show();
                            }
                        })).show();
    }
}
