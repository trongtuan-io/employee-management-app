package com.example.employee_management_app;

import java.util.List;

// Danh sach luong tu leaves_salaries.php?action=salary-list (so tien giu dang String vi MySQL tra ve chuoi)
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
        private String allowance;
        private String overtime_pay;
        private String bonus;
        private String deduction;
        private String net_salary;

        public int getId() { return id; }
        public int getMonth() { return month; }
        public int getYear() { return year; }
        public String getNetSalary() { return net_salary; }

        // Dinh dang tien kieu Viet Nam (5.000.000 ₫) thay vi chuoi tho "5000000.00".
        private static String money(String raw) {
            if (raw == null) return "0 ₫";
            try {
                double v = Double.parseDouble(raw);
                return java.text.NumberFormat.getInstance(new java.util.Locale("vi", "VN"))
                        .format(Math.round(v)) + " ₫";
            } catch (NumberFormatException e) {
                return raw;
            }
        }

        @Override
        public String toString() {
            return "Tháng " + month + "/" + year
                    + "\nLương cơ bản: " + money(base_salary)
                    + " | Phụ cấp: " + money(allowance)
                    + " | Tăng ca: " + money(overtime_pay)
                    + " | Thưởng: " + money(bonus)
                    + " | Khấu trừ: " + money(deduction)
                    + "\nThực lĩnh: " + money(net_salary);
        }
    }
}
