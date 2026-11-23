package com.example.employee_management_app;

public class RegisterRequest {
    private String username;
    private String password;
    private String full_name;
    private String phone;
    private String email;
    private int role_id;
    private Integer department_id;
    private Integer position_id;

    public RegisterRequest(String username, String password, String fullName,
                           String phone, String email, int roleId,
                           Integer departmentId, Integer positionId) {
        this.username = username;
        this.password = password;
        this.full_name = fullName;
        this.phone = phone;
        this.email = email;
        this.role_id = roleId;
        this.department_id = departmentId;
        this.position_id = positionId;
    }
}
