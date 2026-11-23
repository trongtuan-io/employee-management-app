package com.example.employee_management_app;

import java.util.List;

// Lich vang theo thang (leaves_salaries.php?action=leave-calendar)
public class LeaveCalendarResponse {
    private boolean success;
    private Data data;

    public boolean isSuccess() { return success; }
    public Data getData() { return data; }

    public static class Data {
        private int month;
        private int year;
        private String scope; // company | department
        private List<Item> items;

        public int getMonth() { return month; }
        public int getYear() { return year; }
        public String getScope() { return scope; }
        public List<Item> getItems() { return items; }
    }

    public static class Item {
        private int id;
        private int employee_id;
        private String full_name;
        private String dept_name;
        private String start_date;
        private String end_date;
        private String leave_type;
        private String status;
        private String reason;

        public int getId() { return id; }
        public int getEmployeeId() { return employee_id; }
        public String getFullName() { return full_name; }
        public String getDeptName() { return dept_name; }
        public String getStartDate() { return start_date; }
        public String getEndDate() { return end_date; }
        public String getLeaveType() { return leave_type; }
        public String getStatus() { return status; }
        public String getReason() { return reason; }
    }
}
