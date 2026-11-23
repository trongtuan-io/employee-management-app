package com.example.employee_management_app;

import org.json.JSONObject;

import retrofit2.Response;

// Trich thong bao loi that tu errorBody cua server ({"success":false,"error":"..."})
// thay vi hien thong bao chung chung khong dung nguyen nhan.
public class ApiError {
    public static String message(Response<?> response, String fallback) {
        try {
            if (response != null && response.errorBody() != null) {
                String raw = response.errorBody().string();
                JSONObject obj = new JSONObject(raw);
                String err = obj.optString("error", "");
                if (!err.isEmpty()) return err;
            }
        } catch (Exception ignored) {
        }
        int code = response != null ? response.code() : 0;
        return code > 0 ? fallback + " (HTTP " + code + ")" : fallback;
    }
}
