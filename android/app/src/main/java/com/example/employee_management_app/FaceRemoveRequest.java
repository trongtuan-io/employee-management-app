package com.example.employee_management_app;

// Xoa dang ky khuon mat (biometrics.php?action=face-remove)
public class FaceRemoveRequest {
    private int employee_id;

    public FaceRemoveRequest(int employeeId) {
        this.employee_id = employeeId;
    }
}
