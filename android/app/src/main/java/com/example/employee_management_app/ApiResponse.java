package com.example.employee_management_app;

import com.google.gson.annotations.SerializedName;

public class ApiResponse {
    @SerializedName("success")
    private boolean success;

    // Dùng alternate để hứng được cả key "message" hoặc "error" từ backend
    @SerializedName(value = "message", alternate = {"error"})
    private String message;

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }
}
