package com.example.employee_management_app;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

public interface AuthApi {
    @POST("auth.php?action=login")
    Call<LoginResponse> login(@Body LoginRequest request);

    @POST("auth.php?action=change-password")
    Call<ApiResponse> changePassword(@Body ChangePasswordRequest request);

    @POST("auth.php?action=forgot-password")
    Call<ForgotResponse> forgotPassword(@Body ForgotRequest request);

    @POST("auth.php?action=verify-code")
    Call<VerifyResponse> verifyCode(@Body VerifyRequest request);

    @POST("auth.php?action=reset-password")
    Call<ApiResponse> resetPassword(@Body ResetRequest request);
}
