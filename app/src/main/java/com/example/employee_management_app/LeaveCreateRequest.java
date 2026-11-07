package com.example.employee_management_app;

public class LeaveCreateRequest {
    private int employee_id;
    private String start_date; // YYYY-MM-DD
    private String end_date;   // YYYY-MM-DD
    private String reason;

    public LeaveCreateRequest(int employeeId, String startDate, String endDate, String reason) {
        this.employee_id = employeeId;
        this.start_date = startDate;
        this.end_date = endDate;
        this.reason = reason;
    }
}
