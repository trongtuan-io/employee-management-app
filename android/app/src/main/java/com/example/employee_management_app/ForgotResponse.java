package com.example.employee_management_app;

import com.google.gson.annotations.SerializedName;

public class ForgotResponse {
    @SerializedName("success")
    private boolean success;

    @SerializedName("data")
    private ForgotData data;

    public boolean isSuccess() {
        return success;
    }

    public ForgotData getData() {
        return data;
    }

    public static class ForgotData {
        @SerializedName("sent")
        private boolean sent;

        @SerializedName("debug_code")
        private String debugCode;

        public boolean isSent() {
            return sent;
        }

        public String getDebugCode() {
            return debugCode;
        }
    }
}
