package com.example.employee_management_app;

// approved_by KHONG gui tu client: server lay tu token cua nguoi duyet (auth_guard.php)
public class LeaveApproveRequest {
    private int id;
    private String status; // "approved" hoac "rejected"

    public LeaveApproveRequest(int id, String status) {
        this.id = id;
        this.status = status;
    }
}
