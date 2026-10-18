package com.example.employee_management_app;

public class UpdateEmployeeRequest {
    private final int id;
    private final int requester_id;
    private final String full_name;
    private final String email;
    private final String phone;
    private final Integer department_id;
    private final Integer position_id;
    private final String hire_date;

    public UpdateEmployeeRequest(int id, int requesterId, String fullName, String email, String phone) {
        this(id, requesterId, fullName, email, phone, null, null, null);
    }

    public UpdateEmployeeRequest(int id, int requesterId, String fullName, String email, String phone,
                                 Integer departmentId, Integer positionId, String hireDate) {
        this.id = id;
        this.requester_id = requesterId;
        this.full_name = fullName;
        this.email = email;
        this.phone = phone;
        this.department_id = departmentId;
        this.position_id = positionId;
        this.hire_date = hireDate;
    }
}
