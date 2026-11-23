package com.example.employee_management_app;

import java.util.Map;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.Part;
import retrofit2.http.Query;
import retrofit2.http.Streaming;

/** HR-only endpoints. BOD approvals are intentionally not exposed here. */
public interface HrApi {
    @GET("employees.php?action=detail") Call<Map<String,Object>> profile(@Query("id") int id);
    @POST("employees.php?action=update") Call<Map<String,Object>> updateProfile(@Body Map<String,Object> fields);
    @GET("hr_proposals.php?action=list") Call<Map<String,Object>> proposals();
    @POST("hr_proposals.php?action=create") Call<Map<String,Object>> createProposal(@Body Map<String,Object> body);
    @POST("hr_proposals.php?action=submit") Call<Map<String,Object>> submitProposal(@Body Map<String,Object> body);
    @POST("hr_proposals.php?action=review-internal") Call<Map<String,Object>> reviewRecruitmentInternal(@Body Map<String,Object> body);
    @POST("hr_proposals.php?action=update-draft") Call<Map<String,Object>> updateRecruitmentDraft(@Body Map<String,Object> body);
    @POST("hr_proposals.php?action=activate") Call<Map<String,Object>> activateRecruitment(@Body Map<String,Object> body);
    @GET("hr_positions.php?action=manager-options") Call<Map<String,Object>> managerOptions(@Query("employee_id") int employeeId);
    @GET("departments.php?action=list") Call<Map<String,Object>> departments();
    @GET("hr_positions.php?action=list") Call<Map<String,Object>> positions();
    @POST("hr_positions.php?action=create") Call<Map<String,Object>> createPosition(@Body Map<String,Object> body);
    @POST("hr_positions.php?action=update") Call<Map<String,Object>> updatePosition(@Body Map<String,Object> body);
    @POST("hr_positions.php?action=deactivate") Call<Map<String,Object>> deactivatePosition(@Body Map<String,Object> body);
    @GET("hr_history.php") Call<Map<String,Object>> history(@Query("employee_id") Integer employeeId);
    @GET("hr_documents.php?action=list") Call<Map<String,Object>> documents(@Query("employee_id") int employeeId);
    @GET("hr_documents.php?action=summary") Call<Map<String,Object>> documentSummary(@Query("employee_id") int employeeId);
    @Multipart @POST("hr_documents.php?action=upload") Call<Map<String,Object>> uploadDocument(
            @Part("employee_id") RequestBody employeeId,
            @Part("document_type") RequestBody documentType,
            @Part MultipartBody.Part file);
    @POST("hr_documents.php?action=review") Call<Map<String,Object>> reviewDocument(
            @Query("employee_id") int employeeId, @Body Map<String,Object> body);
    @Streaming @GET("hr_documents.php?action=download") Call<ResponseBody> downloadDocument(
            @Query("employee_id") int employeeId,@Query("id") int documentId);
}
