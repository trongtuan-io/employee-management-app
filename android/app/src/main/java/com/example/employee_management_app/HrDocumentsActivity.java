package com.example.employee_management_app;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class HrDocumentsActivity extends AppCompatActivity {
    private static final int OPEN_DOC=330, SAVE_DOC=331;
    private static final String[] TYPES={"cccd_front","cccd_back","insurance","degree","certificate","contract","other"};
    private int employeeId, selectedDownloadId;
    private Spinner type;
    private TextView summary;
    private ListView list;
    private final List<Map<String,Object>> rows=new ArrayList<>();
    @Override public void onCreate(Bundle state){super.onCreate(state);
        employeeId=getIntent().getIntExtra("EMPLOYEE_ID",TokenManager.getInstance(this).getUserId());
        if(employeeId!=TokenManager.getInstance(this).getUserId()&&!HrUi.hr(this)){finish();return;}
        LinearLayout root=HrUi.page(this,"Giấy tờ nhân sự - NV #"+employeeId);
        summary=HrUi.label(root,"Chưa nộp / Chờ xác nhận / Hợp lệ / Cần bổ sung");
        type=new Spinner(this);type.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,TYPES));root.addView(type);
        HrUi.button(root,"Tải lên PDF / ảnh",()->{
            Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("*/*");i.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"application/pdf","image/jpeg","image/png","image/webp"});
            startActivityForResult(i,OPEN_DOC);
        });
        HrUi.button(root,"Làm mới",this::load);
        list=new ListView(this);list.setMinimumHeight(400);root.addView(list);
        list.setOnItemClickListener((parent,v,pos,id)->{
            if(pos>=rows.size())return;Map<String,Object> d=rows.get(pos);
            int documentId=HrUi.n(d.get("id"));
            String[] options=HrUi.hr(this) && employeeId!=TokenManager.getInstance(this).getUserId()
                    ?new String[]{"Tải xuống","Hợp lệ","Cần bổ sung"}:new String[]{"Tải xuống"};
            new AlertDialog.Builder(this).setTitle(HrUi.s(d.get("original_filename")))
              .setItems(options,(dialog,which)->{
                if(which==0)chooseDownload(documentId);
                else if(which==1)review(documentId,"valid","");
                else {EditText note=new EditText(this);note.setHint("Lý do cần bổ sung");
                    new AlertDialog.Builder(this).setTitle("Yêu cầu bổ sung").setView(note)
                      .setPositiveButton("Gửi",(d2,w)->review(documentId,"needs_update",note.getText().toString()))
                      .setNegativeButton("Hủy",null).show();}
            }).show();
        });
        load();
    }
    private void load(){HrApi api=HrUi.api(this);
        HrUi.call(this,api.documentSummary(employeeId),d->{Map<String,Object> m=HrUi.map(d);
            summary.setText("Hợp lệ "+HrUi.n(m.get("valid"))+" | Chờ xác nhận "+HrUi.n(m.get("pending"))+
              " | Cần bổ sung "+HrUi.n(m.get("needs_update"))+" | Chưa nộp "+HrUi.n(m.get("missing")));});
        HrUi.call(this,api.documents(employeeId),data->{rows.clear();rows.addAll(HrUi.rows(data));
            ArrayList<String> display=new ArrayList<>();
            for(Map<String,Object> row:rows)display.add("#"+HrUi.s(row.get("id"))+" | "+HrUi.s(row.get("document_type"))+
                    "\n"+HrUi.s(row.get("original_filename"))+" - "+HrUi.s(row.get("status"))+
                    "\n"+HrUi.s(row.get("review_note")));
            list.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_list_item_1,display));});
    }
    private void review(int id,String status,String note){Map<String,Object> b=new HashMap<>();b.put("id",id);
        b.put("status",status);b.put("review_note",note);
        HrUi.call(this,HrUi.api(this).reviewDocument(employeeId,b),r->load());
    }
    private void chooseDownload(int id){selectedDownloadId=id;Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("application/octet-stream");
        String filename="hr-document-"+id;
        for(Map<String,Object> row:rows)if(HrUi.n(row.get("id"))==id){
            String original=HrUi.s(row.get("original_filename"));
            String extension=original.contains(".")?original.substring(original.lastIndexOf('.')):"";
            if(extension.matches("\\.[A-Za-z0-9]{1,6}"))filename+=extension;
            break;
        }
        i.putExtra(Intent.EXTRA_TITLE,filename);startActivityForResult(i,SAVE_DOC);}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);
        if(result!=Activity.RESULT_OK||data==null||data.getData()==null)return;
        Uri uri=data.getData();
        if(request==OPEN_DOC)upload(uri);
        else if(request==SAVE_DOC)download(uri,selectedDownloadId);
    }
    private void upload(Uri uri){
        try(InputStream input=getContentResolver().openInputStream(uri);ByteArrayOutputStream bytes=new ByteArrayOutputStream()){
            if(input==null)throw new Exception("Không mở được file");
            byte[] buffer=new byte[16384];int total=0,n;
            while((n=input.read(buffer))!=-1){total+=n;if(total>10*1024*1024)throw new Exception("File quá 10 MB");bytes.write(buffer,0,n);}
            String filename="document";
            try(Cursor c=getContentResolver().query(uri,null,null,null,null)){
                if(c!=null&&c.moveToFirst()) {int index=c.getColumnIndex(OpenableColumns.DISPLAY_NAME);if(index>=0)filename=c.getString(index);}
            }
            String mime=getContentResolver().getType(uri);
            if(mime==null)mime="application/octet-stream";
            RequestBody content=RequestBody.create(MediaType.parse(mime),bytes.toByteArray());
            MultipartBody.Part part=MultipartBody.Part.createFormData("file",filename,content);
            RequestBody id=RequestBody.create(MediaType.parse("text/plain"),String.valueOf(employeeId));
            RequestBody docType=RequestBody.create(MediaType.parse("text/plain"),TYPES[type.getSelectedItemPosition()]);
            HrUi.call(this,HrUi.api(this).uploadDocument(id,docType,part),result->load());
        }catch(Exception e){android.widget.Toast.makeText(this,e.getMessage(),android.widget.Toast.LENGTH_LONG).show();}
    }
    private void download(Uri uri,int id){HrUi.api(this).downloadDocument(employeeId,id).enqueue(new Callback<ResponseBody>(){
        @Override public void onResponse(Call<ResponseBody> c,Response<ResponseBody> r){
            if(!r.isSuccessful()||r.body()==null){android.widget.Toast.makeText(HrDocumentsActivity.this,"Không tải được tài liệu",android.widget.Toast.LENGTH_LONG).show();return;}
            ResponseBody body=r.body();new Thread(()->{
                try(InputStream in=body.byteStream();OutputStream out=getContentResolver().openOutputStream(uri)){
                    if(out==null)throw new Exception("Không mở được nơi lưu");byte[] buffer=new byte[16384];int n;
                    while((n=in.read(buffer))!=-1)out.write(buffer,0,n);
                    runOnUiThread(()->android.widget.Toast.makeText(HrDocumentsActivity.this,"Đã lưu tài liệu",android.widget.Toast.LENGTH_SHORT).show());
                }catch(Exception e){runOnUiThread(()->android.widget.Toast.makeText(HrDocumentsActivity.this,"Lỗi lưu tài liệu",android.widget.Toast.LENGTH_LONG).show());}
                finally{body.close();}
            }).start();
        }
        @Override public void onFailure(Call<ResponseBody> c,Throwable t){android.widget.Toast.makeText(HrDocumentsActivity.this,"Lỗi tải tài liệu",android.widget.Toast.LENGTH_LONG).show();}
    });}
}
