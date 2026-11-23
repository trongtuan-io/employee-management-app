package com.example.employee_management_app;

import java.util.List;

public class CheckinRequest {
    private int employee_id;
    private Double lat; // toa do GPS luc cham cong (null = khong lay duoc vi tri)
    private Double lng;
    // Phuong thuc cham cong: "gps" (mac dinh) | "face" | "fingerprint"
    private String method;
    // method=face: vector embedding 128 chieu trich tu khuon mat vua chup,
    // server doi khop cosine voi ban dang ky cua chinh nhan vien nay.
    private List<Double> face_embedding;
    // method=fingerprint: true khi BiometricPrompt tren may da xac thuc xong.
    private Boolean biometric_verified;

    public CheckinRequest(int employeeId) {
        this.employee_id = employeeId;
    }

    public CheckinRequest(int employeeId, Double lat, Double lng) {
        this.employee_id = employeeId;
        this.lat = lat;
        this.lng = lng;
    }

    public CheckinRequest(int employeeId, Double lat, Double lng, String method,
                          List<Double> faceEmbedding, Boolean biometricVerified) {
        this.employee_id = employeeId;
        this.lat = lat;
        this.lng = lng;
        this.method = method;
        this.face_embedding = faceEmbedding;
        this.biometric_verified = biometricVerified;
    }
}
