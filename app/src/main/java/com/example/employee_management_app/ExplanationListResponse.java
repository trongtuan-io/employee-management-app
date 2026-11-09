package com.example.employee_management_app;

import java.util.List;

// Danh sach don giai trinh cham cong (attendances.php?action=explanation-list)
public class ExplanationListResponse {
    private boolean success;
    private List<Item> data;

    public boolean isSuccess() { return success; }
    public List<Item> getData() { return data; }

    public static class Item {
        private int id;
        private int employee_id;
        private String full_name; // chi co o hang cho duyet
        private String dept_name; // chi co o hang cho duyet
        private String work_date;
        private String kind; // checkin | checkout | both
        private String proposed_in;
        private String proposed_out;
        private String reason;
        private String status;
        private String created_at;
        private String approver_name;
        private String processed_at;

        public int getId() { return id; }
        public int getEmployeeId() { return employee_id; }
        public String getFullName() { return full_name; }
        public String getDeptName() { return dept_name; }
        public String getWorkDate() { return work_date; }
        public String getKind() { return kind; }
        public String getProposedIn() { return proposed_in; }
        public String getProposedOut() { return proposed_out; }
        public String getReason() { return reason; }
        public String getStatus() { return status; }
        public String getCreatedAt() { return created_at; }
        public String getApproverName() { return approver_name; }
        public String getProcessedAt() { return processed_at; }
    }
}
