package com.example.employee_management_app;

public class DepartmentMutationRequest {
    private final int requester_id;
    private final int id;
    private final String name;
    private final String description;

    public DepartmentMutationRequest(int requesterId, int id, String name, String description) {
        this.requester_id = requesterId;
        this.id = id;
        this.name = name;
        this.description = description;
    }
}
