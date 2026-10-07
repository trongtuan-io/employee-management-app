package com.example.employee_management_app;

public class UpdateEmployeeRequest {
    private int id;
    private int requester_id;
    private String full_name;
    private String email;
    private String phone;

    public UpdateEmployeeRequest(int id, int requesterId, String fullName, String email, String phone) {
        this.id = id;
        this.requester_id = requesterId;
        this.full_name = fullName;
        this.email = email;
        this.phone = phone;
    }
}
