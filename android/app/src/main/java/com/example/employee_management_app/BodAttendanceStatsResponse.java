package com.example.employee_management_app;

import com.google.gson.annotations.SerializedName;

public class BodAttendanceStatsResponse {
    @SerializedName("success")
    private boolean success;

    @SerializedName("data")
    private Data data;

    public boolean isSuccess() { return success; }
    public Data getData() { return data; }

    public static class Data {
        @SerializedName("month")
        private int month;

        @SerializedName("year")
        private int year;

        @SerializedName("on_time")
        private int onTime;

        @SerializedName("late")
        private int late;

        public int getMonth() { return month; }
        public int getYear() { return year; }
        public int getOnTime() { return onTime; }
        public int getLate() { return late; }
    }
}
