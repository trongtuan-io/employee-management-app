package com.example.employee_management_app;

public class SalaryFinalizeRequest {
    private int id;
    private int month;
    private int year;

    public SalaryFinalizeRequest(int id) {
        this.id = id;
    }

    public SalaryFinalizeRequest(int month, int year) {
        this.month = month;
        this.year = year;
    }
}
