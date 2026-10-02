package com.example.employee_management_app;

import com.google.gson.annotations.SerializedName;

public class LoginResponse {
    @SerializedName("success")
    private boolean success;

    @SerializedName("error")
    private String message;

    @SerializedName("data")
    private EmployeeData data;

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }

    public EmployeeData getData() {
        return data;
    }

    public static class EmployeeData {
        @SerializedName("id")
        private int id;

        @SerializedName("username")
        private String username;

        @SerializedName("role_name")
        private String role;

        public int getId() {
            return id;
        }

        public String getUsername() {
            return username;
        }

        public String getRole() {
            return role;
        }
    }
}
