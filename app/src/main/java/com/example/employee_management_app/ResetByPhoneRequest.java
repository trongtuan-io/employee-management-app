package com.example.employee_management_app;

import com.google.gson.annotations.SerializedName;

public class ResetByPhoneRequest {
    private String phone;

    @SerializedName("firebase_uid")
    private String firebaseUid;

    @SerializedName("new_password")
    private String newPassword;

    public ResetByPhoneRequest(String phone, String firebaseUid, String newPassword) {
        this.phone = phone;
        this.firebaseUid = firebaseUid;
        this.newPassword = newPassword;
    }
}
