package com.example.employee_management_app;

import java.util.List;

// Dang ky khuon mat: gui vector embedding 128 chieu (FaceNet) len
// biometrics.php?action=face-enroll. Chi gui vector so, khong gui anh.
public class FaceEnrollRequest {
    private List<Double> embedding;
    private String model;

    public FaceEnrollRequest(List<Double> embedding, String model) {
        this.embedding = embedding;
        this.model = model;
    }
}
