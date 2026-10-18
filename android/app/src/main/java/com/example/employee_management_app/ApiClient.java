package com.example.employee_management_app;

import android.content.Context;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class ApiClient {
    private static final String BASE_URL = "http://10.0.2.2/employee-api/";
    // Thêm hàm getter này
    public static String getBaseUrl() {
        return BASE_URL;
    }
    private static Retrofit retrofit = null;

    public static String baseUrl() {
        return BASE_URL;
    }

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

            Gson gson = new GsonBuilder().setLenient().create();

            retrofit = new Retrofit.Builder()
                    .baseUrl(BASE_URL)
                    .client(client)
                    .addConverterFactory(GsonConverterFactory.create(gson))
                    .build();
        }
        return retrofit;
    }
}
