package com.example.employee_management_app;

// Sua cac khoan cong/tru cua 1 dong bang luong nhap (salary-adjust); server tu tinh lai thuc linh
public class SalaryAdjustRequest {
    private int id;
    private double allowance;
    private double overtime_pay;
    private double bonus;
    private double deduction;

    public SalaryAdjustRequest(int id, double allowance, double overtimePay, double bonus, double deduction) {
        this.id = id;
        this.allowance = allowance;
        this.overtime_pay = overtimePay;
        this.bonus = bonus;
        this.deduction = deduction;
    }
}
