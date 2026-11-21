package com.example.employee_management_app;

import com.google.gson.annotations.SerializedName;

// Trang thai dang ky khuon mat tu biometrics.php?action=face-status
public class FaceStatusResponse {
    private boolean success;
    private Data data;

    public boolean isSuccess() { return success; }
    public Data getData() { return data; }

    public static class Data {
        private boolean registered;
        private String model;
        @SerializedName("updated_at")
        private String updatedAt;

        public boolean isRegistered() { return registered; }
        public String getModel() { return model; }
        public String getUpdatedAt() { return updatedAt; }
    }
}
