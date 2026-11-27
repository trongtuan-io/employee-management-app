package com.example.employee_management_app;

import com.google.gson.annotations.SerializedName;

public class BodDashboardResponse {
    @SerializedName("success")
    private boolean success;

    @SerializedName("data")
    private Data data;

    public boolean isSuccess() { return success; }
    public Data getData() { return data; }

    public static class Data {
        @SerializedName("total_employees")
        private int totalEmployees;

        @SerializedName("pending_proposals")
        private int pendingProposals;

        @SerializedName("total_salary_fund")
        private double totalSalaryFund;

        @SerializedName("attendance_rate")
        private double attendanceRate;

        @SerializedName("month")
        private int month;

        @SerializedName("year")
        private int year;

        public int getTotalEmployees() { return totalEmployees; }
        public int getPendingProposals() { return pendingProposals; }
        public double getTotalSalaryFund() { return totalSalaryFund; }
        public double getAttendanceRate() { return attendanceRate; }
        public int getMonth() { return month; }
        public int getYear() { return year; }
    }
}
