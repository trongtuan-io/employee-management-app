package com.example.employee_management_app;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

public interface AuthApi {
    @POST("auth.php?action=login")
    Call<LoginResponse> login(@Body LoginRequest request);

    @POST("auth.php?action=change-password")
    Call<ApiResponse> changePassword(@Body ChangePasswordRequest request);
}
