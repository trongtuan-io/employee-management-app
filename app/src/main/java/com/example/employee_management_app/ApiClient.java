package com.example.employee_management_app;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;

import java.io.IOException;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class ApiClient {
    // IP backend lay tu BuildConfig (doc tu apiUrl trong local.properties
    // hoac bien moi truong API_URL). Moi may tu cau hinh, khong sua code.
    private static final String BASE_URL = BuildConfig.API_URL;
    private static Retrofit retrofit = null;
    private static Context appContext = null;

    public static String baseUrl() {
        return BASE_URL;
    }

    public static Retrofit getClient(Context context) {
        // Chi giu application context, tranh ro ri Activity vao client tinh.
        appContext = context.getApplicationContext();
        if (retrofit == null) {
            final Context appCtx = appContext;
            // Interceptor: dinh kem token vao moi request; gap 401 (phien het han /
            // tai khoan bi khoa) thi xoa phien va dua nguoi dung ve man dang nhap.
            OkHttpClient client = new OkHttpClient.Builder().addInterceptor(new Interceptor() {
                @Override
                public Response intercept(Chain chain) throws IOException {
                    Request original = chain.request();
                    Request.Builder requestBuilder = original.newBuilder();

                    String token = TokenManager.getInstance(appCtx).getToken();
                    if (token != null && !token.isEmpty()) {
                        requestBuilder.addHeader("Authorization", "Bearer " + token);
                    }

                    Response response = chain.proceed(requestBuilder.build());
                    boolean isAuthCall = original.url().encodedPath().contains("auth.php");
                    if (response.code() == 401 && !isAuthCall) {
                        TokenManager.getInstance(appCtx).clearToken();
                        new Handler(Looper.getMainLooper()).post(() -> {
                            Intent intent = new Intent(appCtx, LoginActivity.class);
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                            appCtx.startActivity(intent);
                        });
                    }
                    return response;
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
