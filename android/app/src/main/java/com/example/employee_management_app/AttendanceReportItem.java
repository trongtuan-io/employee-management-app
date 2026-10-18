package com.example.employee_management_app;

import com.google.gson.annotations.SerializedName;

public class AttendanceReportItem {
    @SerializedName("id")
    private int id;

    @SerializedName("employee_id")
    private int employeeId;

    @SerializedName("work_date")
    private String workDate;

    @SerializedName("check_in_time")
    private String checkInTime;

    @SerializedName("check_out_time")
    private String checkOutTime;

    private String status;

    @SerializedName("full_name")
    private String fullName;

    public AttendanceReportItem(int id, int employeeId, String workDate, String checkInTime, String checkOutTime, String status, String fullName) {
        this.id = id;
        this.employeeId = employeeId;
        this.workDate = workDate;
        this.checkInTime = checkInTime;
        this.checkOutTime = checkOutTime;
        this.status = status;
        this.fullName = fullName;
    }

    public int getId() { return id; }
    public int getEmployeeId() { return employeeId; }
    public String getWorkDate() { return workDate; }
    public String getCheckInTime() { return checkInTime; }
    public String getCheckOutTime() { return checkOutTime; }
    public String getStatus() { return status; }
    public String getFullName() { return fullName; }

    public String getWorkHours() {
        if (checkInTime == null || checkOutTime == null || checkInTime.equals("null") || checkOutTime.equals("null")) return "0";
        return "8";
    }
}
