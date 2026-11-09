package com.example.employee_management_app;

import java.util.List;

// Danh sach luong tu leaves_salaries.php?action=salary-list (so tien giu dang String vi MySQL tra ve chuoi)
// Luu y: base_salary cua bang salaries la LUONG THEO NGAY CONG (da nhan ty le cong/chuan);
// full_base_salary moi la luong co ban goc trong ho so nhan vien.
public class SalaryListResponse {
    private boolean success;
    private List<Item> data;

    public boolean isSuccess() { return success; }
    public List<Item> getData() { return data; }

    public static class Item {
        private int id;
        private int employee_id;
        private String full_name;
        private int month;
        private int year;
        private String base_salary;
        private String full_base_salary;
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
        private String paid_at;
        private String insurance_salary;
        private int dependents;

        public int getId() { return id; }
        public int getEmployeeId() { return employee_id; }
        public String getFullName() { return full_name; }
        public int getMonth() { return month; }
        public int getYear() { return year; }
        public String getBaseSalary() { return base_salary; }
        public String getFullBaseSalary() { return full_base_salary; }
        public String getNetSalary() { return net_salary; }
        public String getAllowance() { return allowance; }
        public String getOvertimePay() { return overtime_pay; }
        public String getBonus() { return bonus; }
        public String getDeduction() { return deduction; }
        public String getWorkDays() { return work_days; }
        public String getStandardDays() { return standard_days; }
        public boolean isFinalized() { return finalized == 1; }
        public String getInsuranceBase() { return insurance_base; }
        public String getInsuranceAmount() { return insurance_amount; }
        public String getTaxAmount() { return tax_amount; }
        public boolean isPaid() { return paid == 1; }
        public String getPaidAt() { return paid_at; }
        public String getInsuranceSalary() { return insurance_salary; }
        public int getDependents() { return dependents; }

        public static double num(String raw) {
            if (raw == null) return 0;
            try { return Double.parseDouble(raw); } catch (NumberFormatException e) { return 0; }
        }

        // "24.00" -> "24", "24.50" -> "24.5"
        public static String daysText(String raw) {
            double v = num(raw);
            if (v == Math.floor(v)) return String.valueOf((int) v);
            return String.valueOf(v);
        }

        // Dinh dang tien kieu Viet Nam (5.000.000 ₫) thay vi chuoi tho "5000000.00".
        public static String money(String raw) {
            if (raw == null) return "0 ₫";
            try {
                double v = Double.parseDouble(raw);
                return java.text.NumberFormat.getInstance(new java.util.Locale("vi", "VN"))
                        .format(Math.round(v)) + " ₫";
            } catch (NumberFormatException e) {
                return raw;
            }
        }

        public String workDaysText() {
            if (standard_days == null || num(standard_days) <= 0) return "";
            return daysText(work_days) + "/" + daysText(standard_days) + " ngày";
        }

        @Override
        public String toString() {
            String s = "Tháng " + month + "/" + year
                    + (isFinalized() ? " (đã chốt)" : " (bản nháp)");
            String wd = workDaysText();
            if (!wd.isEmpty()) s += "\nNgày công: " + wd;
            return s
                    + "\nLương theo ngày công: " + money(base_salary)
                    + " | Phụ cấp: " + money(allowance)
                    + " | Tăng ca: " + money(overtime_pay)
                    + " | Thưởng: " + money(bonus)
                    + " | Khấu trừ: " + money(deduction)
                    + "\nThực lĩnh: " + money(net_salary);
        }
    }
}
