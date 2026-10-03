package com.example.employee_management_app;

public class VerifyRequest {
    private String email;
    private String phone;
    private String code;

    public VerifyRequest(String emailOrPhone, String code, boolean isEmail) {
        if (isEmail) this.email = emailOrPhone;
        else this.phone = emailOrPhone;
        this.code = code;
    }
}
