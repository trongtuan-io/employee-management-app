package com.example.employee_management_app;

import java.util.List;

// Bang luong tong hop toan cong ty theo thang tu leaves_salaries.php?action=salary-overview.
// salary_id = 0 nghia la nhan vien chua duoc tinh luong thang do.
public class SalaryOverviewResponse {
    private boolean success;
    private List<Item> data;

    public boolean isSuccess() { return success; }
    public List<Item> getData() { return data; }

    public static class Item {
        private int employee_id;
        private String full_name;
        private String dept_name;
        private String full_base_salary;
        private int salary_id;
        private String base_salary;
        private String allowance;
        private String overtime_pay;
        private String bonus;
        private String deduction;
        private String net_salary;
        private String work_days;
        private String standard_days;
        private int finalized;
        private String insurance_base;
        private String insurance_amount;
        private String tax_amount;
        private int paid;
        private String insurance_salary;
        private int dependents;

        public int getEmployeeId() { return employee_id; }
        public String getFullName() { return full_name; }
        public String getDeptName() { return dept_name; }
        public String getFullBaseSalary() { return full_base_salary; }
        public int getSalaryId() { return salary_id; }
        public String getBaseSalary() { return base_salary; }
        public String getAllowance() { return allowance; }
        public String getOvertimePay() { return overtime_pay; }
        public String getBonus() { return bonus; }
        public String getDeduction() { return deduction; }
        public String getNetSalary() { return net_salary; }
        public String getWorkDays() { return work_days; }
        public String getStandardDays() { return standard_days; }
        public boolean isFinalized() { return finalized == 1; }
        public boolean hasSalary() { return salary_id > 0; }
        public String getInsuranceBase() { return insurance_base; }
        public String getInsuranceAmount() { return insurance_amount; }
        public String getTaxAmount() { return tax_amount; }
        public boolean isPaid() { return paid == 1; }
        public String getInsuranceSalary() { return insurance_salary; }
        public int getDependents() { return dependents; }
    }
}
