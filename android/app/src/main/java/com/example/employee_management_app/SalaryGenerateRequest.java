package com.example.employee_management_app;

// Tinh luong tu dong theo ngay cong cho 1 thang (leaves_salaries.php?action=salary-generate)
public class SalaryGenerateRequest {
    private int month;
    private int year;

    public SalaryGenerateRequest(int month, int year) {
        this.month = month;
        this.year = year;
    }
}
