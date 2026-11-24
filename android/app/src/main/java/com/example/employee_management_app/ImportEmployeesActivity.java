package com.example.employee_management_app;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
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
    private Call<EmployeeImportResponse> importCall;

    @Override protected void onDestroy() {
        if (importCall != null) importCall.cancel();
        super.onDestroy();
    }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_import_employees);
        setTitle("Nhập Excel/CSV - HR");
        status = findViewById(R.id.tvImportStatus);
        choose = findViewById(R.id.btnChooseImport);
        if (!HrUi.hr(this)) {
            choose.setEnabled(false); status.setText("Chỉ phòng Nhân sự được import dữ liệu."); return;
        }
        choose.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "text/csv", "text/comma-separated-values", "application/octet-stream"});
            startActivityForResult(intent, REQUEST_FILE);
        });
        findViewById(R.id.btnImportHistory).setOnClickListener(v->showHistory());
    }

    private void showHistory(){
        HrUi.call(this,HrUi.api(this).importBatches(),data->{
            StringBuilder history=new StringBuilder();
            for(Map<String,Object> row:HrUi.rows(data)){
                if(history.length()>0)history.append("\n\n");
                history.append("#").append(HrUi.id(row.get("id"))).append(" · ")
                        .append(HrUi.s(row.get("created_at"))).append('\n')
                        .append(HrUi.s(row.get("source_name"))).append('\n')
                        .append("Tổng ").append(HrUi.n(row.get("rows_total")))
                        .append(" · Tạo ").append(HrUi.n(row.get("rows_created")))
                        .append(" · Cập nhật ").append(HrUi.n(row.get("rows_updated")));
                int proposed=HrUi.n(row.get("rows_proposed")), refreshed=HrUi.n(row.get("rows_refreshed"));
                if(proposed+refreshed>0)history.append(" · Đề xuất cũ ").append(proposed)
                        .append(" · Làm mới ").append(refreshed);
            }
            new AlertDialog.Builder(this).setTitle("Lịch sử import")
                    .setMessage(history.length()==0?"Chưa có lần import nào.":history)
                    .setPositiveButton("Đóng",null).show();
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
                .setMessage("Tạo tài khoản Staff từ " + fileName + "? Nếu có dòng lỗi, toàn bộ đợt nhập sẽ bị hủy.")
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
            importCall = ApiClient.getClient(this).create(EmployeeApi.class).importEmployees(requesterId, file);
            importCall.enqueue(new Callback<EmployeeImportResponse>() {
                        @Override public void onResponse(Call<EmployeeImportResponse> call, Response<EmployeeImportResponse> response) {
                            if (isFinishing() || isDestroyed() || call.isCanceled()) return;
                            choose.setEnabled(true);
                            EmployeeImportResponse r = response.body();
                            if (!response.isSuccessful() || r == null || !r.isSuccess() || r.getData() == null) {
                                status.setText(ApiError.message(response,"Nhập thất bại")); return;
                            }
                            status.setText("Đã tạo " + r.getData().getImported()
                                    + " tài khoản Staff · Cập nhật " + r.getData().getUpdated()
                                    + " · Giữ nguyên " + r.getData().getUnchanged());
                            List<EmployeeImportResponse.Credential> creds = r.getData().getCredentials();
                            if (creds != null && !creds.isEmpty()) {
                                StringBuilder text = new StringBuilder();
                                for (EmployeeImportResponse.Credential c : creds) {
                                    text.append(c.getUsername()).append(" : ").append(c.getPassword()).append('\n');
                                }
                                // No clipboard or persistent logging of temporary credentials.
                                AlertDialog dialog=new AlertDialog.Builder(ImportEmployeesActivity.this)
                                        .setTitle("Mật khẩu tạm · Hiển thị một lần")
                                        .setMessage(text.toString()).setPositiveButton("Đóng", null).create();
                                if(dialog.getWindow()!=null)dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
                                dialog.show();
                            }
                        }
                        @Override public void onFailure(Call<EmployeeImportResponse> call, Throwable t) {
                            if (isFinishing() || isDestroyed() || call.isCanceled()) return;
                            choose.setEnabled(true); status.setText("Mất kết nối. Kiểm tra Database trước khi nhập lại để tránh trùng.");
                        }
                    });
        } catch (Exception e) {
            choose.setEnabled(true);
            status.setText("Không đọc được file: " + e.getMessage());
        }
    }
}
