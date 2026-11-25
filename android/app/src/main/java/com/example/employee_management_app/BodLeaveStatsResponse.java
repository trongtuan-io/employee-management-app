package com.example.employee_management_app;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class BodLeaveStatsResponse {
    @SerializedName("success")
    private boolean success;

    @SerializedName("data")
    private List<Item> data;

    public boolean isSuccess() { return success; }
    public List<Item> getData() { return data; }

    public static class Item {
        @SerializedName("dept_name")
        private String deptName;

        @SerializedName("approved")
        private int approved;

        @SerializedName("pending")
        private int pending;

        @SerializedName("rejected")
        private int rejected;

        public String getDeptName() { return deptName; }
        public int getApproved() { return approved; }
        public int getPending() { return pending; }
        public int getRejected() { return rejected; }
    }
}
