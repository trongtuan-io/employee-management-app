package com.example.employee_management_app;

import com.google.gson.annotations.SerializedName;

public class AttendanceResponse {
    @SerializedName("id")
    private int id;

    @SerializedName("employee_id")
    private int employeeId;

    @SerializedName("full_name")
    private String fullName;

    @SerializedName("department_name")
    private String departmentName;

    @SerializedName("work_date")
    private String workDate;

    @SerializedName("check_in_time")
    private String checkInTime;

    @SerializedName("check_out_time")
    private String checkOutTime;

    @SerializedName("work_hours")
    private double workHours; // Khai báo chuẩn kiểu double để nhận chính xác số thập phân từ PHP

    @SerializedName("status")
    private String status;

    // --- Các Getter ---
    public int getId() {
        return id;
    }

    public int getEmployeeId() {
        return employeeId;
    }

    public String getFullName() {
        return fullName != null ? fullName : "Nhân viên ID: " + employeeId;
    }

    public String getDepartmentName() {
        return departmentName != null ? departmentName : "";
    }

    public String getWorkDate() {
        return workDate;
    }

    public String getCheckInTime() {
        return checkInTime != null && !checkInTime.isEmpty() ? checkInTime : "Chưa vào";
    }

    public String getCheckOutTime() {
        return checkOutTime != null && !checkOutTime.isEmpty() && !checkOutTime.equals("0000-00-00 00:00:00") ? checkOutTime : "Chưa ra";
    }

    public double getWorkHoursNumeric() {
        return workHours;
    }

    // Trả về chuỗi định dạng số giờ làm việc kèm chữ 'h' (ví dụ: "0.07h" hoặc "8.0h")
    public String getWorkHours() {
        return workHours + "h";
    }

    public String getStatus() {
        return status != null ? status : "--";
    }
}