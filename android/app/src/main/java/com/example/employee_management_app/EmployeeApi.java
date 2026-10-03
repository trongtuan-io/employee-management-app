package com.example.employee_management_app;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Query;

public interface EmployeeApi {
    // Ho so chi tiet 1 nhan vien
    @GET("employees.php?action=detail")
    Call<EmployeeDetailResponse> getDetail(@Query("id") int employeeId);

    // So lieu tong quan cho Admin
    @GET("leaves_salaries.php?action=dashboard")
    Call<DashboardResponse> getDashboard();

    // Cham cong vao / ra
    @POST("attendances.php?action=checkin")
    Call<AttendanceResponse> checkIn(@Body CheckinRequest request);

    @POST("attendances.php?action=checkout")
    Call<AttendanceResponse> checkOut(@Body CheckinRequest request);
}
