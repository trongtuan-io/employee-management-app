package com.example.employee_management_app;

// So du quy phep nam (leaves_salaries.php?action=leave-balance)
public class LeaveBalanceResponse {
    private boolean success;
    private Data data;

    public boolean isSuccess() { return success; }
    public Data getData() { return data; }

    public static class Data {
        private int employee_id;
        private int year;
        private int quota;
        private int used;
        private int pending;
        private int remaining;

        public int getEmployeeId() { return employee_id; }
        public int getYear() { return year; }
        public int getQuota() { return quota; }
        public int getUsed() { return used; }
        public int getPending() { return pending; }
        public int getRemaining() { return remaining; }
    }
}
