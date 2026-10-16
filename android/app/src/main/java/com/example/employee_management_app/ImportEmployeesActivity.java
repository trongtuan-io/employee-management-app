package com.example.employee_management_app;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import org.json.JSONObject;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import okhttp3.ResponseBody;

/** Manage local Excel/CSV documents; sync/rollback batches separately in MySQL. */
public class ImportEmployeesActivity extends AppCompatActivity {
    private static final int REQUEST_FILE=512;
    private static final String PREFS="imported_source_files";
    private static final String KEY_URIS="completed_import_file_uris";
    private static final String KEY_BATCH_PREFIX="batch_for_";
    private TextView status;
    private Button choose;
    private EmployeeApi api;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_import_employees);
        setTitle("Nhập Excel nhân viên");
        status=findViewById(R.id.tvImportStatus);
        choose=findViewById(R.id.btnChooseImport);
        api=ApiClient.getClient(this).create(EmployeeApi.class);
        findViewById(R.id.btnManageImportFiles).setOnClickListener(v->chooseImportedFile());
        findViewById(R.id.btnImportBatchHistory).setOnClickListener(v->chooseBatchHistory());
        findViewById(R.id.btnManageImportedEmployees).setOnClickListener(v->
                startActivity(new Intent(this,StaffListActivity.class)));
        if(!"admin".equalsIgnoreCase(TokenManager.getInstance(this).getRole())) {
            choose.setEnabled(false);
            findViewById(R.id.btnManageImportFiles).setEnabled(false);
            findViewById(R.id.btnImportBatchHistory).setEnabled(false);
            findViewById(R.id.btnManageImportedEmployees).setEnabled(false);
            status.setText("Chỉ Admin được import và quản lý file.");return;
        }
        choose.setOnClickListener(v->{
            Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            intent.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "text/csv",
                "text/comma-separated-values","application/octet-stream"});
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            startActivityForResult(intent,REQUEST_FILE);
        });
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if(request!=REQUEST_FILE||result!=Activity.RESULT_OK||data==null||data.getData()==null)return;
        Uri uri=data.getData();
        try {
            int granted=data.getFlags()&(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            getContentResolver().takePersistableUriPermission(uri,granted);
        }catch(Exception ignored) { }
        String name=displayName(uri);
        if(!isSheet(name)){status.setText("Chỉ chọn .xlsx hoặc .csv");return;}
        new AlertDialog.Builder(this).setTitle("Xác nhận import")
                .setMessage("Nhập nhân viên từ "+name+"? Dữ liệu sai sẽ hủy cả lần nhập.")
                .setNegativeButton("Hủy",null)
                .setPositiveButton("Nhập",(d,w)->importNew(uri,name)).show();
    }
    private int userId(){return TokenManager.getInstance(this).getUserId();}
    private String displayName(Uri uri) {
        String internalName = ImportFileStore.visibleName(this, uri);
        if (internalName != null) return internalName;
        try(Cursor cursor=getContentResolver().query(uri,null,null,null,null)) {
            if(cursor!=null&&cursor.moveToFirst()) {
                int column=cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if(column>=0&&cursor.getString(column)!=null)return cursor.getString(column);
            }
        } catch(Exception ignored) { }
        return uri.getLastPathSegment()==null?"Không xác định":uri.getLastPathSegment();
    }
    private boolean isSheet(String name){
        String lower=name.toLowerCase(java.util.Locale.ROOT);
        return lower.endsWith(".xlsx")||lower.endsWith(".csv");
    }
    private String error(Response<?> response) {
        try(ResponseBody body=response.errorBody()) {
            if(body!=null) return new JSONObject(body.string()).optString("error","HTTP "+response.code());
        }catch(Exception ignored){ }
        return "HTTP "+response.code();
    }
    private MultipartBody.Part part(byte[] bytes,String filename) {
        String mime=filename.toLowerCase(java.util.Locale.ROOT).endsWith(".csv")?"text/csv":
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        return MultipartBody.Part.createFormData("file",filename,
                RequestBody.create(MediaType.parse(mime),bytes));
    }
    private RequestBody textBody(String text){return RequestBody.create(MediaType.parse("text/plain"),text);}
    private void importNew(Uri uri,String filename) {
        choose.setEnabled(false);status.setText("Đang nhập file…");
        try {
            // Save exactly the bytes that will be sent to MySQL. Avoid the source changing
            // while a network request is in flight.
            final byte[] uploadedBytes = ImportFileStore.read(this, uri);
            ImportSheetDocument preview = ImportSheetDocument.open(
                    new java.io.ByteArrayInputStream(uploadedBytes), filename);
            String validationError = ImportSheetRules.validateDocument(preview,
                    filename.toLowerCase(java.util.Locale.ROOT).endsWith(".xlsx"));
            if (validationError != null) {
                choose.setEnabled(true);
                status.setText("File Excel/CSV không hợp lệ: " + validationError);
                return;
            }
            api.importEmployees(textBody(Integer.toString(userId())),part(uploadedBytes,filename))
                    .enqueue(new Callback<EmployeeImportResponse>() {
                        @Override public void onResponse(Call<EmployeeImportResponse> call,Response<EmployeeImportResponse> response) {
                            choose.setEnabled(true);
                            EmployeeImportResponse data=response.body();
                            if(!response.isSuccessful()||data==null||!data.isSuccess()||data.getData()==null) {
                                status.setText("Import thất bại: "+error(response));return;
                            }
                            long batchId=data.getData().getBatchId();
                            try {
                                Uri saved = ImportFileStore.saveBatch(ImportEmployeesActivity.this,
                                        batchId, filename, uploadedBytes);
                                remember(saved,batchId);
                                status.setText("Đã nhập "+data.getData().getImported()+" nhân viên. Đợt #"+batchId
                                        +". Đã lưu bản Excel/CSV riêng cho đợt này; sửa file không ảnh hưởng các đợt khác.");
                            } catch (Exception storeError) {
                                status.setText("Đã nhập "+data.getData().getImported()+" nhân viên vào MySQL, đợt #"+batchId
                                        +", NHƯNG không lưu được bản Excel trên máy: "
                                        +storeError.getMessage()+". Có thể Rollback qua Lịch sử import.");
                            }
                            List<EmployeeImportResponse.Credential> credentials=data.getData().getCredentials();
                            if(credentials!=null&&!credentials.isEmpty()) {
                                StringBuilder message=new StringBuilder();
                                for(EmployeeImportResponse.Credential c:credentials)
                                    message.append(c.getUsername()).append(" : ").append(c.getPassword()).append('\n');
                                new AlertDialog.Builder(ImportEmployeesActivity.this)
                                        .setTitle("LƯU NGAY mật khẩu tạm - chỉ hiện một lần")
                                        .setMessage(message.toString()).setPositiveButton("Đã lưu",null).show();
                            }
                        }
                        @Override public void onFailure(Call<EmployeeImportResponse> call,Throwable t) {
                            choose.setEnabled(true);
                            status.setText("Mất kết nối. Kiểm tra MySQL trước khi thử lại để tránh tạo trùng.");
                        }
                    });
        } catch(Exception e){choose.setEnabled(true);status.setText("Không đọc được file: "+e.getMessage());}
    }
    private void remember(Uri uri,long batchId) {
        SharedPreferences prefs=getSharedPreferences(PREFS,MODE_PRIVATE);
        Set<String> uris=new HashSet<>(prefs.getStringSet(KEY_URIS,Collections.emptySet()));
        uris.add(uri.toString());
        prefs.edit().putStringSet(KEY_URIS,uris).putLong(KEY_BATCH_PREFIX+uri.toString(),batchId).apply();
    }
    private long batchId(Uri uri) {
        return getSharedPreferences(PREFS,MODE_PRIVATE).getLong(KEY_BATCH_PREFIX+uri.toString(),0);
    }
    private void forget(Uri uri) {
        SharedPreferences prefs=getSharedPreferences(PREFS,MODE_PRIVATE);
        Set<String> uris=new HashSet<>(prefs.getStringSet(KEY_URIS,Collections.emptySet()));
        uris.remove(uri.toString());
        prefs.edit().putStringSet(KEY_URIS,uris).remove(KEY_BATCH_PREFIX+uri.toString()).apply();
    }
    private void chooseImportedFile() {
        // Import batches created by old app versions reused external SAF documents.
        // Migrate each to its own internal snapshot once; leave the external original alone.
        migrateLegacyBatchFiles();
        Set<String> uris=getSharedPreferences(PREFS,MODE_PRIVATE).getStringSet(KEY_URIS,Collections.emptySet());
        if(uris.isEmpty()){status.setText("Chưa có file import trên thiết bị.");return;}
        ArrayList<Uri> files=new ArrayList<>();
        for(String name:uris)files.add(Uri.parse(name));
        Collections.sort(files,(a,b)->{
            int byBatch=Long.compare(batchId(b),batchId(a));
            return byBatch != 0 ? byBatch : displayName(a).compareToIgnoreCase(displayName(b));
        });
        String[] names=new String[files.size()];
        for(int i=0;i<names.length;i++) {
            long batch=batchId(files.get(i));
            names[i]=displayName(files.get(i))+(batch>0?" (đợt #"+batch+")":" (import cũ)");
        }
        new AlertDialog.Builder(this).setTitle("File Excel/CSV đã import")
                .setItems(names,(d,index)->manage(files.get(index)))
                .setNegativeButton("Đóng",null).show();
    }
    private void migrateLegacyBatchFiles() {
        SharedPreferences prefs = getSharedPreferences(PREFS,MODE_PRIVATE);
        Set<String> stored = prefs.getStringSet(KEY_URIS,Collections.emptySet());
        for (String value : new HashSet<>(stored)) {
            Uri oldUri = Uri.parse(value);
            long id = batchId(oldUri);
            if (id <= 0 || ImportFileStore.isLocal(oldUri)) continue;
            try {
                Uri saved = ImportFileStore.saveBatch(this,id,displayName(oldUri),
                        ImportFileStore.read(this,oldUri));
                Set<String> current = new HashSet<>(prefs.getStringSet(KEY_URIS,Collections.emptySet()));
                current.remove(value);
                current.add(saved.toString());
                if (!prefs.edit().putStringSet(KEY_URIS,current)
                        .remove(KEY_BATCH_PREFIX+value)
                        .putLong(KEY_BATCH_PREFIX+saved.toString(),id).commit()) {
                    throw new java.io.IOException("Không cập nhật được danh sách file");
                }
            } catch (Exception ex) {
                status.setText("Không thể tách bản Excel cũ của đợt #"+id+": "
                        +ex.getMessage()+". Giữ nguyên file nguồn.");
            }
        }
    }

    private void chooseBatchHistory() {
        api.getImportBatchHistory(userId()).enqueue(new Callback<EmployeeBatchResponse>() {
            @Override public void onResponse(Call<EmployeeBatchResponse> call,Response<EmployeeBatchResponse> response) {
                EmployeeBatchResponse r=response.body();
                if(!response.isSuccessful()||r==null||!r.isSuccess()||r.getData()==null) {
                    status.setText("Không xem được lịch sử: "+error(response));return;
                }
                List<EmployeeBatchResponse.Batch> batches=r.getData().getBatches();
                if(batches==null||batches.isEmpty()){status.setText("Chưa có đợt import nào được ghi nhận.");return;}
                String[] labels=new String[batches.size()];
                for(int i=0;i<labels.length;i++) {
                    EmployeeBatchResponse.Batch b=batches.get(i);
                    labels[i]="#"+b.getId()+" · "+b.getSourceName()+" · "+b.getTotalRows()+" NV · "+b.getStatus();
                }
                new AlertDialog.Builder(ImportEmployeesActivity.this)
                        .setTitle("Lịch sử import trên MySQL")
                        .setItems(labels,(d,index)->{
                            EmployeeBatchResponse.Batch b=batches.get(index);
                            if("active".equals(b.getStatus()))previewRollback(b.getId());
                            else status.setText("Đợt #"+b.getId()+" đã rollback trước đó.");
                        })
                        .setNegativeButton("Đóng",null).show();
            }
            @Override public void onFailure(Call<EmployeeBatchResponse> call,Throwable t) {
                status.setText("Không kết nối được API lịch sử import.");
            }
        });
    }
    private void manage(Uri uri) {
        long id=batchId(uri);
        String[] actions=id>0
                ?new String[]{"Xem file trong ứng dụng","Sửa file trong ứng dụng","Cập nhật MySQL từ file đã sửa","Rollback lần import","Xóa file Excel/CSV trên thiết bị"}
                :new String[]{"Xem file trong ứng dụng","Sửa file trong ứng dụng","Xóa file Excel/CSV trên thiết bị"};
        new AlertDialog.Builder(this).setTitle(displayName(uri))
                .setItems(actions,(dialog,index)->{
                    if(index==0){openEditor(uri,false);return;}
                    if(index==1){openEditor(uri,true);return;}
                    if(id>0 && index==2){confirmSync(uri,id);return;}
                    if(id>0 && index==3){previewRollback(id);return;}
                    if((id>0&&index==4)||(id==0&&index==2)){confirmDelete(uri);}
                })
                .setNegativeButton("Đóng",null).show();
        if(id==0)status.setText("File import cũ chưa có ID lần import: vẫn xem/sửa được, nhưng không thể đồng bộ hoặc Rollback an toàn. Chỉ những lần import mới sau migration được hỗ trợ.");
    }
    private void openEditor(Uri uri,boolean edit) {
        Intent intent=new Intent(this,ImportSheetActivity.class);
        intent.putExtra(ImportSheetActivity.EXTRA_URI,uri.toString());
        intent.putExtra(ImportSheetActivity.EXTRA_NAME,displayName(uri));
        intent.putExtra(ImportSheetActivity.EXTRA_EDIT,edit);
        intent.putExtra(ImportSheetActivity.EXTRA_BATCH_ID,batchId(uri));
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        if(edit)intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        startActivity(intent);
    }
    private void confirmSync(Uri uri,long id) {
        new AlertDialog.Builder(this).setTitle("Cập nhật MySQL từ file?")
                .setMessage("Chỉ các dòng đã thay đổi được cập nhật vào đúng ID nhân viên của đợt #"+id+". "
                        +"Không tạo người mới. Nếu hồ sơ đã thay đổi bên ngoài Excel, toàn bộ yêu cầu bị chặn.")
                .setNegativeButton("Hủy",null)
                .setPositiveButton("Cập nhật",(d,w)->sync(uri,id)).show();
    }
    private void sync(Uri uri,long id) {
        choose.setEnabled(false);status.setText("Đang đồng bộ đợt #"+id+"…");
        try {
            byte[] latest = ImportFileStore.read(this, uri);
            String filename = displayName(uri);
            ImportSheetDocument preview = ImportSheetDocument.open(
                    new java.io.ByteArrayInputStream(latest), filename);
            String validationError = ImportSheetRules.validateDocument(preview,
                    filename.toLowerCase(java.util.Locale.ROOT).endsWith(".xlsx"));
            if (validationError != null) {
                choose.setEnabled(true);
                status.setText("File Excel/CSV không hợp lệ, chưa đồng bộ: " + validationError);
                return;
            }
            api.syncImportBatch(textBody(Integer.toString(userId())),textBody(Long.toString(id)),part(latest,filename))
                    .enqueue(new Callback<EmployeeBatchResponse>() {
                        @Override public void onResponse(Call<EmployeeBatchResponse> call,Response<EmployeeBatchResponse> response) {
                            choose.setEnabled(true);
                            EmployeeBatchResponse value=response.body();
                            if(!response.isSuccessful()||value==null||!value.isSuccess()||value.getData()==null) {
                                status.setText("Đồng bộ thất bại: "+error(response));return;
                            }
                            status.setText("Đồng bộ thành công: cập nhật "+value.getData().getUpdated()
                                    +"/"+value.getData().getTotal()+" nhân viên; không tạo bản ghi mới.");
                        }
                        @Override public void onFailure(Call<EmployeeBatchResponse> call,Throwable t) {
                            choose.setEnabled(true);status.setText("Mất kết nối khi đồng bộ. Kiểm tra MySQL trước khi thử lại.");
                        }
                    });
        }catch(Exception e){choose.setEnabled(true);status.setText("Không mở được file: "+e.getMessage());}
    }
    private void previewRollback(long id) {
        api.getImportBatchDetail(userId(),id).enqueue(new Callback<EmployeeBatchResponse>() {
            @Override public void onResponse(Call<EmployeeBatchResponse> call,Response<EmployeeBatchResponse> response) {
                EmployeeBatchResponse value=response.body();
                if(!response.isSuccessful()||value==null||!value.isSuccess()||value.getData()==null||value.getData().getBatch()==null) {
                    status.setText("Không kiểm tra được đợt import: "+error(response));return;
                }
                if(!"active".equals(value.getData().getBatch().getStatus())) {
                    status.setText("Đợt này đã được Rollback trước đó.");return;
                }
                int count=value.getData().getBatch().getTotalRows();
                EditText confirmation=new EditText(ImportEmployeesActivity.this);
                confirmation.setSingleLine(true);confirmation.setHint("Nhập ROLLBACK");
                LinearLayoutCompatHolder holder=new LinearLayoutCompatHolder(ImportEmployeesActivity.this,confirmation);
                AlertDialog dialog=new AlertDialog.Builder(ImportEmployeesActivity.this)
                        .setTitle("Hoàn tác đợt #"+id+"?")
                        .setMessage("Dự kiến xóa "+count+" tài khoản được tạo ở đợt này. "
                                +"Hệ thống sẽ CHẶN nếu hồ sơ đã đổi hoặc phát sinh dữ liệu chấm công/lương/nghỉ phép. "
                                +"Nhập ROLLBACK để xác nhận.")
                        .setView(holder.root).setNegativeButton("Hủy",null)
                        .setPositiveButton("Rollback",null).create();
                dialog.setOnShowListener(x->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
                    if(!"ROLLBACK".equals(confirmation.getText().toString().trim())) {
                        confirmation.setError("Hãy nhập đúng ROLLBACK");return;
                    }
                    dialog.dismiss();executeRollback(id);
                }));
                dialog.show();
            }
            @Override public void onFailure(Call<EmployeeBatchResponse> call,Throwable t) {
                status.setText("Không kết nối được Backend để xác nhận rollback.");
            }
        });
    }
    /** Slight padding around the confirmation field without requiring a new XML layout. */
    private static final class LinearLayoutCompatHolder {
        final android.widget.LinearLayout root;
        LinearLayoutCompatHolder(android.content.Context ctx,EditText input) {
            root=new android.widget.LinearLayout(ctx);root.setOrientation(android.widget.LinearLayout.VERTICAL);
            root.setPadding(32,0,32,0);root.addView(input);
        }
    }
    private void executeRollback(long id) {
        choose.setEnabled(false);status.setText("Đang kiểm tra và Rollback đợt #"+id+"…");
        api.rollbackImportBatch(new ImportBatchActionRequest(userId(),id))
                .enqueue(new Callback<EmployeeBatchResponse>() {
                    @Override public void onResponse(Call<EmployeeBatchResponse> call,Response<EmployeeBatchResponse> response) {
                        choose.setEnabled(true);
                        EmployeeBatchResponse value=response.body();
                        if(!response.isSuccessful()||value==null||!value.isSuccess()||value.getData()==null) {
                            status.setText("Không thể Rollback: "+error(response));return;
                        }
                        status.setText("Đã Rollback "+value.getData().getRolledBack()+" nhân viên thuộc đợt #"+id
                                +". File Excel trên thiết bị vẫn được giữ nguyên.");
                    }
                    @Override public void onFailure(Call<EmployeeBatchResponse> call,Throwable t) {
                        choose.setEnabled(true);status.setText("Mất kết nối khi Rollback. Kiểm tra dữ liệu MySQL trước khi thao tác lại.");
                    }
                });
    }
    private void confirmDelete(Uri uri) {
        new AlertDialog.Builder(this).setTitle("Xóa file trên thiết bị?")
                .setMessage(ImportFileStore.isLocal(uri)
                        ? "Xóa bản Excel/CSV riêng của đợt này trong ứng dụng. File gốc đã chọn ở Downloads không bị xóa. MySQL không thay đổi."
                        : "Xóa file nguồn đã chọn và tên trong danh sách trên máy. MySQL không thay đổi; đây không phải Rollback.")
                .setNegativeButton("Hủy",null)
                .setPositiveButton("Xóa file",(d,w)->deleteDocument(uri)).show();
    }
    private void deleteDocument(Uri uri) {
        try {
            if(!ImportFileStore.delete(this,uri)){
                status.setText("Trình quản lý tài liệu từ chối xóa file.");return;
            }
            forget(uri);
            status.setText("Đã xóa file Excel/CSV trong danh sách. MySQL không thay đổi.");
        } catch(Exception e){status.setText("Không xóa được file: "+e.getMessage());}
    }
}
