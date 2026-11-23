package com.example.employee_management_app;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import java.util.List;
import java.util.Map;
import java.util.Locale;

/** Read-only HR activity history; proposal details are never editable here. */
public class HrHistoryActivity extends AppCompatActivity {
    private LinearLayout entries;
    private TextView summary;
    private int employeeId;

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        if(!HrUi.hr(this)){finish();return;}
        employeeId=getIntent().getIntExtra("EMPLOYEE_ID",0);
        LinearLayout root=HrUi.page(this,employeeId>0?"Lịch sử thay đổi hồ sơ":"Lịch sử hoạt động nhân sự");
        HrUi.label(root,employeeId>0?"Chỉ hiển thị sự kiện của hồ sơ đang xem.":
                "Theo dõi các thao tác nhân sự. Nhấn Xem chi tiết để đọc nội dung; không thể sửa lịch sử.");
        summary=HrUi.label(root,"Đang tải lịch sử…");
        HrUi.secondary(root,"Tải lại lịch sử",this::load);
        entries=new LinearLayout(this);entries.setOrientation(LinearLayout.VERTICAL);root.addView(entries);
        load();
    }
    private static String actor(Map<String,Object> row){
        return person(HrUi.s(row.get("actor_name")),HrUi.s(row.get("actor_username")),
                HrUi.id(row.get("actor_id")));
    }
    private static String person(String name,String username,String id){
        name=name.trim();username=username.trim();
        if(!name.isEmpty()&&!username.isEmpty())return name+" ("+username+")";
        if(!name.isEmpty())return name;
        if(!username.isEmpty())return username;
        return id.isEmpty()?"Không rõ":"#"+id;
    }
    private static String proposalName(String value){
        switch(value){
            case "recruitment":return "Tuyển dụng";
            case "base_salary_change":return "Điều chỉnh lương";
            case "promotion":return "Thăng chức";
            case "transfer":return "Chuyển phòng";
            case "termination":return "Nghỉ việc";
            default:return value;
        }
    }
    private static String statusName(String value){
        switch(value){
            case "draft":return "Bản nháp";
            case "hr_pending":return "Chờ Trưởng phòng HR";
            case "pending":return "Chờ BOD duyệt";
            case "approved":return "Đã duyệt";
            case "activated":return "Đã tạo tài khoản";
            case "rejected":return "Đã từ chối";
            default:return value;
        }
    }
    private static String actionName(String action){
        if(action.startsWith("proposal.created."))return "Tạo đề xuất " + proposalName(action.substring("proposal.created.".length())).toLowerCase(new Locale("vi"));
        switch(action){
            case "proposal.submitted":return "Gửi đề xuất";
            case "proposal.import.refreshed":return "Cập nhật đề xuất từ tệp nhập";
            case "recruitment.hr.approve":return "Trưởng phòng HR duyệt hồ sơ";
            case "recruitment.hr.return":return "Trả hồ sơ tuyển dụng bổ sung";
            case "recruitment.draft.edited":return "Chỉnh sửa hồ sơ ứng viên";
            case "recruitment.account.activated":return "Tạo tài khoản nhân viên";
            case "employee.profile.updated":return "Cập nhật hồ sơ nhân viên";
            case "employee.import.updated":return "Cập nhật hồ sơ từ tệp nhập";
            case "position.created":return "Tạo chức vụ";
            case "position.updated":return "Cập nhật chức vụ";
            case "position.deactivated":return "Ngừng sử dụng chức vụ";
            case "document.uploaded":return "Tải lên giấy tờ";
            case "document.reviewed":return "Kiểm duyệt giấy tờ";
            default:return action.replace('.', ' ');
        }
    }
    private static String fieldName(String k){
        switch(k){
            case "full_name":return "Họ tên";
            case "username":return "Tên đăng nhập";
            case "email":return "Email";
            case "phone":return "Điện thoại";
            case "department_id":return "Mã phòng ban";
            case "position_id":return "Mã chức vụ";
            case "employee_id":return "Mã nhân viên";
            case "proposal_id":return "Mã đề xuất";
            case "base_salary":return "Lương cơ bản";
            case "role_id":return "Mã vai trò";
            case "employment_status":return "Trạng thái làm việc";
            case "hire_date":return "Ngày vào làm";
            case "status":return "Trạng thái";
            case "hiring_level":return "Cấp tuyển";
            case "disable_login":return "Khóa đăng nhập";
            default:return k.replace('_',' ');
        }
    }
    private static String displayValue(Object value){
        if(value==null)return "(trống)";
        String v=HrUi.s(value);
        if("ordinary".equals(v))return "Nhân viên thông thường";
        if("senior".equals(v))return "Nhân sự cấp cao";
        if("working".equals(v))return "Đang làm việc";
        if("probation".equals(v))return "Thử việc";
        if("left".equals(v))return "Đã nghỉ việc";
        if(value instanceof Number)return HrUi.id(value);
        return v;
    }
    private static String dataText(Object data){
        if(data==null)return "";
        if(!(data instanceof Map))return HrUi.s(data);
        StringBuilder b=new StringBuilder();
        for(Map.Entry<String,Object> e:HrUi.map(data).entrySet()){
            String key=e.getKey();
            if(key.toLowerCase(Locale.ROOT).matches(".*(password|token|secret|hash).*"))continue;
            if(b.length()>0)b.append('\n');
            b.append("• ").append(fieldName(key)).append(": ").append(displayValue(e.getValue()));
        }
        return b.toString();
    }
    private static String shortText(String text){
        text=text.trim();return text.length()>110?text.substring(0,110)+"…":text;
    }
    private static TextView detail(LinearLayout root,String name,String value){
        TextView t=HrUi.label(root,name+value);t.setTextIsSelectable(true);return t;
    }
    private void load(){
        summary.setText("Đang tải lịch sử…");
        HrUi.call(this,HrUi.api(this).history(employeeId>0?employeeId:null),data->{
            entries.removeAllViews();List<Map<String,Object>> history=HrUi.rows(data);
            summary.setText(history.size()+" sự kiện"+(employeeId>0?" của hồ sơ đang xem":""));
            if(history.isEmpty()){
                LinearLayout empty=HrUi.card(entries);
                HrUi.title(empty,"Chưa có lịch sử");
                HrUi.label(empty,"Chưa ghi nhận thay đổi thuộc phạm vi đang xem.");return;
            }
            for(Map<String,Object> row:history){
                LinearLayout card=HrUi.card(entries);
                String target=HrUi.s(row.get("employee_name")).trim();
                boolean candidate=false;
                if(target.isEmpty()){target=HrUi.s(row.get("candidate_name")).trim();candidate=true;}
                String action=HrUi.s(row.get("action"));
                HrUi.title(card,actionName(action)+(target.isEmpty()?"":" – "+target));
                HrUi.label(card,"Sự kiện #"+HrUi.id(row.get("id"))+"  ·  "+HrUi.s(row.get("created_at")));
                HrUi.label(card,"Người thực hiện: "+actor(row));
                if(!target.isEmpty()){
                    String empId=HrUi.id(row.get("employee_id"));
                    HrUi.label(card,(candidate?"Ứng viên: ":"Nhân viên: ")+target+
                            (empId.isEmpty()?"":" (mã "+empId+")"));
                }
                String pId=HrUi.id(row.get("proposal_id"));
                if(!pId.isEmpty()){
                    HrUi.label(card,"Đề xuất #"+pId+" · "+proposalName(HrUi.s(row.get("proposal_type"))));
                    String status=HrUi.s(row.get("proposal_status"));
                    if(!status.isEmpty())HrUi.label(card,"Trạng thái hiện tại: "+statusName(status));
                    String reason=HrUi.s(row.get("proposal_reason"));
                    if(!reason.isEmpty())HrUi.label(card,"Nội dung: "+shortText(reason));
                }
                LinearLayout details=new LinearLayout(this);details.setOrientation(LinearLayout.VERTICAL);
                details.setVisibility(View.GONE);card.addView(details);
                HrUi.section(details,"Chi tiết sự kiện");
                detail(details,"Thao tác: ",actionName(action));
                detail(details,"Người thực hiện: ",actor(row));
                detail(details,"Thời gian: ",HrUi.s(row.get("created_at")));
                if(!pId.isEmpty()){
                    HrUi.section(details,"Chi tiết đề xuất #"+pId);
                    detail(details,"Loại: ",proposalName(HrUi.s(row.get("proposal_type"))));
                    detail(details,"Trạng thái hiện tại: ",statusName(HrUi.s(row.get("proposal_status"))));
                    String creator=person(HrUi.s(row.get("proposal_creator_name")),
                            HrUi.s(row.get("proposal_creator_username")),"");
                    detail(details,"Người lập đề xuất: ",creator);
                    String date=HrUi.s(row.get("proposal_created_at"));
                    if(!date.isEmpty())detail(details,"Ngày lập: ",date);
                    String effective=HrUi.s(row.get("proposal_effective_date"));
                    if(!effective.isEmpty())detail(details,"Ngày hiệu lực dự kiến: ",effective);
                    String reason=HrUi.s(row.get("proposal_reason"));
                    if(!reason.isEmpty())detail(details,"Lý do / nội dung: ",reason);
                    String beforeProposal=dataText(row.get("proposal_before_data"));
                    if(!beforeProposal.isEmpty())detail(details,"Thông tin trước đề xuất:\n",beforeProposal);
                    String afterProposal=dataText(row.get("proposal_proposed_data"));
                    if(!afterProposal.isEmpty())detail(details,"Nội dung thay đổi được đề xuất:\n",afterProposal);
                }
                String before=dataText(row.get("before_data"));
                String after=dataText(row.get("after_data"));
                if(!before.isEmpty())detail(details,"Trước sự kiện:\n",before);
                if(!after.isEmpty())detail(details,"Sau sự kiện:\n",after);
                final Button toggle=HrUi.secondary(card,"Xem chi tiết",()->{});
                toggle.setOnClickListener(v->{
                    boolean show=details.getVisibility()!=View.VISIBLE;
                    details.setVisibility(show?View.VISIBLE:View.GONE);
                    toggle.setText(show?"Ẩn chi tiết":"Xem chi tiết");
                });
            }
        });
    }
}
