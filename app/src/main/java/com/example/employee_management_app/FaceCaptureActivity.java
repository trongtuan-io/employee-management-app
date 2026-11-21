package com.example.employee_management_app;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// Man chup khuon mat dung chung cho DANG KY va CHAM CONG:
// mo camera -> phat hien/cat mat bang ML Kit -> trich embedding FaceNet
// -> tra float[] qua result voi khoa "embedding". Man goi tu quyet dinh
// gui embedding len endpoint nao (face-enroll hay checkin/checkout).
// Anh goc chi nam tam trong cache cua app va bi xoa ngay sau khi xu ly.
public class FaceCaptureActivity extends AppCompatActivity {
    public static final String EXTRA_PURPOSE = "purpose"; // "enroll" | "verify"
    public static final String EXTRA_EMBEDDING = "embedding";

    private ImageView ivPreview;
    private TextView tvStatus, tvTitle;
    private Button btnCapture;
    private File photoFile;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();

    // App da khai quyen CAMERA trong Manifest nen phai xin quyen runtime truoc;
    // thieu quyen thi Intent chup anh bi Android chan ("revoked permission CAMERA").
    private final ActivityResultLauncher<String> requestCameraPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    openCamera();
                } else {
                    tvStatus.setText("App cần quyền Camera để chụp khuôn mặt. Bấm nút bên dưới rồi chọn Cho phép.");
                    btnCapture.setEnabled(true);
                }
            });

    private final ActivityResultLauncher<Uri> takePicture =
            registerForActivityResult(new ActivityResultContracts.TakePicture(), ok -> {
                if (ok && photoFile != null && photoFile.exists()) {
                    processPhoto();
                } else {
                    tvStatus.setText("Đã huỷ chụp. Bấm nút bên dưới để chụp lại.");
                    btnCapture.setEnabled(true);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_face_capture);
        ivPreview = findViewById(R.id.ivFacePreview);
        tvStatus = findViewById(R.id.tvFaceStatus);
        tvTitle = findViewById(R.id.tvFaceTitle);
        btnCapture = findViewById(R.id.btnCaptureFace);
        boolean enroll = "enroll".equals(getIntent().getStringExtra(EXTRA_PURPOSE));
        tvTitle.setText(enroll ? "Đăng ký khuôn mặt" : "Chấm công bằng khuôn mặt");
        tvStatus.setText("Đưa khuôn mặt vào khung hình, đủ sáng, nhìn thẳng vào camera.");
        btnCapture.setOnClickListener(v -> launchCamera());
        launchCamera(); // mo camera ngay cho nhanh; nut bam de chup lai khi can
    }

    private void launchCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            openCamera();
        } else {
            requestCameraPermission.launch(Manifest.permission.CAMERA);
        }
    }

    private void openCamera() {
        try {
            File dir = new File(getCacheDir(), "face_capture");
            if (!dir.exists()) dir.mkdirs();
            photoFile = new File(dir, "capture_" + System.currentTimeMillis() + ".jpg");
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", photoFile);
            btnCapture.setEnabled(false);
            takePicture.launch(uri);
        } catch (Exception e) {
            tvStatus.setText("Không mở được camera: " + e.getMessage());
            btnCapture.setEnabled(true);
        }
    }

    private void processPhoto() {
        tvStatus.setText("Đang xử lý khuôn mặt...");
        worker.execute(() -> {
            Bitmap photo = null;
            try {
                photo = decodeRotated(photoFile);
            } catch (Exception ignored) { }
            if (photo == null) {
                runOnUiThread(() -> {
                    tvStatus.setText("Không đọc được ảnh vừa chụp. Chụp lại.");
                    btnCapture.setEnabled(true);
                });
                return;
            }
            Bitmap finalPhoto = photo;
            runOnUiThread(() -> {
                ivPreview.setImageBitmap(finalPhoto);
                FaceImageHelper.extractFace(finalPhoto, new FaceImageHelper.Callback() {
                    @Override
                    public void onFace(Bitmap faceCrop) {
                        worker.execute(() -> {
                            try {
                                float[] emb = FaceNetHelper.getEmbedding(FaceCaptureActivity.this, faceCrop);
                                cleanup();
                                Intent data = new Intent();
                                data.putExtra(EXTRA_EMBEDDING, emb);
                                setResult(RESULT_OK, data);
                                finish();
                            } catch (Exception e) {
                                runOnUiThread(() -> {
                                    tvStatus.setText("Lỗi trích xuất đặc trưng khuôn mặt: " + e.getMessage());
                                    btnCapture.setEnabled(true);
                                });
                            }
                        });
                    }

                    @Override
                    public void onError(String message) {
                        tvStatus.setText(message);
                        btnCapture.setEnabled(true);
                    }
                });
            });
        });
    }

    // Giai ma anh full-size (gioi han ~1280px cho nhe) + xoay dung chieu theo EXIF
    // (camera truoc/sau luu anh xoay khac nhau; khong xoay thi ML Kit kho thay mat).
    private Bitmap decodeRotated(File file) throws Exception {
        int rotation = 0;
        ExifInterface exif = new ExifInterface(file.getAbsolutePath());
        int o = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
        if (o == ExifInterface.ORIENTATION_ROTATE_90) rotation = 90;
        else if (o == ExifInterface.ORIENTATION_ROTATE_180) rotation = 180;
        else if (o == ExifInterface.ORIENTATION_ROTATE_270) rotation = 270;

        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
        int maxDim = Math.max(bounds.outWidth, bounds.outHeight);
        int sample = 1;
        while (maxDim / sample > 1280) sample *= 2;
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = sample;
        Bitmap bmp = BitmapFactory.decodeFile(file.getAbsolutePath(), opts);
        if (bmp == null || rotation == 0) return bmp;
        Matrix mtx = new Matrix();
        mtx.postRotate(rotation);
        return Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), mtx, true);
    }

    private void cleanup() {
        try {
            if (photoFile != null && photoFile.exists()) photoFile.delete();
        } catch (Exception ignored) { }
    }

    @Override
    protected void onDestroy() {
        cleanup();
        worker.shutdownNow();
        super.onDestroy();
    }
}
