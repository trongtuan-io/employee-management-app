package com.example.employee_management_app;

import com.google.gson.annotations.SerializedName;

public class AvatarResponse {
    @SerializedName("success")
    private boolean success;

    @SerializedName("data")
    private Data data;

    public boolean isSuccess() {
        return success;
    }

    public Data getData() {
        return data;
    }

    public static class Data {
        @SerializedName("avatar_url")
        private String avatarUrl;

        public String getAvatarUrl() {
            return avatarUrl;
        }
    }
}
