package com.example.employee_management_app;

import com.google.gson.annotations.SerializedName;

public class ChangePasswordRequest {
    @SerializedName("employee_id")
    private int employeeId;

    @SerializedName("old_password")
    private String oldPassword;

    @SerializedName("new_password")
    private String newPassword;

    public ChangePasswordRequest(int employeeId, String oldPassword, String newPassword) {
        this.employeeId = employeeId;
        this.oldPassword = oldPassword;
        this.newPassword = newPassword;
    }

    public int getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(int employeeId) {
        this.employeeId = employeeId;
    }

    public String getOldPassword() {
        return oldPassword;
    }

    public void setOldPassword(String oldPassword) {
        this.oldPassword = oldPassword;
    }

    public String getNewPassword() {
        return newPassword;
    }

    public void setNewPassword(String newPassword) {
        this.newPassword = newPassword;
    }
}
