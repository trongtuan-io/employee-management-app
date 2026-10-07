package com.example.employee_management_app;

public class ForgotRequest {
    private String email;
    private String phone;

    public ForgotRequest(String email, boolean isEmail) {
        if (isEmail) this.email = email;
        else this.phone = email;
    }
}
