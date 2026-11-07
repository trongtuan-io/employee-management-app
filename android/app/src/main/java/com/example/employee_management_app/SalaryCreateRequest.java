package com.example.employee_management_app;

public class SalaryCreateRequest {
    private int employee_id;
    private int month;
    private int year;
    private double base_salary;
    private double allowance;
    private double overtime_pay;
    private double bonus;
    private double deduction;

    public SalaryCreateRequest(int employeeId, int month, int year, double baseSalary,
                               double allowance, double overtimePay, double bonus, double deduction) {
        this.employee_id = employeeId;
        this.month = month;
        this.year = year;
        this.base_salary = baseSalary;
        this.allowance = allowance;
        this.overtime_pay = overtimePay;
        this.bonus = bonus;
        this.deduction = deduction;
    }
}
