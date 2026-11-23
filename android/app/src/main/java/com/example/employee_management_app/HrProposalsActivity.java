package com.example.employee_management_app;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Cards and contextual buttons; all authorization still happens on the API. */
public class HrProposalsActivity extends AppCompatActivity {
    private final String[] types={"base_salary_change","promotion","transfer","termination"};
    private final String[] names={"Điều chỉnh lương","Thăng chức","Chuyển phòng","Nghỉ việc"};
    private Spinner type;
    private EditText employeeId,proposalValue,positionId,reason,effectiveDate;
    private LinearLayout results,editPanel;
    private TextView summary;

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        if(!HrUi.hr(this)){finish();return;}
        LinearLayout root=HrUi.page(this,"Đề xuất và phê duyệt");
        HrUi.label(root,"Hồ sơ nhân viên thường: Trưởng phòng HR duyệt. Tuyển trưởng phòng trở lên và các thay đổi nhân sự khác: chờ BOD.");

        HrUi.navCard(root,"Tạo hồ sơ ứng viên","Nhập thông tin ứng viên và lưu nháp",()->startActivity(new Intent(this,CreateAccountActivity.class)));
        if(HrUi.managerHr(this)){
            HrUi.secondary(root,"Tạo đề xuất thay đổi nhân sự",()->editPanel.setVisibility(
                    editPanel.getVisibility()==View.VISIBLE?View.GONE:View.VISIBLE));
            editPanel=HrUi.card(root);editPanel.setVisibility(View.GONE);
            HrUi.title(editPanel,"Đề xuất gửi BOD");
            HrUi.label(editPanel,"Dùng khi tăng lương, thăng chức, chuyển phòng hoặc nghỉ việc. Không tự cập nhật hồ sơ khi chưa được phê duyệt.");
            type=new Spinner(this);
            type.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,names));
            editPanel.addView(type,new LinearLayout.LayoutParams(-1,HrUi.dp(this,48)));
            employeeId=HrUi.input(editPanel,"ID nhân viên cần đề xuất");
            int prefilled=getIntent().getIntExtra("EMPLOYEE_ID",0);
            if(prefilled>0)employeeId.setText(String.valueOf(prefilled));
            proposalValue=HrUi.input(editPanel,"Lương mới / ID chức vụ / ID phòng ban");
            positionId=HrUi.input(editPanel,"ID chức vụ khi chuyển phòng (nếu có)");
            effectiveDate=HrUi.input(editPanel,"Ngày hiệu lực (YYYY-MM-DD, nếu có)");
            reason=HrUi.input(editPanel,"Lý do đề xuất (bắt buộc)");
            HrUi.button(editPanel,"Gửi đề xuất BOD",this::create);
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
            results.removeAllViews();
            int review=0,approve=0,bod=0;
            for(Map<String,Object> p:rows){
                String st=HrUi.s(p.get("status"));
                if("hr_pending".equals(st))review++;
                else if("approved".equals(st))approve++;
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
        HrUi.status(box,statusName(st),"activated".equals(st)||"approved".equals(st));
        if(!name.isEmpty())HrUi.label(box,"Ứng viên: "+name);
        if(p.get("employee_id")!=null)HrUi.label(box,"Nhân viên: #"+HrUi.id(p.get("employee_id")));
        String hiring=HrUi.s(person.get("hiring_level"));
        if(!hiring.isEmpty())HrUi.label(box,"Cấp tuyển: "+("ordinary".equals(hiring)?"Nhân viên thông thường":hiring));
        String reason=HrUi.s(p.get("reason"));
        if(!reason.isEmpty())HrUi.label(box,"Lý do: "+reason);
        String note=HrUi.s(p.get("hr_review_note"));
        if(!note.isEmpty())HrUi.label(box,"Phản hồi HR: "+note);
        if("recruitment".equals(typeKey)&&id>0){
            if("draft".equals(st))HrUi.button(box,"Gửi Trưởng phòng HR duyệt",()->submit(id));
            if(HrUi.managerHr(this)&&"hr_pending".equals(st)){
                HrUi.button(box,"Duyệt hồ sơ",()->review(id,"approve"));
                HrUi.danger(box,"Trả lại để bổ sung",()->review(id,"return"));
            }
            if(HrUi.managerHr(this)&&"approved".equals(st)){
                HrUi.button(box,"Tạo tài khoản nhân viên",()->activate(id));
            }
        }
        String details=HrUi.s(p.get("proposed_data"));
        if(!details.isEmpty()&&!"{}".equals(details)){
            TextView raw=HrUi.label(box,details);raw.setTextIsSelectable(true);raw.setVisibility(View.GONE);
            HrUi.secondary(box,"Xem / ẩn chi tiết hồ sơ",()->raw.setVisibility(raw.getVisibility()==View.VISIBLE?View.GONE:View.VISIBLE));
        }
    }
    private void submit(int id){
        Map<String,Object> b=new HashMap<>();b.put("id",id);
        HrUi.call(this,HrUi.api(this).submitProposal(b),data->load());
    }
    private void review(int id,String decision){
        EditText note=new EditText(this);note.setHint("Nhận xét"+("return".equals(decision)?" (bắt buộc)":" (tùy chọn)"));
        LinearLayout wrap=new LinearLayout(this);wrap.setPadding(HrUi.dp(this,20),0,HrUi.dp(this,20),0);
        wrap.addView(note,new LinearLayout.LayoutParams(-1,-2));
        new AlertDialog.Builder(this).setTitle("return".equals(decision)?"Trả hồ sơ #"+id:"Duyệt hồ sơ #"+id)
            .setMessage("return".equals(decision)?"Hồ sơ chưa đạt sẽ trở về nháp để Staff HR chỉnh sửa.":"Tuyển nhân viên thường được HR duyệt; chức vụ trưởng phòng trở lên tiếp tục chờ BOD.")
            .setView(wrap).setNegativeButton("Hủy",null)
            .setPositiveButton("Xác nhận",(dialog,which)->{
                String value=note.getText().toString().trim();
                if("return".equals(decision)&&value.isEmpty()){
                    android.widget.Toast.makeText(this,"Cần nhập lý do trả hồ sơ",android.widget.Toast.LENGTH_LONG).show();return;
                }
                Map<String,Object> b=new HashMap<>();b.put("id",id);b.put("decision",decision);b.put("review_note",value);
                HrUi.call(this,HrUi.api(this).reviewRecruitmentInternal(b),data->load());
            }).show();
    }
    private void activate(int id){
        new AlertDialog.Builder(this).setTitle("Tạo tài khoản cho hồ sơ #"+id)
            .setMessage("Chỉ được tạo sau khi đúng cấp thẩm quyền phê duyệt. Mật khẩu tạm chỉ hiển thị một lần.")
            .setNegativeButton("Hủy",null).setPositiveButton("Tạo tài khoản",(dialog,which)->{
                Map<String,Object> b=new HashMap<>();b.put("id",id);
                HrUi.call(this,HrUi.api(this).activateRecruitment(b),this::showCredentials);
            }).show();
    }
    private void showCredentials(Object obj){
        Map<String,Object> data=HrUi.map(obj);
        TextView msg=new TextView(this);msg.setPadding(30,24,30,24);msg.setTextSize(16);msg.setTextIsSelectable(true);
        msg.setText("Tên đăng nhập: "+HrUi.s(data.get("username"))+
            "\nMật khẩu tạm: "+HrUi.s(data.get("temporary_password"))+
            "\n\nChỉ hiển thị một lần; bàn giao an toàn và yêu cầu nhân viên đổi mật khẩu.");
        new AlertDialog.Builder(this).setTitle("Đã tạo tài khoản #"+HrUi.id(data.get("employee_id")))
            .setView(msg).setPositiveButton("Đã ghi lại",(dialog,which)->load()).show();
    }
    private int num(String s){try{return Integer.parseInt(s.trim());}catch(Exception e){return 0;}}
    private void create(){
        int idx=type.getSelectedItemPosition();
        Map<String,Object> b=new HashMap<>();b.put("proposal_type",types[idx]);
        b.put("employee_id",num(employeeId.getText().toString()));
        b.put("reason",reason.getText().toString().trim());
        String date=effectiveDate.getText().toString().trim();if(!date.isEmpty())b.put("effective_date",date);
        int value=num(proposalValue.getText().toString());
        if(idx==0)b.put("base_salary",proposalValue.getText().toString().trim());
        else if(idx==1)b.put("position_id",value);
        else if(idx==2){b.put("department_id",value);
            String p=positionId.getText().toString().trim();if(!p.isEmpty())b.put("position_id",num(p));}
        HrUi.call(this,HrUi.api(this).createProposal(b),data->{
            android.widget.Toast.makeText(this,"Đã lưu đề xuất chờ BOD",android.widget.Toast.LENGTH_LONG).show();
            editPanel.setVisibility(View.GONE);load();
        });
    }
}
