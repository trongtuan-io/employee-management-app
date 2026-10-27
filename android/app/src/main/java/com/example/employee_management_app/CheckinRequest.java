package com.example.employee_management_app;

public class CheckinRequest {
    private int employee_id;
    private double latitude;
    private double longitude;

    // Constructor mới nhận cả ID nhân viên, vĩ độ và kinh độ
    public CheckinRequest(int employeeId, double latitude, double longitude) {
        this.employee_id = employeeId;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public int getEmployee_id() {
        return employee_id;
    }

    public void setEmployee_id(int employee_id) {
        this.employee_id = employee_id;
    }

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }
}