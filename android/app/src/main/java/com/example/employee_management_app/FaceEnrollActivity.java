package com.example.employee_management_app;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

// Man Sinh trac hoc: xem trang thai dang ky khuon mat, dang ky/dang ky lai
// (chup anh -> FaceCaptureActivity trich embedding -> gui vector len server)
// va xoa dang ky. Van tay khong can dang ky trong app — dung van tay da
// dang ky trong Cai dat cua dien thoai.
public class FaceEnrollActivity extends AppCompatActivity {
    private EmployeeApi api;
    private int myId;
    private TextView tvStatus;
    private Button btnEnroll, btnRemove;

    private final ActivityResultLauncher<Intent> captureLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    float[] emb = result.getData().getFloatArrayExtra(FaceCaptureActivity.EXTRA_EMBEDDING);
                    if (emb != null && emb.length > 0) {
                        uploadEmbedding(emb);
                        return;
                    }
                }
                Toast.makeText(this, "Chưa lấy được khuôn mặt, thử lại sau", Toast.LENGTH_SHORT).show();
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_face_enroll);
        api = ApiClient.getClient(this).create(EmployeeApi.class);
        myId = TokenManager.getInstance(this).getUserId();
        tvStatus = findViewById(R.id.tvFaceEnrollStatus);
        btnEnroll = findViewById(R.id.btnEnrollFace);
        btnRemove = findViewById(R.id.btnRemoveFace);
        btnEnroll.setOnClickListener(v -> {
            Intent it = new Intent(this, FaceCaptureActivity.class);
            it.putExtra(FaceCaptureActivity.EXTRA_PURPOSE, "enroll");
            captureLauncher.launch(it);
        });
        btnRemove.setOnClickListener(v -> removeFace());
        loadStatus();
    }

    private void loadStatus() {
        tvStatus.setText("Đang kiểm tra trạng thái đăng ký...");
        btnRemove.setVisibility(View.GONE);
        api.faceStatus().enqueue(new Callback<FaceStatusResponse>() {
            @Override
            public void onResponse(Call<FaceStatusResponse> c, Response<FaceStatusResponse> r) {
                if (r.isSuccessful() && r.body() != null && r.body().getData() != null) {
                    FaceStatusResponse.Data d = r.body().getData();
                    if (d.isRegistered()) {
                        tvStatus.setText("Đã đăng ký khuôn mặt"
                                + (d.getUpdatedAt() != null ? "\nCập nhật: " + d.getUpdatedAt() : "")
                                + "\nBạn có thể chấm công bằng nút Khuôn mặt ở Trang chủ.");
                        btnEnroll.setText("Đăng ký lại khuôn mặt");
                        btnRemove.setVisibility(View.VISIBLE);
                    } else {
                        tvStatus.setText("Chưa đăng ký khuôn mặt.\nĐăng ký 1 lần để chấm công bằng khuôn mặt.");
                        btnEnroll.setText("Chụp & đăng ký khuôn mặt");
                    }
                } else {
                    tvStatus.setText(ApiError.message(r, "Không tải được trạng thái đăng ký"));
                }
            }

            @Override
            public void onFailure(Call<FaceStatusResponse> c, Throwable t) {
                tvStatus.setText("Lỗi kết nối: " + t.getMessage());
            }
        });
    }

    private void uploadEmbedding(float[] emb) {
        List<Double> list = new ArrayList<>(emb.length);
        for (float v : emb) list.add((double) v);
        btnEnroll.setEnabled(false);
        api.faceEnroll(new FaceEnrollRequest(list, "facenet-128")).enqueue(new Callback<ApiResponse>() {
            @Override
            public void onResponse(Call<ApiResponse> c, Response<ApiResponse> r) {
                btnEnroll.setEnabled(true);
                if (r.isSuccessful() && r.body() != null && r.body().isSuccess()) {
                    Toast.makeText(FaceEnrollActivity.this, "Đăng ký khuôn mặt thành công", Toast.LENGTH_SHORT).show();
                    loadStatus();
                } else {
                    Toast.makeText(FaceEnrollActivity.this,
                            ApiError.message(r, "Không đăng ký được khuôn mặt"), Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(Call<ApiResponse> c, Throwable t) {
                btnEnroll.setEnabled(true);
                Toast.makeText(FaceEnrollActivity.this, "Lỗi kết nối: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void removeFace() {
        btnRemove.setEnabled(false);
        api.faceRemove(new FaceRemoveRequest(myId)).enqueue(new Callback<ApiResponse>() {
            @Override
            public void onResponse(Call<ApiResponse> c, Response<ApiResponse> r) {
                btnRemove.setEnabled(true);
                if (r.isSuccessful() && r.body() != null && r.body().isSuccess()) {
                    Toast.makeText(FaceEnrollActivity.this, "Đã xoá đăng ký khuôn mặt", Toast.LENGTH_SHORT).show();
                    loadStatus();
                } else {
                    Toast.makeText(FaceEnrollActivity.this,
                            ApiError.message(r, "Không xoá được đăng ký"), Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(Call<ApiResponse> c, Throwable t) {
                btnRemove.setEnabled(true);
                Toast.makeText(FaceEnrollActivity.this, "Lỗi kết nối: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }
}
