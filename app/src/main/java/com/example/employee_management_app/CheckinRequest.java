package com.example.employee_management_app;

public class CheckinRequest {
    private int employee_id;
    private Double lat; // toa do GPS luc cham cong (null = khong lay duoc vi tri)
    private Double lng;

    public CheckinRequest(int employeeId) {
        this.employee_id = employeeId;
    }

    public CheckinRequest(int employeeId, Double lat, Double lng) {
        this.employee_id = employeeId;
        this.lat = lat;
        this.lng = lng;
    }
}
