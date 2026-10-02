package com.example.employee_management_app;

import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import okhttp3.OkHttpClient;
import java.util.concurrent.TimeUnit;

public class ApiClient {
    // Đổi sang IP thật của máy bạn trên mạng WiFi thay vì 10.0.2.2
    private static final String BASE_URL = "http://192.168.11.5/employee-api/";
    private static Retrofit retrofit = null;

    public static Retrofit getClient() {
        if (retrofit == null) {
            retrofit = new Retrofit.Builder()
                    .baseUrl(BASE_URL)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
        }
        return retrofit;
    }
}
