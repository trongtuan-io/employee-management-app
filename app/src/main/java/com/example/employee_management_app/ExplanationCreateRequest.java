package com.example.employee_management_app;

// Tao don giai trinh quen cham cong (attendances.php?action=explanation-create)
public class ExplanationCreateRequest {
    private String work_date;    // YYYY-MM-DD
    private String kind;         // checkin | checkout | both
    private String proposed_in;  // HH:MM (null neu khong can)
    private String proposed_out; // HH:MM (null neu khong can)
    private String reason;

    public ExplanationCreateRequest(String workDate, String kind, String proposedIn, String proposedOut, String reason) {
        this.work_date = workDate;
        this.kind = kind;
        this.proposed_in = proposedIn;
        this.proposed_out = proposedOut;
        this.reason = reason;
    }
}
