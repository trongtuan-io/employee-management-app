package com.example.employee_management_app;

import java.util.List;

// Danh sach don nghi phep tu leaves_salaries.php?action=leave-list
public class LeaveListResponse {
    private boolean success;
    private List<Item> data;

    public boolean isSuccess() { return success; }
    public List<Item> getData() { return data; }

    public static class Item {
        private int id;
        private int employee_id;
        private String full_name; // chi co o danh sach cho duyet (server JOIN employees)
        private String dept_name; // chi co o danh sach cho duyet (server JOIN departments)
        private String start_date;
        private String end_date;
        private String reason;
        private String status;
        private String created_at;

        public int getId() { return id; }
        public int getEmployeeId() { return employee_id; }
        public String getFullName() { return full_name; }
        public String getDeptName() { return dept_name; }
        public String getStartDate() { return start_date; }
        public String getEndDate() { return end_date; }
        public String getReason() { return reason; }
        public String getStatus() { return status; }
        public String getCreatedAt() { return created_at; }

        @Override
        public String toString() {
            String head = (full_name != null && !full_name.isEmpty())
                    ? full_name + " — " : "";
            String s = head + start_date + " → " + end_date + " (" + status + ")";
            if (reason != null && !reason.isEmpty()) s += "\nLý do: " + reason;
            return s;
        }
    }
}
