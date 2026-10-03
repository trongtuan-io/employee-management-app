package com.example.employee_management_app;

import com.google.gson.annotations.SerializedName;

public class AttendanceResponse {
    @SerializedName("success")
    private boolean success;

    @SerializedName("data")
    private Result data;

    public boolean isSuccess() {
        return success;
    }

    public Result getData() {
        return data;
    }

    public static class Result {
        @SerializedName("check_in")
        private String checkIn;

        @SerializedName("check_out")
        private String checkOut;

        private String status;

        public String getCheckIn() { return checkIn; }
        public String getCheckOut() { return checkOut; }
        public String getStatus() { return status; }
    }
}
