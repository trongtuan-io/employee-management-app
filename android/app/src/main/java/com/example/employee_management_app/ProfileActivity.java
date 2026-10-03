package com.example.employee_management_app;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

// Man hinh sua ho so ca nhan.
// Nhan vien: chi sua duoc ho ten + anh. Mail/SDT bi khoa (server giu nguyen).
// Admin (mo kem EMPLOYEE_ID): sua duoc tat ca.
public class ProfileActivity extends AppCompatActivity {
    private static final int PICK_IMAGE = 101;

    private ImageView ivAvatar;
    private EditText etName, etEmail, etPhone;
    private int targetId, myId;
    private boolean isAdminMode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        ivAvatar = findViewById(R.id.ivAvatar);
        etName = findViewById(R.id.etProfileName);
        etEmail = findViewById(R.id.etProfileEmail);
        etPhone = findViewById(R.id.etProfilePhone);
        Button btnAvatar = findViewById(R.id.btnPickAvatar);
        Button btnSave = findViewById(R.id.btnSaveProfile);

        myId = TokenManager.getInstance(this).getUserId();
        String role = TokenManager.getInstance(this).getRole();
        boolean isAdmin = role.equalsIgnoreCase("admin") || role.equalsIgnoreCase("manager");

        // Admin mo tu danh sach NV kem EMPLOYEE_ID -> sua duoc het. Con lai la tu sua.
        if (getIntent().hasExtra("EMPLOYEE_ID") && isAdmin) {
            targetId = getIntent().getIntExtra("EMPLOYEE_ID", myId);
            isAdminMode = true;
            setTitle("Sửa hồ sơ nhân viên");
        } else {
            targetId = myId;
            isAdminMode = false;
            setTitle("Hồ sơ của tôi");
        }
        etEmail.setEnabled(isAdminMode);
        etPhone.setEnabled(isAdminMode);

        loadProfile();
        btnAvatar.setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_GET_CONTENT);
            i.setType("image/*");
            startActivityForResult(Intent.createChooser(i, "Chọn ảnh"), PICK_IMAGE);
        });
        btnSave.setOnClickListener(v -> saveProfile());
    }

    private void loadProfile() {
        EmployeeApi api = ApiClient.getClient(this).create(EmployeeApi.class);
        api.getDetail(targetId).enqueue(new Callback<EmployeeDetailResponse>() {
            @Override
            public void onResponse(Call<EmployeeDetailResponse> c, Response<EmployeeDetailResponse> r) {
                if (r.isSuccessful() && r.body() != null && r.body().isSuccess()
                        && r.body().getData() != null) {
                    EmployeeDetailResponse.Detail d = r.body().getData();
                    etName.setText(d.getFullName());
                    etEmail.setText(d.getEmail());
                    etPhone.setText(d.getPhone());
                    if (d.getAvatarUrl() != null && !d.getAvatarUrl().isEmpty()) {
                        Glide.with(ProfileActivity.this)
                                .load(ApiClient.baseUrl() + d.getAvatarUrl())
                                .into(ivAvatar);
                    }
                }
            }

            @Override
            public void onFailure(Call<EmployeeDetailResponse> c, Throwable t) {
                Toast.makeText(ProfileActivity.this, "Không tải được hồ sơ", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void saveProfile() {
        String name = etName.getText().toString().trim();
        if (name.isEmpty()) {
            Toast.makeText(this, "Họ tên không được trống", Toast.LENGTH_SHORT).show();
            return;
        }
        EmployeeApi api = ApiClient.getClient(this).create(EmployeeApi.class);
        UpdateEmployeeRequest req = new UpdateEmployeeRequest(targetId, myId, name,
                etEmail.getText().toString().trim(), etPhone.getText().toString().trim());
        api.updateEmployee(req).enqueue(new Callback<ApiResponse>() {
            @Override
            public void onResponse(Call<ApiResponse> c, Response<ApiResponse> r) {
                if (r.isSuccessful() && r.body() != null && r.body().isSuccess()) {
                    Toast.makeText(ProfileActivity.this, "Đã lưu!", Toast.LENGTH_SHORT).show();
                    finish();
                } else {
                    Toast.makeText(ProfileActivity.this, "Lưu thất bại", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<ApiResponse> c, Throwable t) {
                Toast.makeText(ProfileActivity.this, "Lỗi kết nối: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_IMAGE && resultCode == RESULT_OK && data != null && data.getData() != null) {
            uploadAvatar(data.getData());
        }
    }

    // Copy anh vao cache roi upload multipart (khong can quyen doc bo nho)
    private void uploadAvatar(Uri uri) {
        try {
            InputStream in = getContentResolver().openInputStream(uri);
            File f = new File(getCacheDir(), "avatar_tmp.jpg");
            OutputStream out = new FileOutputStream(f);
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            in.close();
            out.close();

            RequestBody reqFile = RequestBody.create(MediaType.parse("image/*"), f);
            MultipartBody.Part part = MultipartBody.Part.createFormData("avatar", f.getName(), reqFile);
            RequestBody empId = RequestBody.create(MediaType.parse("text/plain"), String.valueOf(targetId));
            RequestBody reqId = RequestBody.create(MediaType.parse("text/plain"), String.valueOf(myId));

            EmployeeApi api = ApiClient.getClient(this).create(EmployeeApi.class);
            api.uploadAvatar(empId, reqId, part).enqueue(new Callback<AvatarResponse>() {
                @Override
                public void onResponse(Call<AvatarResponse> c, Response<AvatarResponse> r) {
                    if (r.isSuccessful() && r.body() != null && r.body().isSuccess()
                            && r.body().getData() != null) {
                        Glide.with(ProfileActivity.this)
                                .load(ApiClient.baseUrl() + r.body().getData().getAvatarUrl())
                                .into(ivAvatar);
                        Toast.makeText(ProfileActivity.this, "Đổi ảnh xong!", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(ProfileActivity.this, "Upload thất bại (ảnh jpg/png dưới 2MB)", Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void onFailure(Call<AvatarResponse> c, Throwable t) {
                    Toast.makeText(ProfileActivity.this, "Lỗi kết nối: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
        } catch (Exception e) {
            Toast.makeText(this, "Không đọc được ảnh", Toast.LENGTH_SHORT).show();
        }
    }
}
