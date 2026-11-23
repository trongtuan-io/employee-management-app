package com.example.employee_management_app;

import com.google.gson.annotations.SerializedName;

public class AttendanceReportItem {
    @SerializedName("id")
    private int id;
    @SerializedName("employee_id")
    private int employeeId;
    @SerializedName("full_name")
    private String fullName;
    @SerializedName("dept_name")
    private String deptName;
    @SerializedName("work_date")
    private String workDate;
    @SerializedName("check_in_time")
    private String checkInTime;
    @SerializedName("check_out_time")
    private String checkOutTime;
    @SerializedName("status")
    private String status;
    @SerializedName("check_in_method")
    private String checkInMethod;
    @SerializedName("check_in_remote")
    private Integer checkInRemote;

    public int getId() { return id; }
    public String getFullName() { return fullName; }
    public String getDeptName() { return deptName; }
    public String getWorkDate() { return workDate; }
    public String getCheckInTime() { return checkInTime; }
    public String getCheckOutTime() { return checkOutTime; }
    public String getStatus() { return status; }
    public String getCheckInMethod() { return checkInMethod; }
    public Integer getCheckInRemote() { return checkInRemote; }

    @Override
    public String toString() {
        String in = checkInTime != null && checkInTime.length() >= 16 ? checkInTime.substring(11, 16) : "Chưa vào";
        String out = checkOutTime != null && checkOutTime.length() >= 16 ? checkOutTime.substring(11, 16) : "Chưa ra";
        String st = "present".equals(status) ? "Có mặt" : ("late".equals(status) ? "Đi muộn" : (status != null ? status : "Chưa chấm"));
        String dept = deptName != null ? " · " + deptName : "";
        return fullName + dept + "\nVào: " + in + " | Ra: " + out + " (" + st + ")";
    }
}
