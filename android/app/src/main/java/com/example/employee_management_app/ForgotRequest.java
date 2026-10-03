package com.example.employee_management_app;

import com.google.gson.annotations.SerializedName;

public class ForgotRequest {
    private String email;

    public ForgotRequest(String email) {
        this.email = email;
    }

    public String getEmail() {
        return email;
    }
}
