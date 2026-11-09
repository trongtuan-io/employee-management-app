package com.example.employee_management_app;

// Sua cac khoan cong/tru cua 1 dong bang luong nhap (salary-adjust); server tu
// tinh lai bao hiem, thue TNCN va thuc linh. insurance_salary/dependents chi
// gui khi man chi tiet co du lieu ho so (null = giu nguyen, khong ghi de).
public class SalaryAdjustRequest {
    private int id;
    private double allowance;
    private double overtime_pay;
    private double bonus;
    private double deduction;
    private Double insurance_salary;
    private Integer dependents;

    public SalaryAdjustRequest(int id, double allowance, double overtimePay, double bonus, double deduction) {
        this.id = id;
        this.allowance = allowance;
        this.overtime_pay = overtimePay;
        this.bonus = bonus;
        this.deduction = deduction;
    }

    public SalaryAdjustRequest(int id, double allowance, double overtimePay, double bonus, double deduction,
                               Double insuranceSalary, Integer dependents) {
        this(id, allowance, overtimePay, bonus, deduction);
        this.insurance_salary = insuranceSalary;
        this.dependents = dependents;
    }
}
