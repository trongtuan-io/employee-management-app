package com.example.employee_management_app;

public class ProposalApproveRequest {
    private int id;
    private String status;
    private int version;

    public ProposalApproveRequest(int id, int version, String status) {
        this.id = id;
        this.version = version;
        this.status = status;
    }
}
