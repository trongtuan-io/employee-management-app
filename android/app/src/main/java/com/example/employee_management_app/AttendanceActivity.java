package com.example.employee_management_app;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Calendar;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class AttendanceActivity extends AppCompatActivity {

    private TextView tvGpsStatus, tvResult, tvEmpName, tvEmpId;
    private Button btnCheckIn, btnCheckOut;
    private RecyclerView rvHistory;
    private AttendanceHistoryAdapter historyAdapter;

    private FusedLocationProviderClient fusedLocationClient;
    private TokenManager tokenManager;
    private AttendanceApi attendanceApi;

    private double currentLat = 0.0;
    private double currentLng = 0.0;
    private boolean isLocationLoaded = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_attendance);

        // Ánh xạ View
        tvGpsStatus = findViewById(R.id.tv_gps_status);
        tvResult = findViewById(R.id.tv_result);
        tvEmpName = findViewById(R.id.tv_emp_name);
        tvEmpId = findViewById(R.id.tv_emp_id);
        btnCheckIn = findViewById(R.id.btn_check_in);
        btnCheckOut = findViewById(R.id.btn_check_out);

        rvHistory = findViewById(R.id.rv_history);
        rvHistory.setLayoutManager(new LinearLayoutManager(this));
        historyAdapter = new AttendanceHistoryAdapter();
        rvHistory.setAdapter(historyAdapter);

        tokenManager = TokenManager.getInstance(this);
        attendanceApi = ApiClient.getClient(this).create(AttendanceApi.class);
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        // Tải dữ liệu ban đầu
        loadEmployeeInfo();
        getCurrentLocation();
        loadAttendanceHistory();

        // Xử lý sự kiện Check-in & Check-out
        btnCheckIn.setOnClickListener(v -> performCheckIn());
        btnCheckOut.setOnClickListener(v -> performCheckOut());
    }

    private void loadEmployeeInfo() {
        int employeeId = tokenManager.getUserId();
        if (employeeId == -1) return;

        EmployeeApi employeeApi = ApiClient.getClient(this).create(EmployeeApi.class);
        employeeApi.getDetail(employeeId).enqueue(new Callback<EmployeeDetailResponse>() {
            @Override
            public void onResponse(Call<EmployeeDetailResponse> c, Response<EmployeeDetailResponse> r) {
                if (r.isSuccessful() && r.body() != null && r.body().isSuccess() && r.body().getData() != null) {
                    EmployeeDetailResponse.Detail d = r.body().getData();
                    tvEmpName.setText("Nhân viên: " + d.getFullName());
                    tvEmpId.setText("Mã NV: " + d.getUsername());
                }
            }

            @Override
            public void onFailure(Call<EmployeeDetailResponse> c, Throwable t) {
                tvEmpName.setText("Nhân viên: Không tải được tên");
            }
        });
    }

    private void loadAttendanceHistory() {
        int employeeId = tokenManager.getUserId();
        if (employeeId == -1) return;

        Calendar calendar = Calendar.getInstance();
        int month = calendar.get(Calendar.MONTH) + 1;
        int year = calendar.get(Calendar.YEAR);

        String token = "Bearer " + tokenManager.getToken();

        attendanceApi.getAttendanceHistory(token, employeeId, month, year).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        String jsonString = response.body().string();
                        JSONObject jsonObject = new JSONObject(jsonString);

                        if (jsonObject.optBoolean("success", false) && jsonObject.has("data")) {
                            JSONArray dataArray = jsonObject.getJSONArray("data");
                            historyAdapter.setHistoryData(dataArray);
                        } else {
                            JSONArray dataArray = new JSONArray(jsonString);
                            historyAdapter.setHistoryData(dataArray);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                Toast.makeText(AttendanceActivity.this, "Không thể tải lịch sử chấm công", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void getCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 100);
            return;
        }

        tvGpsStatus.setText("Trạng thái GPS: Đang lấy vị trí...");

        fusedLocationClient.getLastLocation().addOnSuccessListener(this, location -> {
            if (location != null) {
                currentLat = location.getLatitude();
                currentLng = location.getLongitude();
                isLocationLoaded = true;
                tvGpsStatus.setText("Vị trí GPS: " + String.format("%.6f", currentLat) + ", " + String.format("%.6f", currentLng));
            } else {
                fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                        .addOnSuccessListener(this, newLocation -> {
                            if (newLocation != null) {
                                currentLat = newLocation.getLatitude();
                                currentLng = newLocation.getLongitude();
                                isLocationLoaded = true;
                                tvGpsStatus.setText("Vị trí GPS: " + String.format("%.6f", currentLat) + ", " + String.format("%.6f", currentLng));
                            } else {
                                tvGpsStatus.setText("Không lấy được tọa độ GPS.");
                            }
                        });
            }
        });
    }

    private String parseErrorMessage(Response<?> response) {
        try {
            if (response.errorBody() != null) {
                String errorStr = response.errorBody().string();
                JSONObject jsonObject = new JSONObject(errorStr);
                if (jsonObject.has("error")) {
                    return jsonObject.getString("error");
                }
                return errorStr;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "Thất bại, vui lòng thử lại!";
    }

    private void performCheckIn() {
        if (!isLocationLoaded) {
            Toast.makeText(this, "Đang lấy vị trí GPS, vui lòng đợi...", Toast.LENGTH_SHORT).show();
            getCurrentLocation();
            return;
        }

        String token = "Bearer " + tokenManager.getToken();
        CheckinRequest request = new CheckinRequest(tokenManager.getUserId(), currentLat, currentLng);

        attendanceApi.checkIn(token, request).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                if (response.isSuccessful()) {
                    Toast.makeText(AttendanceActivity.this, "Check-in thành công!", Toast.LENGTH_SHORT).show();
                    tvResult.setText("Trạng thái: Đã check-in thành công.");
                    loadAttendanceHistory(); // Cập nhật lại lịch sử
                } else {
                    String errorMsg = parseErrorMessage(response);
                    Toast.makeText(AttendanceActivity.this, errorMsg, Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                Toast.makeText(AttendanceActivity.this, "Lỗi kết nối: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void performCheckOut() {
        String token = "Bearer " + tokenManager.getToken();
        CheckinRequest request = new CheckinRequest(tokenManager.getUserId(), currentLat, currentLng);

        attendanceApi.checkOut(token, request).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                if (response.isSuccessful()) {
                    Toast.makeText(AttendanceActivity.this, "Check-out thành công!", Toast.LENGTH_SHORT).show();
                    tvResult.setText("Trạng thái: Đã check-out thành công.");
                    loadAttendanceHistory(); // Cập nhật lại lịch sử
                } else {
                    String errorMsg = parseErrorMessage(response);
                    Toast.makeText(AttendanceActivity.this, errorMsg, Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                Toast.makeText(AttendanceActivity.this, "Lỗi kết nối: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 100) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                getCurrentLocation();
            } else {
                Toast.makeText(this, "Cần cấp quyền vị trí để chấm công!", Toast.LENGTH_SHORT).show();
                tvGpsStatus.setText("Trạng thái GPS: Bị từ chối quyền vị trí.");
            }
        }
    }
}