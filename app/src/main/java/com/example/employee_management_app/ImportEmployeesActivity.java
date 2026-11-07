package com.example.employee_management_app;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.List;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ImportEmployeesActivity extends AppCompatActivity {
    private static final int REQUEST_FILE = 512;
    private TextView status;
    private Button choose;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_import_employees);
        setTitle("Nhập Excel nhân viên");
        status = findViewById(R.id.tvImportStatus);
        choose = findViewById(R.id.btnChooseImport);
        if (!"admin".equalsIgnoreCase(TokenManager.getInstance(this).getRole())) {
            choose.setEnabled(false); status.setText("Chỉ quản trị viên được import dữ liệu."); return;
        }
        choose.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "text/csv", "text/comma-separated-values", "application/octet-stream"});
            startActivityForResult(intent, REQUEST_FILE);
        });
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != REQUEST_FILE || result != Activity.RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        String fileName = "import.xlsx";
        try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) fileName = cursor.getString(idx);
            }
        } catch (Exception ignored) { }
        if (fileName == null || !(fileName.toLowerCase().endsWith(".xlsx") || fileName.toLowerCase().endsWith(".csv"))) {
            status.setText("Chỉ chọn file .xlsx hoặc .csv"); return;
        }
        final String selectedName = fileName;
        new AlertDialog.Builder(this).setTitle("Xác nhận import")
                .setMessage("Nhập tất cả nhân viên từ " + fileName + "? Dòng bị lỗi sẽ khiến cả lần nhập bị hủy.")
                .setNegativeButton("Hủy", null).setPositiveButton("Nhập", (d,w) -> upload(uri, selectedName)).show();
    }
    private void upload(Uri uri, String fileName) {
        choose.setEnabled(false); status.setText("Đang import…");
        // Bound memory to server's 3MB limit; never read an unbounded file into RAM.
        try (InputStream input = getContentResolver().openInputStream(uri);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (input == null) throw new IllegalArgumentException("Không mở được file");
            byte[] buffer = new byte[8192]; int n;
            while ((n = input.read(buffer)) != -1) {
                if (output.size() + n > 3*1024*1024) throw new IllegalArgumentException("File quá 3MB");
                output.write(buffer,0,n);
            }
            String contentType = fileName.toLowerCase().endsWith(".csv") ? "text/csv" : "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            RequestBody body = RequestBody.create(MediaType.parse(contentType), output.toByteArray());
            MultipartBody.Part file = MultipartBody.Part.createFormData("file", fileName, body);
            RequestBody requesterId = RequestBody.create(MediaType.parse("text/plain"),
                    String.valueOf(TokenManager.getInstance(this).getUserId()));
            ApiClient.getClient(this).create(EmployeeApi.class).importEmployees(requesterId, file)
                    .enqueue(new Callback<EmployeeImportResponse>() {
                        @Override public void onResponse(Call<EmployeeImportResponse> call, Response<EmployeeImportResponse> response) {
                            choose.setEnabled(true);
                            EmployeeImportResponse r = response.body();
                            if (!response.isSuccessful() || r == null || !r.isSuccess() || r.getData() == null) {
                                status.setText("Nhập thất bại (HTTP " + response.code() + "). Kiểm tra tên cột, dữ liệu trùng và migration."); return;
                            }
                            status.setText("Đã nhập " + r.getData().getImported() + " nhân viên. Lưu mật khẩu tạm ngay; chỉ hiển thị một lần.");
                            List<EmployeeImportResponse.Credential> creds = r.getData().getCredentials();
                            if (creds != null && !creds.isEmpty()) {
                                StringBuilder text = new StringBuilder();
                                for (EmployeeImportResponse.Credential c : creds) {
                                    text.append(c.getUsername()).append(" : ").append(c.getPassword()).append('\n');
                                }
                                // No clipboard or persistent logging of temporary credentials.
                                new AlertDialog.Builder(ImportEmployeesActivity.this)
                                        .setTitle("Mật khẩu tạm - LƯU NGAY")
                                        .setMessage(text.toString()).setPositiveButton("Đã lưu", null).show();
                            }
                        }
                        @Override public void onFailure(Call<EmployeeImportResponse> call, Throwable t) {
                            choose.setEnabled(true); status.setText("Mất kết nối. Kiểm tra Database trước khi nhập lại để tránh trùng.");
                        }
                    });
        } catch (Exception e) {
            choose.setEnabled(true);
            status.setText("Không đọc được file: " + e.getMessage());
        }
    }
}
