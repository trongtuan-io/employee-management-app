package com.example.employee_management_app;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import android.view.ViewGroup;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/** Candidate drafts for HR and technical account creation for Admin. */
public class CreateAccountActivity extends AppCompatActivity {

    private EditText etUsername, etPassword, etFullName, etPhone, etEmail;
    private Spinner spRole, spDept, spPosition;
    private Button btnCreate;
    private EditText etReason, etHireDate;
    private final List<DepartmentListResponse.Item> deptChoices = new ArrayList<>();
    private final List<PositionListResponse.Item> positionChoices = new ArrayList<>();
    private Call<PositionListResponse> positionsCall;
    private int positionsForDept = -1;
    private boolean sysAdmin;
    private int draftId, draftVersion, draftDepartment, draftPosition, workflowVersion;

    // Map spinner -> role_id backend: 1 = admin, 2 = manager, 3 = staff.

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_account);
        setTitle("Tạo tài khoản nhân viên");

        String role = TokenManager.getInstance(this).getRole();
        sysAdmin = "admin".equalsIgnoreCase(role);
        if (!HrUi.hr(this) && !sysAdmin) {
            Toast.makeText(this, "Chỉ HR hoặc Admin kỹ thuật được tạo hồ sơ", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        setTitle(sysAdmin ? "Tạo tài khoản Admin kỹ thuật" : "Hồ sơ ứng viên cấp cao");

        etUsername = findViewById(R.id.etNewUsername);
        etPassword = findViewById(R.id.etNewPassword);
        etPassword.setVisibility("admin".equalsIgnoreCase(role) ? View.VISIBLE : View.GONE);
        etFullName = findViewById(R.id.etNewFullName);
        etPhone = findViewById(R.id.etNewPhone);
        etEmail = findViewById(R.id.etNewEmail);
        spDept = findViewById(R.id.spNewDept);
        spPosition = findViewById(R.id.spNewPosition);
        spRole = findViewById(R.id.spNewRole);
        btnCreate = findViewById(R.id.btnDoCreateAccount);
        if(!sysAdmin){
            ViewGroup parent = (ViewGroup) btnCreate.getParent();
            int idx = parent.indexOfChild(btnCreate);
            etReason = new EditText(this); etReason.setHint("Lý do tuyển dụng (bắt buộc)");
            etReason.setSingleLine(true);
            parent.addView(etReason,idx++);
            etHireDate = new EditText(this);
            etHireDate.setSingleLine(true);
            etHireDate.setHint("Ngày vào làm (YYYY-MM-DD, nếu có)");
            parent.addView(etHireDate,idx);
            btnCreate.setText("Lưu nháp và mở giấy tờ");
            Intent draft = getIntent();
            draftId = draft.getIntExtra("DRAFT_ID", 0);
            draftVersion = draft.getIntExtra("DRAFT_VERSION", 0);
            draftDepartment = draft.getIntExtra("DRAFT_DEPARTMENT", 0);
            draftPosition = draft.getIntExtra("DRAFT_POSITION", 0);
            workflowVersion = draft.getIntExtra("WORKFLOW_VERSION", 4);
            etUsername.setText(draft.getStringExtra("username"));
            etFullName.setText(draft.getStringExtra("full_name"));
            etEmail.setText(draft.getStringExtra("email"));
            etPhone.setText(draft.getStringExtra("phone"));
            etReason.setText(draft.getStringExtra("reason"));
            etHireDate.setText(draft.getStringExtra("hire_date"));
        }

        // HR creates candidate proposals only; server derives approval level from position.
        String[] roleNames = "admin".equalsIgnoreCase(role)
                ? new String[]{"Admin kỹ thuật"} : new String[]{"Tuyển cấp cao · Manager HR và BOD duyệt"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, roleNames);
        spRole.setAdapter(adapter);
        spRole.setSelection(0);

        spDept.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                if (pos >= 0 && pos < deptChoices.size()) {
                    loadPositions(deptChoices.get(pos).getId());
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        loadDepartments();
        btnCreate.setOnClickListener(v -> doCreate());
    }

    private void loadDepartments() {
        EmployeeApi api = ApiClient.getClient(this).create(EmployeeApi.class);
        api.getDepartments().enqueue(new Callback<DepartmentListResponse>() {
            @Override
            public void onResponse(Call<DepartmentListResponse> c, Response<DepartmentListResponse> r) {
                if (r.isSuccessful() && r.body() != null && r.body().isSuccess() && r.body().getData() != null) {
                    deptChoices.clear();
                    deptChoices.add(new DepartmentListResponse.Item(0, "Không chỉ định"));
                    deptChoices.addAll(r.body().getData());
                    ArrayAdapter<DepartmentListResponse.Item> a = new ArrayAdapter<>(
                            CreateAccountActivity.this,
                            android.R.layout.simple_spinner_dropdown_item, deptChoices);
                    spDept.setAdapter(a);
                    for (int i = 0; i < deptChoices.size(); i++) {
                        if (deptChoices.get(i).getId() == draftDepartment) {
                            spDept.setSelection(i);
                            loadPositions(draftDepartment);
                            break;
                        }
                    }
                } else {
                    Toast.makeText(CreateAccountActivity.this,
                            "Không tải được danh sách phòng ban", Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(Call<DepartmentListResponse> c, Throwable t) {
                Toast.makeText(CreateAccountActivity.this,
                        "Lỗi kết nối khi tải phòng ban: " + t.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    // deptId = 0 -> tat ca chuc vu; > 0 -> chuc vu cua phong do + chuc vu dung chung.
    // Huy call cu + bo qua response ve tre cua phong truoc (tranh spinner chua chuc vu sai phong).
    private void loadPositions(int deptId) {
        if (positionsCall != null) positionsCall.cancel();
        positionsForDept = deptId;
        positionChoices.clear();
        positionChoices.add(new PositionListResponse.Item(0, "Đang tải..."));
        ArrayAdapter<PositionListResponse.Item> loading = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_dropdown_item, positionChoices);
        spPosition.setAdapter(loading);
        spPosition.setEnabled(false);
        if (!sysAdmin && deptId == 0) {
            positionChoices.set(0, new PositionListResponse.Item(0, "Chọn phòng ban trước"));
            loading.notifyDataSetChanged();
            return;
        }
        EmployeeApi api = ApiClient.getClient(this).create(EmployeeApi.class);
        positionsCall = !sysAdmin && workflowVersion >= 4
                ? HrUi.api(this).candidatePositions(deptId)
                : (deptId > 0 ? api.getPositions(deptId) : api.getPositions());
        positionsCall.enqueue(new Callback<PositionListResponse>() {
            @Override
            public void onResponse(Call<PositionListResponse> c, Response<PositionListResponse> r) {
                if (isFinishing() || isDestroyed() || deptId != positionsForDept || c.isCanceled()) return;
                spPosition.setEnabled(true);
                if (r.isSuccessful() && r.body() != null && r.body().isSuccess() && r.body().getData() != null) {
                    positionChoices.clear();
                    positionChoices.add(new PositionListResponse.Item(0, "Không chỉ định"));
                    positionChoices.addAll(r.body().getData());
                    ArrayAdapter<PositionListResponse.Item> a = new ArrayAdapter<>(
                            CreateAccountActivity.this,
                            android.R.layout.simple_spinner_dropdown_item, positionChoices);
                    spPosition.setAdapter(a);
                    if (deptId == draftDepartment) {
                        for (int i = 0; i < positionChoices.size(); i++) {
                            if (positionChoices.get(i).getId() == draftPosition) {
                                spPosition.setSelection(i);
                                break;
                            }
                        }
                    }
                } else {
                    positionChoices.clear();
                    positionChoices.add(new PositionListResponse.Item(0, "Không tải được chức vụ"));
                    spPosition.setAdapter(new ArrayAdapter<>(CreateAccountActivity.this,
                            android.R.layout.simple_spinner_dropdown_item, positionChoices));
                }
            }

            @Override
            public void onFailure(Call<PositionListResponse> c, Throwable t) {
                if (isFinishing() || isDestroyed() || deptId != positionsForDept || c.isCanceled()) return;
                spPosition.setEnabled(true);
                positionChoices.clear();
                positionChoices.add(new PositionListResponse.Item(0, "Không tải được chức vụ"));
                spPosition.setAdapter(new ArrayAdapter<>(CreateAccountActivity.this,
                        android.R.layout.simple_spinner_dropdown_item, positionChoices));
                Toast.makeText(CreateAccountActivity.this,
                        "Lỗi kết nối khi tải chức vụ: " + t.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void doCreate() {
        String username = etUsername.getText().toString().trim();
        String password = etPassword.getText().toString(); // khong trim mat khau
        String fullName = etFullName.getText().toString().trim();
        String phone = etPhone.getText().toString().trim();
        String email = etEmail.getText().toString().trim();

        if (username.isEmpty() || fullName.isEmpty()) {
            Toast.makeText(this, "Nhập mã nhân viên và họ tên", Toast.LENGTH_SHORT).show();
            return;
        }
        if ("admin".equalsIgnoreCase(TokenManager.getInstance(this).getRole()) && password.length() < 8) {
            etPassword.setError("Mật khẩu Admin ít nhất 8 ký tự");return;
        }
        // Server bat buoc email hop le (truoc day app cho bo trong roi bao loi sai nguyen nhan).
        if (email.isEmpty() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            etEmail.setError("Email bắt buộc và phải đúng định dạng");
            etEmail.requestFocus();
            return;
        }

        boolean adminTechnical = "admin".equalsIgnoreCase(TokenManager.getInstance(this).getRole());
        int roleId = adminTechnical ? 1 : 3;

        Integer deptId = null;
        int deptPos = spDept.getSelectedItemPosition();
        if (deptPos >= 0 && deptPos < deptChoices.size() && deptChoices.get(deptPos).getId() > 0) {
            deptId = deptChoices.get(deptPos).getId();
        }
        Integer posId = null;
        int posPos = spPosition.getSelectedItemPosition();
        if (posPos >= 0 && posPos < positionChoices.size() && positionChoices.get(posPos).getId() > 0) {
            posId = positionChoices.get(posPos).getId();
        }

        if(!adminTechnical && (deptId==null || posId==null)){
            Toast.makeText(this,"Chọn phòng ban và chức vụ tuyển dụng",Toast.LENGTH_LONG).show();
            return;
        }
        btnCreate.setEnabled(false); // prevent accidental double submission
        if(!adminTechnical){
            String reason=etReason.getText().toString().trim();
            if(reason.isEmpty()){etReason.setError("Cần lý do tuyển dụng");btnCreate.setEnabled(true);return;}
            Map<String,Object> b=new HashMap<>();
            b.put("proposal_type","recruitment");b.put("username",username);
            b.put("full_name",fullName);b.put("email",email);
            b.put("phone",phone.isEmpty()?null:phone);
            b.put("department_id",deptId);b.put("position_id",posId);b.put("reason",reason);
            b.put("hire_date",etHireDate.getText().toString().trim());
            if(draftId>0){
                b.put("id",draftId);
                b.put("version",draftVersion);
            }
            Call<Map<String,Object>> request = draftId==0
                ? HrUi.api(this).createProposal(b) : HrUi.api(this).updateRecruitmentDraft(b);
            request.enqueue(new Callback<Map<String,Object>>(){
                @Override public void onResponse(Call<Map<String,Object>> c,Response<Map<String,Object>> r){
                    btnCreate.setEnabled(true);
                    if(r.isSuccessful()&&r.body()!=null&&Boolean.TRUE.equals(r.body().get("success"))){
                        int id=HrUi.n(HrUi.map(r.body().get("data")).get("id"));
                        startActivity(HrDocumentsActivity.candidateIntent(CreateAccountActivity.this,id,true));
                        finish();
                    }else Toast.makeText(CreateAccountActivity.this,
                        ApiError.message(r,"Không lưu được hồ sơ tuyển dụng"),Toast.LENGTH_LONG).show();
                }
                @Override public void onFailure(Call<Map<String,Object>> c,Throwable t){
                    btnCreate.setEnabled(true);
                    Toast.makeText(CreateAccountActivity.this,"Lỗi kết nối: "+t.getMessage(),Toast.LENGTH_LONG).show();
                }
            });
            return;
        }
        RegisterRequest req = new RegisterRequest(username, password, fullName,
                phone.isEmpty() ? null : phone, email, roleId, deptId, posId);
        AuthApi api = ApiClient.getClient(this).create(AuthApi.class);
        api.register(req).enqueue(new Callback<ApiResponse>() {
            @Override public void onResponse(Call<ApiResponse> c, Response<ApiResponse> r) {
                btnCreate.setEnabled(true);
                if(r.isSuccessful()&&r.body()!=null&&r.body().isSuccess()){
                    Toast.makeText(CreateAccountActivity.this,"Đã tạo tài khoản Admin kỹ thuật",Toast.LENGTH_LONG).show();finish();
                }else Toast.makeText(CreateAccountActivity.this,
                    ApiError.message(r,"Tạo tài khoản thất bại"),Toast.LENGTH_LONG).show();
            }
            @Override public void onFailure(Call<ApiResponse> c,Throwable t){
                btnCreate.setEnabled(true);
                Toast.makeText(CreateAccountActivity.this,"Lỗi kết nối: "+t.getMessage(),Toast.LENGTH_LONG).show();
            }
        });
    }
}
