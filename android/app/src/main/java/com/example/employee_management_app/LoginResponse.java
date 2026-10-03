package com.example.employee_management_app;

import com.google.gson.annotations.SerializedName;

public class LoginResponse {
    @SerializedName("success")
    private boolean success;

    @SerializedName("error")
    private String message;
    
    @SerializedName("token")
    private String token;

    @SerializedName("data")
    private EmployeeData data;

    public boolean isSuccess() {
        return success;
    }
    
    public String getToken() {
        return token;
    }

    public String getMessage() {
        return message;
    }

    public EmployeeData getData() {
        return data;
    }

    public String getToken() {
        return data != null ? data.getToken() : null;
    }

    public static class EmployeeData {
        @SerializedName("id")
        private int id;

        @SerializedName("username")
        private String username;

        @SerializedName("role_name")
        private String role;

        @SerializedName("token")
        private String token;

        public int getId() {
            return id;
        }

        public String getUsername() {
            return username;
        }

        public String getRole() {
            return role;
        }

        public String getToken() {
            return token;
        }
    }
}
