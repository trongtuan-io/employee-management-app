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

        @SerializedName("department_id")
        private int departmentId;

        @SerializedName("department_name")
        private String departmentName;

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

        public int getDepartmentId() {
            return departmentId;
        }

        public String getDepartmentName() {
            return departmentName;
        }
    }
}
