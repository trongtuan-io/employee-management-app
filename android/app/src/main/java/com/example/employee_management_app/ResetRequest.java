package com.example.employee_management_app;

import com.google.gson.annotations.SerializedName;

public class ResetRequest {
    @SerializedName("reset_token")
    private String resetToken;

    @SerializedName("new_password")
    private String newPassword;

    public ResetRequest(String resetToken, String newPassword) {
        this.resetToken = resetToken;
        this.newPassword = newPassword;
    }
}
