package com.example.employee_management_app;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.Part;
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

    // Danh sach nhan vien (Admin)
    @GET("employees.php?action=list")
    Call<EmployeeListResponse> getEmployees(@Query("search") String search);

    // Sua ho so: nhan vien chi doi duoc ho ten, admin doi duoc het
    @POST("employees.php?action=update")
    Call<ApiResponse> updateEmployee(@Body UpdateEmployeeRequest request);

    // Khoa / mo tai khoan (admin moi phong / manager cung phong)
    @POST("employees.php?action=set-status")
    Call<ApiResponse> setStatus(@Body SetStatusRequest request);

    // Upload anh dai dien
    @Multipart
    @POST("employees.php?action=upload-avatar")
    Call<AvatarResponse> uploadAvatar(
            @Part("employee_id") okhttp3.RequestBody employeeId,
            @Part("requester_id") okhttp3.RequestBody requesterId,
            @Part okhttp3.MultipartBody.Part avatar);
}
