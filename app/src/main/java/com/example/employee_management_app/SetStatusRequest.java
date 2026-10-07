package com.example.employee_management_app;

public class SetStatusRequest {
    private int id;
    private int requester_id;
    private int status;

    public SetStatusRequest(int id, int requesterId, int status) {
        this.id = id;
        this.requester_id = requesterId;
        this.status = status;
    }
}
