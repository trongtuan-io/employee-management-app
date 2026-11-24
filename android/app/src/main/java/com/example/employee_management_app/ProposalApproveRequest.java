package com.example.employee_management_app;

public class ProposalApproveRequest {
    private int id;
    private String status;

    private final int version;
    private final String review_note;

    public ProposalApproveRequest(int id, int version, String status, String note) {
        this.version = version;
        this.review_note = note;
        this.id = id;
        this.status = status;
    }
}
