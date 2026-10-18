package com.example.employee_management_app;

import java.util.List;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.POST;
import retrofit2.http.Query;

public interface AttendanceApi {

    @POST("attendances.php?action=checkin")
    Call<ResponseBody> checkIn(
            @Header("Authorization") String token,
            @Body CheckinRequest request
    );

    @POST("attendances.php?action=checkout")
    Call<ResponseBody> checkOut(
            @Header("Authorization") String token,
            @Body CheckinRequest request
    );

    @GET("attendances.php?action=me")
    Call<AttendanceResponse> getMyAttendance(
            @Header("Authorization") String token,
            @Query("month") int month
    );

    @GET("attendances.php?action=shifts")
    Call<AttendanceResponse> getShifts(
            @Header("Authorization") String token
    );
    @GET("attendances.php?action=history")
    Call<ResponseBody> getAttendanceHistory(
            @Header("Authorization") String token,
            @Query("employee_id") int employeeId,
            @Query("month") int month,
            @Query("year") int year
    );
    @GET("attendances.php?action=report")
    Call<ApiResponse<List<AttendanceResponse>>> getAttendanceReport(
            @Header("Authorization") String token,
            @Query("date") String date,
            @Query("dept") String dept
    );
}
