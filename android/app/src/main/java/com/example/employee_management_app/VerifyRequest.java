package com.example.employee_management_app;

import com.google.gson.annotations.SerializedName;

public class VerifyRequest {
    private String email;
    private String code;

    public VerifyRequest(String email, String code) {
        this.email = email;
        this.code = code;
    }
}
