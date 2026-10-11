package com.example.employee_management_app;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

// Danh sach nhan vien cho ADMIN: bam vao 1 nguoi -> mo man sua ho so (duoc doi ca mail/SDT)
public class StaffListActivity extends AppCompatActivity {
    private static final String STATE_SEARCH = "staff_search";

    private final List<EmployeeListResponse.Item> items = new ArrayList<>();
    private ArrayAdapter<EmployeeListResponse.Item> adapter;
    private String currentSearch = "";
    private Call<EmployeeListResponse> staffCall;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_staff_list);
        setTitle("Quản lý nhân viên");

        EditText edtSearchEmployee = findViewById(R.id.edtSearchEmployee);
        Button btnSearchEmployee = findViewById(R.id.btnSearchEmployee);
        if (savedInstanceState != null) {
            currentSearch = savedInstanceState.getString(STATE_SEARCH, "");
        }
        edtSearchEmployee.setText(currentSearch);
        btnSearchEmployee.setOnClickListener(v ->
                loadStaff(edtSearchEmployee.getText().toString().trim()));

        ListView lv = findViewById(R.id.lvStaff);
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, items);
        lv.setAdapter(adapter);
        lv.setOnItemClickListener((parent, view, position, id) -> {
            Intent i = new Intent(this, ProfileActivity.class);
            i.putExtra("EMPLOYEE_ID", items.get(position).getId());
            startActivity(i);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Tai lan dau va tai lai sau khi sua ho so, giu nguyen bo loc da tim.
        loadStaff(currentSearch);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putString(STATE_SEARCH, currentSearch);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onDestroy() {
        if (staffCall != null) {
            staffCall.cancel();
            staffCall = null;
        }
        super.onDestroy();
    }

    private void loadStaff(String search) {
        // Khong gui trung yeu cau dang chay; huy yeu cau cu khi doi tu khoa.
        if (staffCall != null) {
            if (search.equals(currentSearch)) return;
            staffCall.cancel();
        }
        currentSearch = search;
        items.clear();
        adapter.notifyDataSetChanged();

        EmployeeApi api = ApiClient.getClient(this).create(EmployeeApi.class);
        staffCall = api.getEmployees(currentSearch);
        staffCall.enqueue(new Callback<EmployeeListResponse>() {
            @Override
            public void onResponse(Call<EmployeeListResponse> c, Response<EmployeeListResponse> r) {
                // Ket qua cu khong duoc ghi de danh sach cua tu khoa moi.
                if (c != staffCall || c.isCanceled()) return;
                staffCall = null;
                if (r.isSuccessful() && r.body() != null && r.body().isSuccess()
                        && r.body().getData() != null) {
                    items.addAll(r.body().getData());
                    adapter.notifyDataSetChanged();
                    if (items.isEmpty()) {
                        Toast.makeText(StaffListActivity.this,
                                "Không tìm thấy nhân viên", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    String message = r.isSuccessful()
                            ? "Không tải được danh sách. Vui lòng thử lại."
                            : "Lỗi API (HTTP " + r.code() + "). Vui lòng thử lại.";
                    Toast.makeText(StaffListActivity.this, message, Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(Call<EmployeeListResponse> c, Throwable t) {
                if (c != staffCall || c.isCanceled()) return;
                staffCall = null;
                Toast.makeText(StaffListActivity.this,
                        "Không tải được danh sách. Kiểm tra kết nối và thử lại.",
                        Toast.LENGTH_LONG).show();
            }
        });
    }
}
