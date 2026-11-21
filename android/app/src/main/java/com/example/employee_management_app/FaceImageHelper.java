package com.example.employee_management_app;

import android.graphics.Bitmap;
import android.graphics.Rect;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;

// Phat hien + cat khuon mat trong anh chup bang ML Kit (chay tren may).
// Dieu kien chat luong toi thieu de embedding on dinh: dung 1 khuon mat,
// mat du lon (>= 120px), khong quay ngang qua nhieu.
public class FaceImageHelper {

    public interface Callback {
        void onFace(Bitmap faceCrop);
        void onError(String message);
    }

    public static void extractFace(Bitmap photo, Callback cb) {
        FaceDetectorOptions options = new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .setMinFaceSize(0.15f)
                .build();
        FaceDetector detector = FaceDetection.getClient(options);
        InputImage image = InputImage.fromBitmap(photo, 0);
        detector.process(image)
                .addOnSuccessListener(faces -> {
                    detector.close();
                    if (faces.isEmpty()) {
                        cb.onError("Không thấy khuôn mặt trong ảnh. Chụp lại gần hơn, đủ sáng.");
                        return;
                    }
                    if (faces.size() > 1) {
                        cb.onError("Ảnh có nhiều hơn 1 khuôn mặt. Chụp một mình để chấm công đúng người.");
                        return;
                    }
                    Face face = faces.get(0);
                    Rect box = face.getBoundingBox();
                    if (box.width() < 120 || box.height() < 120) {
                        cb.onError("Khuôn mặt quá nhỏ trong ảnh. Đưa điện thoại lại gần hơn.");
                        return;
                    }
                    if (Math.abs(face.getHeadEulerAngleY()) > 30 || Math.abs(face.getHeadEulerAngleZ()) > 25) {
                        cb.onError("Giữ mặt thẳng, nhìn thẳng vào camera rồi chụp lại.");
                        return;
                    }
                    // Cat mo rong 25% quanh mat de FaceNet co du ngu canh (tran/cam).
                    int marginX = (int) (box.width() * 0.25);
                    int marginY = (int) (box.height() * 0.25);
                    int left = Math.max(0, box.left - marginX);
                    int top = Math.max(0, box.top - marginY);
                    int right = Math.min(photo.getWidth(), box.right + marginX);
                    int bottom = Math.min(photo.getHeight(), box.bottom + marginY);
                    if (right - left < 50 || bottom - top < 50) {
                        cb.onError("Không cắt được vùng khuôn mặt. Chụp lại.");
                        return;
                    }
                    Bitmap crop = Bitmap.createBitmap(photo, left, top, right - left, bottom - top);
                    cb.onFace(crop);
                })
                .addOnFailureListener(e -> {
                    detector.close();
                    cb.onError("Lỗi nhận diện khuôn mặt: " + e.getMessage());
                });
    }
}
