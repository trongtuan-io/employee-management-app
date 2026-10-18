package com.example.employee_management_app;

import com.google.gson.annotations.SerializedName;

public class DashboardResponse {
    @SerializedName("success")
    private boolean success;

    @SerializedName("data")
    private Stats data;

    public boolean isSuccess() {
        return success;
    }

    public Stats getData() {
        return data;
    }

    public static class Stats {
        @SerializedName("total_active")
        private int totalActive;

        @SerializedName("pending_leaves")
        private int pendingLeaves;

        @SerializedName("late_today")
        private int lateToday;

        public int getTotalActive() { return totalActive; }
        public int getPendingLeaves() { return pendingLeaves; }
        public int getLateToday() { return lateToday; }
    }
}
