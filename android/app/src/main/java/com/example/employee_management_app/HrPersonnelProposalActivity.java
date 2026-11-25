package com.example.employee_management_app;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class HrPersonnelProposalActivity extends AppCompatActivity {
    private enum Type {
        SALARY("base_salary_change","Điều chỉnh lương"),
        PROMOTION("promotion","Thăng chức"),
        TRANSFER("transfer","Chuyển phòng"),
        TERMINATION("termination","Nghỉ việc");

        final String key, label;
        Type(String key,String label){this.key=key;this.label=label;}
        @Override public String toString(){return label;}
    }

    private int employeeId, submittedId;
    private EmployeeDetailResponse.Detail employee;
    private TextView identity, result, departmentLabel, positionLabel;
    private LinearLayout form;
    private EditText salary, reason;
    private Spinner type, department, position;
    private Button dateButton, submitButton, retryProfile, retryCatalog;
    private String effectiveDate="";
    private List<Map<String,Object>> positions;
    private boolean departmentsLoaded, departmentsRequested, positionsRequested;
    private int shownPositionDepartment=-1;

    @Override protected void onCreate(Bundle state){
        super.onCreate(state);
        employeeId=getIntent().getIntExtra("EMPLOYEE_ID",0);
        if(!HrUi.managerHr(this) || employeeId<=0){finish();return;}
        if(state!=null)submittedId=state.getInt("submitted_id");

        LinearLayout root=HrUi.page(this,"Đề xuất nhân sự – chờ BOD");
        LinearLayout person=HrUi.card(root);
        identity=HrUi.title(person,"Đang tải hồ sơ nhân viên…");
        retryProfile=HrUi.secondary(person,"Tải lại hồ sơ",this::loadEmployee);
        retryProfile.setVisibility(View.GONE);
        result=HrUi.label(root,"");
        result.setVisibility(View.GONE);
        form=HrUi.card(root);
        form.setVisibility(View.GONE);
        HrUi.label(form,"Loại đề xuất");
        type=spinner(form);
        setOptions(type,Type.values());
        salary=HrUi.input(form,"Lương mới (VND)");
        salary.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);
        departmentLabel=HrUi.label(form,"Phòng ban mới");
        department=spinner(form);
        department.setEnabled(false);
        positionLabel=HrUi.label(form,"Chức vụ mới");
        position=spinner(form);
        retryCatalog=HrUi.secondary(form,"Tải lại danh mục",()->loadCatalogs(true));
        reason=HrUi.input(form,"Lý do đề xuất (bắt buộc)");
        reason.setFilters(new InputFilter[]{new InputFilter.LengthFilter(1000)});
        dateButton=HrUi.secondary(form,"",this::chooseDate);
        submitButton=HrUi.button(form,"Gửi đề xuất BOD",this::submit);
        onSelection(type,this::updateFields);
        onSelection(department,this::updatePositionChoices);
        HrUi.secondary(root,"Theo dõi đề xuất của nhân viên",this::openTracking);
        loadEmployee();
    }

    @Override protected void onSaveInstanceState(Bundle state){
        state.putInt("submitted_id",submittedId);
        super.onSaveInstanceState(state);
    }

    private Spinner spinner(LinearLayout parent){
        Spinner field=new Spinner(this);
        parent.addView(field,new LinearLayout.LayoutParams(-1,HrUi.dp(this,48)));
        return field;
    }

    private <T> void setOptions(Spinner field,T[] options){
        ArrayAdapter<T> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,options);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        field.setAdapter(adapter);
    }

    private void onSelection(Spinner field,Runnable action){
        field.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
            @Override public void onItemSelected(AdapterView<?> parent,View row,int index,long id){action.run();}
            @Override public void onNothingSelected(AdapterView<?> parent){action.run();}
        });
    }

    private void loadEmployee(){
        retryProfile.setVisibility(View.GONE);
        identity.setText("Đang tải hồ sơ nhân viên…");
        ApiClient.getClient(this).create(EmployeeApi.class).getDetail(employeeId).enqueue(new Callback<EmployeeDetailResponse>(){
            @Override public void onResponse(Call<EmployeeDetailResponse> call,Response<EmployeeDetailResponse> response){
                if(isFinishing()||isDestroyed())return;
                EmployeeDetailResponse body=response.body();
                if(!response.isSuccessful()||body==null||!body.isSuccess()||body.getData()==null
                        ||body.getData().getId()!=employeeId){
                    profileError(ApiError.message(response,"Không tải được đúng hồ sơ nhân viên."));
                    return;
                }
                employee=body.getData();
                String salaryValue=employee.getBaseSalary();
                identity.setText(employee.getFullName()+" · #"+employeeId
                        +"\nTài khoản: "+ProfileValidation.display(employee.getUsername())
                        +"\nPhòng ban: "+ProfileValidation.display(employee.getDeptName())
                        +"\nChức vụ: "+ProfileValidation.display(employee.getPositionName())
                        +(salaryValue==null||salaryValue.isEmpty()?"":"\nLương cơ bản hiện tại: "+salaryValue+" VND"));
                if(employee.getRoleId()==1){
                    result.setText("Tài khoản Admin kỹ thuật không áp dụng đề xuất nhân sự.");
                    result.setVisibility(View.VISIBLE);
                }else if(submittedId>0)showResult();
                else{
                    form.setVisibility(View.VISIBLE);
                    updateFields();
                }
            }
            @Override public void onFailure(Call<EmployeeDetailResponse> call,Throwable error){
                if(!isFinishing()&&!isDestroyed())profileError("Không kết nối được API hồ sơ.");
            }
        });
    }

    private void profileError(String message){
        identity.setText(message);
        retryProfile.setVisibility(View.VISIBLE);
    }

    private Type selection(){return (Type)type.getSelectedItem();}

    private void updateFields(){
        Type selected=selection();
        boolean transfer=selected==Type.TRANSFER;
        boolean usePosition=transfer||selected==Type.PROMOTION;
        salary.setVisibility(selected==Type.SALARY?View.VISIBLE:View.GONE);
        departmentLabel.setVisibility(transfer?View.VISIBLE:View.GONE);
        department.setVisibility(transfer?View.VISIBLE:View.GONE);
        positionLabel.setVisibility(usePosition?View.VISIBLE:View.GONE);
        position.setVisibility(usePosition?View.VISIBLE:View.GONE);
        retryCatalog.setVisibility(usePosition && (positions==null || (transfer&&!departmentsLoaded))
                ?View.VISIBLE:View.GONE);
        dateButton.setText(effectiveDate.isEmpty() ? (selected==Type.TERMINATION?"Chọn ngày hiệu lực (bắt buộc)"
                :"Chọn ngày hiệu lực (tùy chọn)") : "Ngày hiệu lực: "+effectiveDate);
        updatePositionChoices();
        if(employee!=null && usePosition)loadCatalogs(false);
    }

    private void loadCatalogs(boolean retry){
        HrApi api=HrUi.api(this);
        if(selection()==Type.TRANSFER && !departmentsLoaded && (retry||!departmentsRequested)){
            departmentsRequested=true;
            HrUi.call(this,api.departments(),data->{
                List<DepartmentListResponse.Item> options=new ArrayList<>();
                options.add(new DepartmentListResponse.Item(0,"Chọn phòng ban mới"));
                for(Map<String,Object> row:HrUi.rows(data)){
                    int id=HrUi.n(row.get("id"));
                    if(id!=employee.getDepartmentId())options.add(new DepartmentListResponse.Item(id,HrUi.s(row.get("name"))));
                }
                departmentsLoaded=true;
                setOptions(department,options.toArray(new DepartmentListResponse.Item[0]));
                department.setEnabled(options.size()>1);
                updateFields();
            });
        }
        if(positions==null && (retry||!positionsRequested)){
            positionsRequested=true;
            HrUi.call(this,api.positions(),data->{positions=HrUi.rows(data);updateFields();});
        }
    }

    private int destinationDepartment(){
        if(selection()!=Type.TRANSFER)return employee==null?0:employee.getDepartmentId();
        DepartmentListResponse.Item chosen=(DepartmentListResponse.Item)department.getSelectedItem();
        return chosen==null?0:chosen.getId();
    }

    private void updatePositionChoices(){
        Type selected=selection();
        if(employee==null || (selected!=Type.PROMOTION && selected!=Type.TRANSFER))return;
        int departmentId=destinationDepartment();
        shownPositionDepartment=departmentId;
        List<PositionListResponse.Item> options=new ArrayList<>();
        if(positions!=null && (selected==Type.PROMOTION || departmentId>0)){
            for(Map<String,Object> row:positions){
                int id=HrUi.n(row.get("id"));
                if(HrUi.n(row.get("is_active"))!=1 || (selected==Type.PROMOTION && id==employee.getPositionId()))continue;
                if(row.get("department_id")==null || HrUi.n(row.get("department_id"))==departmentId)
                    options.add(new PositionListResponse.Item(id,HrUi.s(row.get("title"))));
            }
        }
        String placeholder=options.isEmpty()?(positions==null?"Chưa tải danh mục chức vụ"
                :selected==Type.TRANSFER && departmentId==0?"Chọn phòng ban trước":"Không có chức vụ phù hợp")
                :"Chọn chức vụ mới";
        position.setEnabled(!options.isEmpty());
        options.add(0,new PositionListResponse.Item(0,placeholder));
        setOptions(position,options.toArray(new PositionListResponse.Item[0]));
    }

    private void chooseDate(){
        Calendar today=Calendar.getInstance();
        DatePickerDialog dialog=new DatePickerDialog(this,(view,year,month,day)->{
            effectiveDate=String.format(Locale.ROOT,"%04d-%02d-%02d",year,month+1,day);
            dateButton.setText("Ngày hiệu lực: "+effectiveDate);
        },today.get(Calendar.YEAR),today.get(Calendar.MONTH),today.get(Calendar.DAY_OF_MONTH));
        if(selection()!=Type.TERMINATION)dialog.setButton(DatePickerDialog.BUTTON_NEUTRAL,"Bỏ ngày",(d,w)->{
            effectiveDate="";
            dateButton.setText("Chọn ngày hiệu lực (tùy chọn)");
        });
        dialog.show();
    }

    private void submit(){
        Type selected=selection();
        if(employee==null || selected==null || !HrUi.managerHr(this) || submittedId>0)return;
        String reasonText=reason.getText().toString().trim();
        if(reasonText.isEmpty()){reason.setError("Nhập lý do đề xuất.");return;}
        Map<String,Object> body=new HashMap<>();
        body.put("employee_id",employeeId);
        body.put("proposal_type",selected.key);
        body.put("reason",reasonText);
        if(!effectiveDate.isEmpty())body.put("effective_date",effectiveDate);
        if(selected==Type.SALARY){
            try{
                BigDecimal value=new BigDecimal(salary.getText().toString().trim().replace(',','.'));
                if(value.signum()<0||value.compareTo(BigDecimal.valueOf(9999999999L))>0)throw new NumberFormatException();
                body.put("base_salary",value.toPlainString());
            }catch(NumberFormatException error){salary.setError("Nhập mức lương hợp lệ.");return;}
        }else if(selected==Type.PROMOTION || selected==Type.TRANSFER){
            int departmentId=destinationDepartment();
            if(selected==Type.TRANSFER && departmentId<=0){showError("Chọn phòng ban mới.");return;}
            PositionListResponse.Item chosen=(PositionListResponse.Item)position.getSelectedItem();
            if(chosen==null || chosen.getId()<=0 || shownPositionDepartment!=departmentId){
                showError("Chọn chức vụ mới trong danh mục.");return;
            }
            body.put("position_id",chosen.getId());
            if(selected==Type.TRANSFER)body.put("department_id",departmentId);
        }else if(effectiveDate.isEmpty()){showError("Chọn ngày hiệu lực nghỉ việc.");return;}

        submitButton.setEnabled(false);
        result.setText("Đang gửi đề xuất…");
        result.setVisibility(View.VISIBLE);
        HrUi.api(this).createProposal(body).enqueue(new Callback<Map<String,Object>>(){
            @Override public void onResponse(Call<Map<String,Object>> call,Response<Map<String,Object>> response){
                if(isFinishing()||isDestroyed())return;
                Map<String,Object> envelope=HrUi.map(response.body());
                Map<String,Object> data=HrUi.map(envelope.get("data"));
                int id=HrUi.n(data.get("id"));
                if(response.isSuccessful() && Boolean.TRUE.equals(envelope.get("success"))
                        && id>0 && HrUi.n(data.get("employee_id"))==employeeId && "pending".equals(data.get("status"))){
                    submittedId=id;
                    showResult();
                }else{
                    submitButton.setEnabled(true);
                    showError(ApiError.message(response,"Chưa xác nhận được đề xuất. Kiểm tra trạng thái trước khi gửi lại."));
                }
            }
            @Override public void onFailure(Call<Map<String,Object>> call,Throwable error){
                if(isFinishing()||isDestroyed())return;
                submitButton.setEnabled(true);
                showError("Mất kết nối. Kiểm tra trạng thái trước khi gửi lại.");
            }
        });
    }

    private void showError(String error){
        result.setText(error);
        result.setVisibility(View.VISIBLE);
        android.widget.Toast.makeText(this,error,android.widget.Toast.LENGTH_SHORT).show();
    }

    private void showResult(){
        result.setText("Đã gửi đề xuất #"+submittedId+" · Chờ BOD");
        result.setVisibility(View.VISIBLE);
        form.setVisibility(View.GONE);
    }

    private void openTracking(){
        startActivity(new Intent(this,HrProposalsActivity.class)
                .putExtra(HrProposalsActivity.TRACK_EMPLOYEE_ID,employeeId));
    }
}
