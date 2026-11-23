package com.example.employee_management_app;

// Them/sua 1 khoan phu cap chinh sach (salary-policy-save); id null = tao moi
public class SalaryPolicySaveRequest {
    private Integer id;
    private String name;
    private double amount;
    private Integer department_id;
    private int active;

    public SalaryPolicySaveRequest(Integer id, String name, double amount, Integer departmentId, int active) {
        this.id = id;
        this.name = name;
        this.amount = amount;
        this.department_id = departmentId;
        this.active = active;
    }
}
