package com.example.employee_management_app;

import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Position catalog: select a department by name; no change to role or approval policy. */
public class HrPositionsActivity extends AppCompatActivity {
    private EditText id,title;
    private Spinner department;
    private TextView summary,editHeading;
    private LinearLayout rows,editor;
    private Button saveButton,deactivateButton;
    private final List<Integer> departmentIds=new ArrayList<>();
    private final Map<Integer,String> departmentNames=new HashMap<>();
    private boolean departmentsReady=false;

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        if(!HrUi.hr(this)){finish();return;}
        LinearLayout root=HrUi.page(this,"Danh mục chức vụ");
        HrUi.label(root,"Chức vụ và phòng ban tương ứng. Chọn một chức vụ để chỉnh sửa nếu bạn có quyền.");
        if(HrUi.managerHr(this)){
            HrUi.secondary(root,"Thêm chức vụ mới",()->openCreate());
            editor=HrUi.card(root);editor.setVisibility(View.GONE);
            editHeading=HrUi.title(editor,"Thêm chức vụ");
            id=HrUi.input(editor,"ID chức vụ");
            id.setVisibility(View.GONE); // Internal ID, always filled from the selected record.
            title=HrUi.input(editor,"Tên chức vụ");
            HrUi.label(editor,"Phòng ban");
            department=new Spinner(this);
            department.setMinimumHeight(HrUi.dp(this,48));
            editor.addView(department,new LinearLayout.LayoutParams(-1,HrUi.dp(this,48)));
            HrUi.label(editor,"Chọn “Dùng chung” nếu chức vụ không thuộc riêng phòng ban nào.");
            saveButton=HrUi.button(editor,"Lưu chức vụ",this::save);
            deactivateButton=HrUi.danger(editor,"Ngừng sử dụng chức vụ",()->new AlertDialog.Builder(this)
                    .setTitle("Ngừng sử dụng chức vụ?")
                    .setMessage("Chức vụ sẽ không được chọn cho nhân viên mới.")
                    .setNegativeButton("Hủy",null).setPositiveButton("Xác nhận",(d,w)->deactivate()).show());
            deactivateButton.setVisibility(View.GONE);
            HrUi.secondary(editor,"Đóng",()->editor.setVisibility(View.GONE));
        }
        HrUi.section(root,"Danh sách chức vụ");
        summary=HrUi.label(root,"Đang tải…");
        HrUi.secondary(root,"Tải lại danh sách",this::refresh);
        rows=new LinearLayout(this);rows.setOrientation(LinearLayout.VERTICAL);root.addView(rows);
        refresh();
    }
    private int number(String value){try{return Integer.parseInt(value.trim());}catch(Exception e){return 0;}}
    private void refresh(){loadDepartments();load();}
    private void loadDepartments(){
        if(department!=null&&departmentsReady){
            int chosen=department.getSelectedItemPosition();
            if(chosen>=0&&chosen<departmentIds.size())selectedDepartmentId=departmentIds.get(chosen);
        }
        departmentsReady=false;
        if(saveButton!=null)saveButton.setEnabled(false);
        HrUi.call(this,HrUi.api(this).departments(),data->{
            departmentIds.clear();departmentNames.clear();
            List<String> labels=new ArrayList<>();
            departmentIds.add(0);labels.add("Dùng chung (không thuộc phòng ban)");
            for(Map<String,Object> r:HrUi.rows(data)){
                int depId=HrUi.n(r.get("id"));
                String depName=HrUi.s(r.get("name")).trim();
                if(depId<=0||depName.isEmpty())continue;
                departmentIds.add(depId);departmentNames.put(depId,depName);
                labels.add(depName);
            }
            departmentsReady=true;
            if(department!=null){
                // Keep the selected department while refreshing the name list.
                int wanted=selectedDepartmentId;
                ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,labels);
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                department.setAdapter(adapter);
                selectDepartment(wanted);
                saveButton.setEnabled(true);
            }
            load(); // Refresh position cards with the department names.
        });
    }
    private int selectedDepartmentId=0;
    private void selectDepartment(int depId){
        selectedDepartmentId=depId;
        if(department==null||!departmentsReady)return;
        int index=departmentIds.indexOf(depId);
        if(index>=0)department.setSelection(index);
        else {
            Toast.makeText(this,"Phòng ban cũ không còn trong danh sách. Không tự thay đổi phòng ban.",Toast.LENGTH_LONG).show();
            saveButton.setEnabled(false);
        }
    }
    private void openCreate(){
        editor.setVisibility(View.VISIBLE);
        editHeading.setText("Thêm chức vụ");id.setText("");title.setText("");
        deactivateButton.setVisibility(View.GONE);
        selectDepartment(0);
        if(departmentsReady)saveButton.setEnabled(true);
        title.requestFocus();
    }
    private void openEdit(Map<String,Object> position){
        editor.setVisibility(View.VISIBLE);
        id.setText(HrUi.id(position.get("id")));
        title.setText(HrUi.s(position.get("title")));
        editHeading.setText("Chỉnh sửa chức vụ #"+HrUi.id(position.get("id")));
        deactivateButton.setVisibility(View.VISIBLE);
        selectDepartment(position.get("department_id")==null?0:HrUi.n(position.get("department_id")));
        if(departmentsReady&&departmentIds.contains(selectedDepartmentId))saveButton.setEnabled(true);
        title.requestFocus();
    }
    private void save(){
        if(!departmentsReady||department==null){
            Toast.makeText(this,"Chưa tải được danh sách phòng ban; hãy thử lại.",Toast.LENGTH_LONG).show();return;
        }
        String name=title.getText().toString().trim();
        if(name.isEmpty()){title.setError("Tên chức vụ không được để trống");return;}
        int selected=department.getSelectedItemPosition();
        if(selected<0||selected>=departmentIds.size()){
            Toast.makeText(this,"Vui lòng chọn phòng ban hợp lệ.",Toast.LENGTH_LONG).show();return;
        }
        boolean create=number(id.getText().toString())<=0;
        Map<String,Object> body=new HashMap<>();
        if(!create)body.put("id",number(id.getText().toString()));
        body.put("title",name);
        int depId=departmentIds.get(selected);
        body.put("department_id",depId==0?null:depId);
        saveButton.setEnabled(false);
        HrUi.call(this,create?HrUi.api(this).createPosition(body):HrUi.api(this).updatePosition(body),data->{
            Toast.makeText(this,create?"Đã thêm chức vụ":"Đã lưu thay đổi",Toast.LENGTH_SHORT).show();
            editor.setVisibility(View.GONE);
            id.setText("");title.setText("");selectDepartment(0);
            saveButton.setEnabled(true);
            load();
        });
        // Keep the form visible if the server reports an error; user may retry.
        saveButton.postDelayed(()->{if(editor.getVisibility()==View.VISIBLE)saveButton.setEnabled(departmentsReady);},2200);
    }
    private void deactivate(){
        int positionId=number(id.getText().toString());
        if(positionId<=0){Toast.makeText(this,"Hãy chọn chức vụ cần ngừng",Toast.LENGTH_LONG).show();return;}
        Map<String,Object> b=new HashMap<>();b.put("id",positionId);
        HrUi.call(this,HrUi.api(this).deactivatePosition(b),data->{
            Toast.makeText(this,"Đã ngừng sử dụng chức vụ",Toast.LENGTH_SHORT).show();
            editor.setVisibility(View.GONE);load();
        });
    }
    private void load(){
        summary.setText("Đang tải danh sách…");
        HrUi.call(this,HrUi.api(this).positions(),data->{
            List<Map<String,Object>> list=HrUi.rows(data);rows.removeAllViews();
            summary.setText(list.size()+" chức vụ");
            if(list.isEmpty()){HrUi.label(rows,"Chưa có chức vụ.");return;}
            for(Map<String,Object> r:list){
                LinearLayout card=HrUi.card(rows);
                HrUi.title(card,HrUi.s(r.get("title"))+"  ·  #"+HrUi.id(r.get("id")));
                boolean active=HrUi.n(r.get("is_active"))==1;
                HrUi.status(card,active?"Đang sử dụng":"Ngừng sử dụng",active);
                int depId=HrUi.n(r.get("department_id"));
                String departmentLabel=r.get("department_id")==null?"Dùng chung":departmentNames.get(depId);
                if(departmentLabel==null){departmentLabel="Chưa tải được tên phòng ban";}
                HrUi.label(card,"Phòng ban: "+departmentLabel+"  ·  Nhân viên: "+HrUi.id(r.get("assigned_count")));
                if(HrUi.managerHr(this))HrUi.secondary(card,"Chọn để chỉnh sửa",()->openEdit(r));
            }
        });
    }
}
