package com.example.employee_management_app;

import android.content.Context;

import java.io.IOException;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class ApiClient {
    private static final String BASE_URL = "http://172.16.8.36/employee-api/";
    private static Retrofit retrofit = null;

    public static Retrofit getClient(Context context) {
        if (retrofit == null) {
            
            // Tạo Interceptor để tự động đính kèm JWT Token vào Header của mọi API
            OkHttpClient client = new OkHttpClient.Builder().addInterceptor(new Interceptor() {
                @Override
                public Response intercept(Chain chain) throws IOException {
                    Request.Builder requestBuilder = chain.request().newBuilder();
                    
                    // Lấy token đã lưu
                    String token = TokenManager.getInstance(context).getToken();
                    if (token != null && !token.isEmpty()) {
                        // Thêm Bearer token vào Header Authorization
                        requestBuilder.addHeader("Authorization", "Bearer " + token);
                    }
                    
                    return chain.proceed(requestBuilder.build());
                }
            }).build();

            retrofit = new Retrofit.Builder()
                    .baseUrl(BASE_URL)
                    .client(client)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
        }
        return retrofit;
    }
}
