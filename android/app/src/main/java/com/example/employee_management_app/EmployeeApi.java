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

    // Danh ba toi thieu cho man Bang luong (admin/Ke toan/Truong phong HR):
    // trang Quan ly nhan vien da siet theo phong nen spinner luong dung duong rieng.
    @GET("employees.php?action=directory")
    Call<EmployeeListResponse> getDirectory();

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

    // Chuc vu theo phong ban (kem chuc vu dung chung)
    @GET("employees.php?action=positions")
    Call<PositionListResponse> getPositions(@Query("dept_id") int departmentId);

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

    @GET("employees.php?action=departments")
    Call<DepartmentListResponse> getDepartments();

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

    // Nghi phep (leaves_salaries.php)
    @POST("leaves_salaries.php?action=leave-create")
    Call<ApiResponse> createLeave(@Body LeaveCreateRequest request);

    @GET("leaves_salaries.php?action=leave-list")
    Call<LeaveListResponse> getMyLeaves(@Query("employee_id") int employeeId);

    @GET("leaves_salaries.php?action=leave-list")
    Call<LeaveListResponse> getPendingLeaves();

    @POST("leaves_salaries.php?action=leave-approve")
    Call<ApiResponse> approveLeave(@Body LeaveApproveRequest request);

    // Luong (leaves_salaries.php)
    @GET("leaves_salaries.php?action=salary-list")
    Call<SalaryListResponse> getSalaries(@Query("employee_id") int employeeId, @Query("year") int year);

    // Tinh luong tu dong theo ngay cong cho ca thang (chi admin/manager)
    @POST("leaves_salaries.php?action=salary-generate")
    Call<SalaryGenerateResponse> generateSalaries(@Body SalaryGenerateRequest request);

    // Sua phu cap/tang ca/thuong/khau tru cua bang luong nhap (chi admin/manager)
    @POST("leaves_salaries.php?action=salary-adjust")
    Call<ApiResponse> adjustSalary(@Body SalaryAdjustRequest request);

    // Chot 1 dong bang luong (chi admin/manager)
    @POST("leaves_salaries.php?action=salary-finalize")
    Call<ApiResponse> finalizeSalary(@Body SalaryFinalizeRequest request);

    // Mo lai bang luong da chot (chi admin)
    @POST("leaves_salaries.php?action=salary-unfinalize")
    Call<ApiResponse> unfinalizeSalary(@Body SalaryFinalizeRequest request);

    // Trang chu: trang thai cham cong hom nay + bang tin cong ty
    @GET("attendances.php?action=today")
    Call<AttendanceTodayResponse> getAttendanceToday();

    @GET("announcements.php?action=list")
    Call<AnnouncementResponse> getAnnouncements();
}
