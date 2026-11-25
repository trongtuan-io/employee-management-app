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
    private static final String[] TYPES={"portrait","cccd_front","cccd_back","insurance","degree","certificate","contract","other"};
    private static final String[] CANDIDATE_TYPES={"portrait","cccd_front","cccd_back","degree","certificate","other"};
    private int employeeId, proposalId, selectedDownloadId;
    private boolean canReview;
    private Spinner type;
    private TextView summary;
    private LinearLayout checklistBox;
    private LinearLayout list;
    private final List<Map<String,Object>> rows=new ArrayList<>();

    static Intent candidateIntent(Activity activity,int proposalId,boolean draft){
        return new Intent(activity,HrDocumentsActivity.class)
                .putExtra("PROPOSAL_ID",proposalId).putExtra("CANDIDATE_DRAFT",draft);
    }

    @Override public void onCreate(Bundle state){super.onCreate(state);
        TokenManager token=TokenManager.getInstance(this);
        proposalId=getIntent().getIntExtra("PROPOSAL_ID",0);
        employeeId=getIntent().getIntExtra("EMPLOYEE_ID",token.getUserId());
        if(proposalId==0 && employeeId!=token.getUserId() && !HrUi.hr(this)){finish();return;}
        canReview=proposalId==0 && HrUi.hr(this) && employeeId!=token.getUserId();
        if(state!=null)selectedDownloadId=state.getInt("download_id");
        LinearLayout root=HrUi.page(this,proposalId>0?"Giấy tờ ứng viên #"+proposalId:"Giấy tờ nhân sự - NV #"+employeeId);
        if(proposalId==0){
            summary=HrUi.label(root,"Đang tải tiến độ…");
            checklistBox=new LinearLayout(this);
            checklistBox.setOrientation(LinearLayout.VERTICAL);
            root.addView(checklistBox);
        }
        if(proposalId==0 || (HrUi.hr(this) && getIntent().getBooleanExtra("CANDIDATE_DRAFT",false))){
            String[] groups=proposalId>0?CANDIDATE_TYPES:TYPES;
            List<String> labels=new ArrayList<>();
            for(String group:groups)labels.add(typeLabel(group));
            HrUi.label(root,"Loại giấy tờ cần tải lên");
            type=new Spinner(this);
            type.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,labels));
            root.addView(type);
            HrUi.button(root,"Tải lên PDF / ảnh",()->{
                Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("*/*");i.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"application/pdf","image/jpeg","image/png","image/webp"});
                startActivityForResult(i,OPEN_DOC);
            });
        }
        HrUi.secondary(root,"Làm mới",this::load);
        HrUi.section(root,proposalId>0?"Tài liệu đã đính kèm":"Lịch sử giấy tờ");
        list=new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        root.addView(list);
        load();
    }

    @Override protected void onSaveInstanceState(Bundle out){
        out.putInt("download_id",selectedDownloadId);
        super.onSaveInstanceState(out);
    }

    private void showActions(Map<String,Object> d){
            int documentId=HrUi.n(d.get("id"));
            String[] options=canReview && HrUi.n(d.get("is_latest"))==1
                    ?new String[]{"Tải xuống","Hợp lệ","Yêu cầu bổ sung"}:new String[]{"Tải xuống"};
            new AlertDialog.Builder(this).setTitle(typeLabel(HrUi.s(d.get("document_type"))))
              .setItems(options,(dialog,which)->{
                if(which==0)chooseDownload(documentId);
                else if(which==1)review(documentId,"valid","");
                else {EditText note=new EditText(this);note.setHint("Lý do cần bổ sung");
                    new AlertDialog.Builder(this).setTitle("Yêu cầu bổ sung").setView(note)
                      .setPositiveButton("Gửi",(d2,w)->review(documentId,"needs_update",note.getText().toString()))
                      .setNegativeButton("Hủy",null).show();}
            }).show();
    }

    static String progressText(Map<String,Object> data){
        String deadline=HrUi.s(data.get("due_at"));
        return "Hồ sơ đã duyệt "+HrUi.n(data.get("valid"))+"/"+HrUi.n(data.get("required_count"))
                +" · Chưa nộp "+HrUi.n(data.get("missing"))+" · Chờ duyệt "+HrUi.n(data.get("pending"))
                +" · Cần bổ sung "+HrUi.n(data.get("needs_update"))
                +(deadline.isEmpty()?"":"\nHạn nộp "+deadline);
    }

    private static String statusLabel(String status){
        switch(status){
            case "pending":return "Chờ duyệt";
            case "valid":return "Hợp lệ";
            case "needs_update":return "Yêu cầu bổ sung";
            default:return "Chưa nộp";
        }
    }

    private static String typeLabel(String value){
        switch(value){
            case "portrait":return "Ảnh chân dung";
            case "cccd_front":return "CCCD mặt trước";
            case "cccd_back":return "CCCD mặt sau";
            case "insurance":return "BHXH";
            case "degree":return "Bằng cấp";
            case "certificate":return "Chứng chỉ";
            case "contract":return "Hợp đồng";
            default:return "Giấy tờ khác";
        }
    }

    private void load(){
        HrApi api=HrUi.api(this);
        if(proposalId==0)HrUi.call(this,api.documentSummary(employeeId),data->{
            Map<String,Object> details=HrUi.map(data);
            summary.setText(progressText(details));
            checklistBox.removeAllViews();
            for(Map<String,Object> item:HrUi.rows(details.get("checklist"))){
                String status=HrUi.s(item.get("status"));
                LinearLayout card=HrUi.card(checklistBox);
                HrUi.title(card,typeLabel(HrUi.s(item.get("document_type")))
                        +(Boolean.TRUE.equals(item.get("required"))?" (bắt buộc)":""));
                HrUi.status(card,statusLabel(status),"valid".equals(status));
            }
        });
        HrUi.call(this,proposalId>0?api.candidateDocuments(proposalId):api.documents(employeeId),data->{
            rows.clear();
            rows.addAll(HrUi.rows(data));
            list.removeAllViews();
            if(rows.isEmpty())HrUi.label(list,"Chưa có giấy tờ.");
            for(Map<String,Object> row:rows){
                LinearLayout card=HrUi.card(list);
                String label=typeLabel(HrUi.s(row.get("document_type")));
                String state=HrUi.s(row.get("status"));
                HrUi.title(card,label+(state.isEmpty()?"":" · "+statusLabel(state)));
                String filename=HrUi.s(row.get("original_filename"));
                if(!filename.isEmpty())HrUi.label(card,filename+" · "+HrUi.s(row.get("created_at")));
                String note=HrUi.s(row.get("review_note"));
                if(!note.isEmpty())HrUi.label(card,note);
                HrUi.secondary(card,canReview && HrUi.n(row.get("is_latest"))==1?"Tải xuống hoặc kiểm duyệt":"Tải xuống",
                        ()->{if(canReview && HrUi.n(row.get("is_latest"))==1)showActions(row);
                            else chooseDownload(HrUi.n(row.get("id")));});
            }
        });
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
            if(extension.isEmpty()){
                switch(HrUi.s(row.get("mime_type"))){
                    case "application/pdf":extension=".pdf";break;
                    case "image/jpeg":extension=".jpg";break;
                    case "image/png":extension=".png";break;
                    case "image/webp":extension=".webp";break;
                }
            }
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
            String[] groups=proposalId>0?CANDIDATE_TYPES:TYPES;
            int selected=type.getSelectedItemPosition();
            if(selected<0)return;
            RequestBody docType=RequestBody.create(MediaType.parse("text/plain"),groups[selected]);
            HrApi api=HrUi.api(this);
            if(proposalId>0)HrUi.call(this,api.uploadCandidateDocument(proposalId,docType,part),result->load());
            else{
                RequestBody id=RequestBody.create(MediaType.parse("text/plain"),String.valueOf(employeeId));
                HrUi.call(this,api.uploadDocument(id,docType,part),result->load());
            }
        }catch(Exception e){android.widget.Toast.makeText(this,e.getMessage(),android.widget.Toast.LENGTH_LONG).show();}
    }
    private void download(Uri uri,int id){
        HrApi api=HrUi.api(this);
        (proposalId>0?api.downloadCandidateDocument(proposalId,id):api.downloadDocument(employeeId,id)).enqueue(new Callback<ResponseBody>(){
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
