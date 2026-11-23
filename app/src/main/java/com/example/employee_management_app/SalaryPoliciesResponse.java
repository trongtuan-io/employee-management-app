package com.example.employee_management_app;

import java.util.List;

// Danh sach phu cap co dinh theo chinh sach (salary-policies)
public class SalaryPoliciesResponse {
    private boolean success;
    private List<Item> data;

    public boolean isSuccess() { return success; }
    public List<Item> getData() { return data; }

    public static class Item {
        private int id;
        private String name;
        private String amount;
        private Integer department_id;
        private String dept_name;
        private int active;

        public int getId() { return id; }
        public String getName() { return name; }
        public String getAmount() { return amount; }
        public Integer getDepartmentId() { return department_id; }
        public String getDeptName() { return dept_name; }
        public boolean isActive() { return active == 1; }
    }
}
