package com.example.employee_management_app;

import com.google.gson.annotations.SerializedName;

public class ProposalItem {
    @SerializedName("id")
    private int id;
    private int version;
    @SerializedName("effective_date")
    private String effectiveDate;
    @SerializedName("can_apply")
    private boolean canApply;
    @SerializedName("type")
    private String type;
    @SerializedName("employee_id")
    private Integer employeeId;
    @SerializedName("employee_name")
    private String employeeName;
    @SerializedName("department_id")
    private Integer departmentId;
    @SerializedName("dept_name")
    private String deptName;
    @SerializedName("position_id")
    private Integer positionId;
    @SerializedName("pos_title")
    private String posTitle;
    @SerializedName("old_value")
    private String oldValue;
    @SerializedName("new_value")
    private String newValue;
    @SerializedName("reason")
    private String reason;
    @SerializedName("status")
    private String status;
    @SerializedName("created_by")
    private int createdBy;
    @SerializedName("creator_name")
    private String creatorName;
    @SerializedName("approved_by")
    private Integer approvedBy;
    @SerializedName("approver_name")
    private String approverName;
    @SerializedName("created_at")
    private String createdAt;
    @SerializedName("processed_at")
    private String processedAt;

    public int getId() { return id; }
    public int getVersion() { return version; }
    public String getEffectiveDate() { return effectiveDate; }
    public boolean canApply() { return canApply; }
    public String getType() { return type; }
    public Integer getEmployeeId() { return employeeId; }
    public String getEmployeeName() { return employeeName; }
    public String getDeptName() { return deptName; }
    public String getPosTitle() { return posTitle; }
    public String getOldValue() { return oldValue; }
    public String getNewValue() { return newValue; }
    public String getReason() { return reason; }
    public String getStatus() { return status; }
    public String getCreatorName() { return creatorName; }
    public String getApproverName() { return approverName; }
    public String getCreatedAt() { return createdAt; }

    public String getTypeLabel() {
        if (type == null) return "Đề xuất nhân sự";
        switch (type) {
            case "recruitment":
            case "new_hire": return "Tuyển mới nhân viên";
            case "salary_change": return "Thay đổi lương cơ bản";
            case "transfer":
            case "department_transfer": return "Chuyển phòng ban";
            case "promotion": return "Thăng chức / Đổi chức vụ";
            case "termination": return "Cho nghỉ việc";
            default: return type;
        }
    }

    public String getStatusLabel() {
        if ("activated".equals(status)) return "Đã kích hoạt";
        if ("approved".equals(status)) return "Đã duyệt";
        if ("rejected".equals(status)) return "Đã từ chối";
        return "Chờ duyệt";
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(getStatusLabel()).append("] ").append(getTypeLabel());
        if (employeeName != null && !employeeName.trim().isEmpty()) {
            sb.append("\nNhân viên: ").append(employeeName);
        }
        if (newValue != null && !newValue.trim().isEmpty()) {
            sb.append("\nNội dung: ").append(newValue);
        } else if (deptName != null && !deptName.trim().isEmpty()) {
            sb.append("\nPhòng: ").append(deptName);
        } else if (posTitle != null && !posTitle.trim().isEmpty()) {
            sb.append("\nChức vụ: ").append(posTitle);
        }
        if (creatorName != null && !creatorName.trim().isEmpty()) {
            sb.append(" · Nguồn: ").append(creatorName);
        }
        return sb.toString();
    }
}
