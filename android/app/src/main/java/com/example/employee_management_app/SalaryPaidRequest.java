package com.example.employee_management_app;

// Danh dau da tra luong / huy da tra (salary-paid)
public class SalaryPaidRequest {
    private int id;
    private int paid;

    public SalaryPaidRequest(int id, int paid) {
        this.id = id;
        this.paid = paid;
    }
}
