package com.example.employee_management_app;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Cards and contextual buttons; all authorization still happens on the API. */
public class HrProposalsActivity extends AppCompatActivity {
    static final String TRACK_EMPLOYEE_ID = "TRACK_EMPLOYEE_ID";
    private int trackedEmployeeId;
    private LinearLayout results;
    private TextView summary;

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        if(!HrUi.hr(this)){finish();return;}
        if(HrUi.managerHr(this))trackedEmployeeId=getIntent().getIntExtra(TRACK_EMPLOYEE_ID,0);
        LinearLayout root=HrUi.page(this,trackedEmployeeId>0?"Theo dõi đề xuất nhân sự":"Đề xuất và phê duyệt");
        if(trackedEmployeeId<=0){
            HrUi.label(root,"Hồ sơ cấp cao qua Trưởng phòng HR và BOD. Tạo tài khoản Staff bằng Import Excel / CSV.");
            HrUi.navCard(root,"Tạo hồ sơ ứng viên cấp cao","Lưu nháp và đính kèm giấy tờ",()->startActivity(new Intent(this,CreateAccountActivity.class)));
        }

        HrUi.section(root,"Hồ sơ và trạng thái");
        summary=HrUi.label(root,"Đang tải danh sách…");
        HrUi.secondary(root,"Tải lại danh sách",this::load);
        results=new LinearLayout(this);results.setOrientation(LinearLayout.VERTICAL);root.addView(results);
    }
    @Override protected void onResume(){super.onResume();if(results!=null)load();}

    private static String statusName(String status){
        switch(status){
            case "draft":return "Bản nháp";
            case "hr_pending":return "Chờ Trưởng phòng HR";
            case "pending":return "Chờ BOD";
            case "approved":return "Đã duyệt · Chờ tạo tài khoản";
            case "activated":return "Đã tạo tài khoản";
            case "rejected":return "Đã từ chối";
            default:return status;
        }
    }
    private static String typeName(String type){
        switch(type){
            case "recruitment":return "Tuyển dụng";
            case "base_salary_change":return "Điều chỉnh lương";
            case "promotion":return "Thăng chức";
            case "transfer":return "Chuyển phòng";
            case "termination":return "Nghỉ việc";
            default:return type;
        }
    }
    private void load(){
        summary.setText("Đang tải danh sách…");
        HrUi.call(this,HrUi.api(this).proposals(),data->{
            List<Map<String,Object>> rows=HrUi.rows(data);
            if(trackedEmployeeId>0)rows.removeIf(p->HrUi.n(p.get("employee_id"))!=trackedEmployeeId
                    || "recruitment".equals(HrUi.s(p.get("proposal_type"))));
            results.removeAllViews();
            int review=0,approve=0,bod=0;
            for(Map<String,Object> p:rows){
                String st=HrUi.s(p.get("status"));
                if("hr_pending".equals(st))review++;
                else if("approved".equals(st)&&"recruitment".equals(HrUi.s(p.get("proposal_type"))))approve++;
                else if("pending".equals(st))bod++;
                drawProposal(p);
            }
            summary.setText(rows.size()+" đề xuất  ·  "+review+" chờ HR  ·  "+approve+" chờ tạo tài khoản  ·  "+bod+" chờ BOD");
            if(rows.isEmpty())HrUi.label(results,"Chưa có đề xuất nào.");
        });
    }
    private void drawProposal(Map<String,Object> p){
        final int id=HrUi.n(p.get("id"));
        String typeKey=HrUi.s(p.get("proposal_type"));
        String st=HrUi.s(p.get("status"));
        Map<String,Object> person=HrUi.map(p.get("proposed_data"));
        String name=HrUi.s(person.get("full_name"));
        LinearLayout box=HrUi.card(results);
        HrUi.title(box,"#"+HrUi.id(p.get("id"))+"  ·  "+typeName(typeKey));
        boolean personnelChange = !"recruitment".equals(typeKey) && "approved".equals(st);
        boolean applied = p.get("applied_at") != null;
        String status = personnelChange ? (applied ? "Đã áp dụng" : "Đã duyệt · Chờ áp dụng") : statusName(st);
        HrUi.status(box,status,"activated".equals(st)||"approved".equals(st));
        String effective = HrUi.s(p.get("effective_date"));
        if (!effective.isEmpty()) HrUi.label(box,"Ngày hiệu lực: "+effective);
        if (personnelChange && !applied && HrUi.managerHr(this)) {
            HrUi.button(box,"Áp dụng khi đến hạn",()->{
                Map<String,Object> body=new HashMap<>();
                body.put("id",id);
                HrUi.call(this,HrUi.api(this).applyProposal(body),data->load());
            });
        }
        if(!name.isEmpty())HrUi.label(box,"Ứng viên: "+name);
        if(p.get("employee_id")!=null)HrUi.label(box,"Nhân viên: #"+HrUi.id(p.get("employee_id")));
        String hiring=HrUi.s(person.get("hiring_level"));
        if(!hiring.isEmpty())HrUi.label(box,"Cấp tuyển: "+("ordinary".equals(hiring)?"Nhân viên thông thường":hiring));
        String reason=HrUi.s(p.get("reason"));
        if(!reason.isEmpty())HrUi.label(box,"Lý do: "+reason);
        String note=HrUi.s(p.get("hr_review_note"));
        if(!note.isEmpty())HrUi.label(box,"Phản hồi HR: "+note);
        String bodNote=HrUi.s(p.get("review_note"));
        if(!bodNote.isEmpty())HrUi.label(box,"Phản hồi BOD: "+bodNote);
        if("recruitment".equals(typeKey)&&id>0){
            int version=HrUi.n(p.get("version"));
            boolean draft="draft".equals(st);
            if(draft){
                HrUi.secondary(box,"Chỉnh sửa bản nháp",()->editDraft(p,person));
                HrUi.button(box,"Gửi Trưởng phòng HR duyệt",()->submit(id,version));
            }
            HrUi.secondary(box,"Giấy tờ ứng viên",()->startActivity(
                    HrDocumentsActivity.candidateIntent(this,id,draft)));
            if(HrUi.managerHr(this)&&"hr_pending".equals(st)){
                HrUi.button(box,"Duyệt hồ sơ",()->review(id,version,"approve"));
                HrUi.danger(box,"Trả lại để bổ sung",()->review(id,version,"return"));
            }
            if(HrUi.managerHr(this)&&"approved".equals(st)){
                HrUi.button(box,"Tạo tài khoản nhân viên",()->activate(id,version));
            }
        }
        String details=HrUi.s(p.get("proposed_data"));
        if(!details.isEmpty()&&!"{}".equals(details)){
            TextView raw=HrUi.label(box,details);raw.setTextIsSelectable(true);raw.setVisibility(View.GONE);
            HrUi.secondary(box,"Xem / ẩn chi tiết hồ sơ",()->raw.setVisibility(raw.getVisibility()==View.VISIBLE?View.GONE:View.VISIBLE));
        }
    }
    private void editDraft(Map<String,Object> p, Map<String,Object> person){
        Intent intent=new Intent(this,CreateAccountActivity.class);
        intent.putExtra("DRAFT_ID",HrUi.n(p.get("id")));
        intent.putExtra("DRAFT_VERSION",HrUi.n(p.get("version")));
        intent.putExtra("WORKFLOW_VERSION",HrUi.n(p.get("workflow_version")));
        intent.putExtra("DRAFT_DEPARTMENT",HrUi.n(person.get("department_id")));
        intent.putExtra("DRAFT_POSITION",HrUi.n(person.get("position_id")));
        for(String field:new String[]{"username","full_name","email","phone","hire_date"})
            intent.putExtra(field,HrUi.s(person.get(field)));
        intent.putExtra("reason",HrUi.s(p.get("reason")));
        startActivity(intent);
    }
    private void submit(int id,int version){
        Map<String,Object> b=new HashMap<>();b.put("id",id);b.put("version",version);
        HrUi.call(this,HrUi.api(this).submitProposal(b),data->load());
    }
    private void review(int id,int version,String decision){
        EditText note=new EditText(this);note.setHint("Nhận xét"+("return".equals(decision)?" (bắt buộc)":" (tùy chọn)"));
        LinearLayout wrap=new LinearLayout(this);wrap.setPadding(HrUi.dp(this,20),0,HrUi.dp(this,20),0);
        wrap.addView(note,new LinearLayout.LayoutParams(-1,-2));
        new AlertDialog.Builder(this).setTitle("return".equals(decision)?"Trả hồ sơ #"+id:"Duyệt hồ sơ #"+id)
            .setMessage("return".equals(decision)?"Hồ sơ sẽ trở về nháp để chỉnh sửa.":"Hồ sơ cấp cao sẽ được gửi BOD để quyết định.")
            .setView(wrap).setNegativeButton("Hủy",null)
            .setPositiveButton("Xác nhận",(dialog,which)->{
                String value=note.getText().toString().trim();
                if("return".equals(decision)&&value.isEmpty()){
                    android.widget.Toast.makeText(this,"Cần nhập lý do trả hồ sơ",android.widget.Toast.LENGTH_LONG).show();return;
                }
                Map<String,Object> b=new HashMap<>();b.put("id",id);b.put("version",version);b.put("decision",decision);b.put("review_note",value);
                HrUi.call(this,HrUi.api(this).reviewRecruitmentInternal(b),data->load());
            }).show();
    }
    private void activate(int id,int version){
        new AlertDialog.Builder(this).setTitle("Tạo tài khoản cho hồ sơ #"+id)
            .setMessage("Chỉ được tạo sau khi đúng cấp thẩm quyền phê duyệt. Mật khẩu tạm chỉ hiển thị một lần.")
            .setNegativeButton("Hủy",null).setPositiveButton("Tạo tài khoản",(dialog,which)->{
                Map<String,Object> b=new HashMap<>();b.put("id",id);b.put("version",version);
                HrUi.call(this,HrUi.api(this).activateRecruitment(b),this::showCredentials);
            }).show();
    }
    private void showCredentials(Object obj){
        Map<String,Object> data=HrUi.map(obj);
        TextView msg=new TextView(this);msg.setPadding(30,24,30,24);msg.setTextSize(16);msg.setTextIsSelectable(true);
        msg.setText("Tên đăng nhập: "+HrUi.s(data.get("username"))+
            "\nMật khẩu tạm: "+HrUi.s(data.get("temporary_password"))+
            "\n\nChỉ hiển thị một lần; bàn giao an toàn và yêu cầu nhân viên đổi mật khẩu.");
        AlertDialog credentials=new AlertDialog.Builder(this)
            .setTitle("Đã tạo tài khoản #"+HrUi.id(data.get("employee_id")))
            .setView(msg).setPositiveButton("Đóng",null).create();
        if(credentials.getWindow()!=null)credentials.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        credentials.setOnDismissListener(dialog->load());
        credentials.show();
    }
}
