package com.example.employee_management_app;

import com.google.gson.annotations.SerializedName;

public class ForgotResponse {
    @SerializedName("success")
    private boolean success;

    @SerializedName("data")
    private ForgotData data;

    public boolean isSuccess() {
        return success;
    }

    public ForgotData getData() {
        return data;
    }

    public static class ForgotData {
        @SerializedName("sent")
        private boolean sent;

        // Ket qua gui that: false khi SMTP/SMS chua cau hinh hoac gui loi.
        // (Da bo field debug_code: ma xac nhan khong bao gio duoc tra trong response.)
        @SerializedName("mail_ok")
        private Boolean mailOk;

        @SerializedName("sms_ok")
        private Boolean smsOk;

        public boolean isSent() {
            return sent;
        }

        public boolean isDelivered() {
            return Boolean.TRUE.equals(mailOk) || Boolean.TRUE.equals(smsOk);
        }
    }
}
