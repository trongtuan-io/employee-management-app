package com.example.employee_management_app;

import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

// Xem bang luong cua chinh minh (moi vai tro). Chuc nang tao bang luong da go bo
// theo yeu cau - bang luong chi con de xem.
public class SalaryActivity extends AppCompatActivity {
    private final List<SalaryListResponse.Item> items = new ArrayList<>();
    private ArrayAdapter<SalaryListResponse.Item> adapter;
    private EmployeeApi api;
    private int myId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_salary);
        BottomNavHelper.setup(this, R.id.nav_salary);

        api = ApiClient.getClient(this).create(EmployeeApi.class);
        myId = TokenManager.getInstance(this).getUserId();

        ListView lv = findViewById(R.id.lvSalaries);
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, items);
        lv.setAdapter(adapter);

        loadSalaries();
    }

    private void loadSalaries() {
        int year = Calendar.getInstance().get(Calendar.YEAR);
        api.getSalaries(myId, year).enqueue(new Callback<SalaryListResponse>() {
            @Override
            public void onResponse(Call<SalaryListResponse> c, Response<SalaryListResponse> r) {
                if (r.isSuccessful() && r.body() != null && r.body().isSuccess()
                        && r.body().getData() != null) {
                    items.clear();
                    items.addAll(r.body().getData());
                    adapter.notifyDataSetChanged();
                    if (items.isEmpty()) {
                        Toast.makeText(SalaryActivity.this, "Chưa có bảng lương nào trong năm nay", Toast.LENGTH_SHORT).show();
                    }
                } else if (r.code() != 401) {
                    Toast.makeText(SalaryActivity.this,
                            ApiError.message(r, "Không tải được bảng lương"), Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<SalaryListResponse> c, Throwable t) {
                Toast.makeText(SalaryActivity.this, "Không tải được bảng lương", Toast.LENGTH_SHORT).show();
            }
        });
    }
}
