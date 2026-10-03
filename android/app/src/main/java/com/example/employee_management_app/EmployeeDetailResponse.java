package com.example.employee_management_app;

import com.google.gson.annotations.SerializedName;

public class EmployeeDetailResponse {
    @SerializedName("success")
    private boolean success;

    @SerializedName("data")
    private Detail data;

    public boolean isSuccess() {
        return success;
    }

    public Detail getData() {
        return data;
    }

    public static class Detail {
        private int id;
        private String username;
        private String full_name;
        private String phone;
        private String email;
        private String base_salary;

        @SerializedName("dept_name")
        private String deptName;

        @SerializedName("position_name")
        private String positionName;

        public int getId() { return id; }
        public String getUsername() { return username; }
        public String getFullName() { return full_name; }
        public String getPhone() { return phone; }
        public String getEmail() { return email; }
        public String getBaseSalary() { return base_salary; }
        public String getDeptName() { return deptName; }
        public String getPositionName() { return positionName; }
    }
}
