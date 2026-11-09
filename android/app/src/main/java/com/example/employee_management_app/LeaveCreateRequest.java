package com.example.employee_management_app;

public class LeaveCreateRequest {
    private int employee_id;
    private String start_date; // YYYY-MM-DD
    private String end_date;   // YYYY-MM-DD
    private String reason;
    private String leave_type; // annual | sick | personal | unpaid

    public LeaveCreateRequest(int employeeId, String startDate, String endDate, String reason) {
        this(employeeId, startDate, endDate, reason, "annual");
    }

    public LeaveCreateRequest(int employeeId, String startDate, String endDate, String reason, String leaveType) {
        this.employee_id = employeeId;
        this.start_date = startDate;
        this.end_date = endDate;
        this.reason = reason;
        this.leave_type = leaveType;
    }
}
