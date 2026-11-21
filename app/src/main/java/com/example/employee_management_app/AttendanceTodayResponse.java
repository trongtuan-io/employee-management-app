package com.example.employee_management_app;

import com.google.gson.annotations.SerializedName;

// Trang thai cham cong hom nay tu attendances.php?action=today (the cham cong o Trang chu)
public class AttendanceTodayResponse {
    private boolean success;
    private Data data;

    public boolean isSuccess() { return success; }
    public Data getData() { return data; }

    public static class Data {
        @SerializedName("checked_in")
        private boolean checkedIn;
        @SerializedName("checked_out")
        private boolean checkedOut;
        @SerializedName("check_in")
        private String checkIn;
        @SerializedName("check_out")
        private String checkOut;
        private String status;
        private Integer check_in_remote;  // 1 = cham vao ngoai van phong, 0 = trong van phong, null = khong co GPS
        private Integer check_out_remote;

        public boolean isCheckedIn() { return checkedIn; }
        public boolean isCheckedOut() { return checkedOut; }
        public String getCheckIn() { return checkIn; }
        public String getCheckOut() { return checkOut; }
        public String getStatus() { return status; }
        public Integer getCheckInRemote() { return check_in_remote; }
        public Integer getCheckOutRemote() { return check_out_remote; }
    }
}
