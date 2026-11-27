package com.example.employee_management_app;

// Body cho employees.php?action=delete (endpoint khai bao trong EmployeeApi tu nhanh yen,
// thieu class nay nen khong build duoc - bo sung 27/11/2026).
// Server hien tra 409 (khong xoa vinh vien, cho quy trinh nghi viec BOD), chi doc token admin.
public class DeleteEmployeeRequest {
    private int id;
    private int requester_id;

    public DeleteEmployeeRequest(int id, int requesterId) {
        this.id = id;
        this.requester_id = requesterId;
    }
}
