package com.example.employee_management_app;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.Part;
import retrofit2.http.Query;
import retrofit2.http.Header;

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

    @GET("employees.php?action=list")
    Call<EmployeeListResponse> getEmployees(@Query("search") String search,
                                            @Query("dept_id") Integer departmentId,
                                            @Query("page") int page,
                                            @Query("limit") int limit,
                                            @Query("sort") String sort);

    @GET("employees.php?action=list")
    Call<EmployeeListResponse> getEmployees(@Query("search") String search,
                                            @Query("dept_id") Integer departmentId,
                                            @Query("page") int page,
                                            @Query("limit") int limit,
                                            @Query("sort") String sort,
                                            @Query("search_mode") String searchMode);

    @GET("employees.php?action=positions")
    Call<PositionListResponse> getPositions();

    @GET("departments.php?action=list")
    Call<DepartmentListResponse> listDepartments();

    @POST("departments.php?action=create")
    Call<ApiResponse> createDepartment(@Body DepartmentMutationRequest request);

    @POST("departments.php?action=update")
    Call<ApiResponse> updateDepartment(@Body DepartmentMutationRequest request);

    @POST("departments.php?action=delete")
    Call<ApiResponse> deleteDepartment(@Body DepartmentMutationRequest request);

    @Multipart
    @POST("import_employees.php")
    Call<EmployeeImportResponse> importEmployees(@Part("requester_id") okhttp3.RequestBody requesterId,
                                                  @Part okhttp3.MultipartBody.Part file);


    // Task 22: keep this separate from the team's other employee APIs.
    @Multipart
    @POST("import_batches.php?action=sync")
    Call<EmployeeBatchResponse> syncImportBatch(
            @Part("requester_id") okhttp3.RequestBody requesterId,
            @Part("batch_id") okhttp3.RequestBody batchId,
            @Part okhttp3.MultipartBody.Part file);

    @POST("import_batches.php?action=rollback")
    Call<EmployeeBatchResponse> rollbackImportBatch(@Body ImportBatchActionRequest request);

    @GET("import_batches.php?action=list")
    Call<EmployeeBatchResponse> getImportBatchHistory(@Query("requester_id") int requesterId);

    @GET("import_batches.php?action=detail")
    Call<EmployeeBatchResponse> getImportBatchDetail(
            @Query("requester_id") int requesterId, @Query("batch_id") long batchId);

    @GET("employees.php?action=departments")
    Call<DepartmentListResponse> getDepartments();

    // Sua ho so: nhan vien chi doi duoc ho ten, admin doi duoc het
    @POST("employees.php?action=update")
    Call<ApiResponse> updateEmployee(@Body UpdateEmployeeRequest request);

    // Khoa / mo tai khoan (admin moi phong / manager cung phong)
    @POST("employees.php?action=set-status")
    Call<ApiResponse> setStatus(@Body SetStatusRequest request);

    // Only admin may delete employee; server verifies role and identity.
    @POST("employees.php?action=delete")
    Call<ApiResponse> deleteEmployee(@Body DeleteEmployeeRequest request);

    // Upload anh dai dien
    @Multipart
    @POST("employees.php?action=upload-avatar")
    Call<AvatarResponse> uploadAvatar(
            @Part("employee_id") okhttp3.RequestBody employeeId,
            @Part("requester_id") okhttp3.RequestBody requesterId,
            @Part okhttp3.MultipartBody.Part avatar);
}
