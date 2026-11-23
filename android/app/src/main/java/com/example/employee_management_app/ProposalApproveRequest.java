package com.example.employee_management_app;

public class ProposalApproveRequest {
    private int id;
    private String status;

    public ProposalApproveRequest(int id, String status) {
        this.id = id;
        this.status = status;
    }
}
