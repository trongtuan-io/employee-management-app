package com.example.employee_management_app;

import com.google.gson.annotations.SerializedName;

public class VerifyResponse {
    @SerializedName("success")
    private boolean success;

    @SerializedName("data")
    private VerifyData data;

    public boolean isSuccess() {
        return success;
    }

    public VerifyData getData() {
        return data;
    }

    public static class VerifyData {
        @SerializedName("reset_token")
        private String resetToken;

        public String getResetToken() {
            return resetToken;
        }
    }
}
