package com.example.employee_management_app;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
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
    private final List<EmployeeListResponse.Item> items = new ArrayList<>();
    private ArrayAdapter<EmployeeListResponse.Item> adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_staff_list);
        BottomNavHelper.setup(this, R.id.nav_staff);
        setTitle("Quản lý nhân viên");

        Button btnDept = findViewById(R.id.btnManageDepartments);
        btnDept.setOnClickListener(v ->
                startActivity(new Intent(this, DepartmentActivity.class)));
        Button btnImport = findViewById(R.id.btnImportEmployees);
        btnImport.setOnClickListener(v ->
                startActivity(new Intent(this, ImportEmployeesActivity.class)));

        ListView lv = findViewById(R.id.lvStaff);
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, items);
        lv.setAdapter(adapter);
        lv.setOnItemClickListener((parent, view, position, id) -> {
            Intent i = new Intent(this, ProfileActivity.class);
            i.putExtra("EMPLOYEE_ID", items.get(position).getId());
            startActivity(i);
        });
        loadStaff();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadStaff(); // ve lai la tai moi (sau khi sua xong)
    }

    private void loadStaff() {
        EmployeeApi api = ApiClient.getClient(this).create(EmployeeApi.class);
        api.getEmployees("").enqueue(new Callback<EmployeeListResponse>() {
            @Override
            public void onResponse(Call<EmployeeListResponse> c, Response<EmployeeListResponse> r) {
                if (r.isSuccessful() && r.body() != null && r.body().isSuccess()
                        && r.body().getData() != null) {
                    items.clear();
                    items.addAll(r.body().getData());
                    adapter.notifyDataSetChanged();
                }
            }

            @Override
            public void onFailure(Call<EmployeeListResponse> c, Throwable t) {
                Toast.makeText(StaffListActivity.this, "Không tải được danh sách", Toast.LENGTH_SHORT).show();
            }
        });
    }
}
