package com.example.employee_management_app;

import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Additional employee fields missing from the original profile screen. */
public class HrDetailsActivity extends AppCompatActivity {
    private int employeeId;
    private EditText dob, gender, address, hire;
    private Spinner managerSpinner, employmentSpinner;
    private TextView companyInfo;
    private final List<Integer> managerIds = new ArrayList<>();
    private ArrayAdapter<String> managerAdapter;
    private int originalManagerId = 0;
    private String originalEmployment = "";
    private boolean profileLoaded = false, managersLoaded = false;
    private boolean hrManager;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        employeeId = getIntent().getIntExtra("EMPLOYEE_ID", TokenManager.getInstance(this).getUserId());
        hrManager = HrUi.managerHr(this);
        boolean personal = employeeId == TokenManager.getInstance(this).getUserId();
        boolean editable = personal || HrUi.hr(this)
                || "admin".equalsIgnoreCase(TokenManager.getInstance(this).getRole());
        LinearLayout root = HrUi.page(this, "Thông tin hồ sơ mở rộng");
        companyInfo = HrUi.label(root, "Đang tải thông tin…");
        dob = HrUi.input(root, "Ngày sinh YYYY-MM-DD");
        gender = HrUi.input(root, "Giới tính: male / female / other");
        address = HrUi.input(root, "Địa chỉ");
        HrUi.label(root, "Quản lý trực tiếp (không bắt buộc)");
        managerSpinner = new Spinner(this);
        root.addView(managerSpinner);
        managerAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, new ArrayList<String>());
        managerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        managerSpinner.setAdapter(managerAdapter);
        managerIds.add(0);
        managerAdapter.add("Không có quản lý trực tiếp");
        managerSpinner.setEnabled(false);
        hire = HrUi.input(root, "Ngày vào làm YYYY-MM-DD (Trưởng phòng HR)");
        HrUi.label(root, "Trạng thái làm việc");
        employmentSpinner = new Spinner(this);
        root.addView(employmentSpinner);
        employmentSpinner.setEnabled(false);

        dob.setEnabled(editable);
        gender.setEnabled(editable);
        address.setEnabled(editable);
        hire.setEnabled(hrManager);
        if (editable) HrUi.button(root, "Lưu thông tin cho phép", this::save);
        HrUi.button(root, "Tải lại", this::load);
        if (hrManager) loadManagers();
        load();
    }

    private void loadManagers() {
        HrUi.call(this, HrUi.api(this).managerOptions(employeeId), data -> {
            // Chỉ đưa trưởng phòng đang hoạt động vào danh sách; không có thông tin nhạy cảm.
            managerIds.clear();
            managerAdapter.clear();
            managerIds.add(0);
            managerAdapter.add("Không có quản lý trực tiếp");
            for (Map<String, Object> item : HrUi.rows(data)) {
                int id = HrUi.n(item.get("id"));
                if (id <= 0 || id == employeeId) continue;
                managerIds.add(id);
                managerAdapter.add(HrUi.s(item.get("full_name")) + " – "
                        + HrUi.s(item.get("department_name")) + " (#" + id + ")");
            }
            managersLoaded = true;
            managerSpinner.setEnabled(hrManager);
            selectCurrentManager();
        });
    }

    private void selectCurrentManager() {
        if (!profileLoaded) return;
        if (!hrManager || !managersLoaded) {
            // Read-only accounts should still see the saved manager ID, not a false "none".
            managerIds.clear();
            managerAdapter.clear();
            managerIds.add(0);
            managerAdapter.add("Không có quản lý trực tiếp");
            if (originalManagerId > 0) {
                managerIds.add(originalManagerId);
                managerAdapter.add("Quản lý hiện tại #" + originalManagerId + " (chỉ HR thay đổi)");
            }
            managerSpinner.setSelection(originalManagerId > 0 ? 1 : 0);
            return;
        }
        int index = managerIds.indexOf(originalManagerId);
        if (index < 0 && originalManagerId > 0) {
            // Hồ sơ cũ có quản lý bị khóa / đã nghỉ / không còn là manager:
            // Hiển thị nguyên giá trị cũ để không âm thầm xóa khi lưu trường khác.
            managerIds.add(originalManagerId);
            managerAdapter.add("Quản lý cũ #" + originalManagerId + " (không còn khả dụng)");
            index = managerIds.size() - 1;
        }
        managerSpinner.setSelection(Math.max(0, index));
    }

    private void showEmploymentStatus(String value) {
        ArrayList<String> labels = new ArrayList<>();
        int selection;
        if ("left".equals(value)) {
            labels.add("Đã nghỉ việc (chỉ BOD phê duyệt)");
            selection = 0;
        } else {
            labels.add("Chọn trạng thái");
            labels.add("Thử việc");
            labels.add("Đang làm");
            selection = "probation".equals(value) ? 1 : "working".equals(value) ? 2 : 0;
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        employmentSpinner.setAdapter(adapter);
        employmentSpinner.setSelection(selection);
        employmentSpinner.setEnabled(hrManager && !"left".equals(value));
    }

    private void load() {
        HrUi.call(this, HrUi.api(this).profile(employeeId), data -> {
            Map<String, Object> p = HrUi.map(data);
            companyInfo.setText("Hồ sơ #" + employeeId + " | " + HrUi.s(p.get("full_name")) + "\n"
                    + "Phòng: " + HrUi.s(p.get("dept_name"))
                    + " | Chức vụ: " + HrUi.s(p.get("position_name")) + "\n"
                    + "Lương cơ bản (nếu có quyền): " + HrUi.s(p.get("base_salary")) + "\n"
                    + "Trạng thái tài khoản: " + (HrUi.n(p.get("status")) == 1 ? "Hoạt động" : "Đã khóa"));
            dob.setText(HrUi.s(p.get("birth_date")));
            gender.setText(HrUi.s(p.get("gender")));
            address.setText(HrUi.s(p.get("address")));
            hire.setText(HrUi.s(p.get("hire_date")));
            originalManagerId = HrUi.n(p.get("direct_manager_id"));
            originalEmployment = HrUi.s(p.get("employment_status"));
            showEmploymentStatus(originalEmployment);
            profileLoaded = true;
            selectCurrentManager();
        });
    }

    private void save() {
        if (!profileLoaded) {
            Toast.makeText(this, "Chưa tải xong hồ sơ", Toast.LENGTH_SHORT).show();
            return;
        }
        String birthValue = nil(dob), hireValue = nil(hire);
        if (!HrDateRules.atLeast18AtHire(birthValue, hireValue)) {
            hire.setError("Ngày vào làm phải từ ngày đủ 18 tuổi");
            hire.requestFocus();
            return;
        }
        Map<String, Object> body = new HashMap<>();
        body.put("id", employeeId);
        body.put("birth_date", birthValue);
        body.put("gender", nil(gender));
        body.put("address", nil(address));
        if (hrManager) {
            if (managersLoaded && managerSpinner.getSelectedItemPosition() >= 0) {
                int managerId = managerIds.get(managerSpinner.getSelectedItemPosition());
                if (managerId != originalManagerId) body.put("direct_manager_id", managerId == 0 ? null : managerId);
            }
            body.put("hire_date", hireValue);
            if (!"left".equals(originalEmployment)) {
                int chosen = employmentSpinner.getSelectedItemPosition();
                String status = chosen == 1 ? "probation" : chosen == 2 ? "working" : "";
                if (!status.isEmpty() && !status.equals(originalEmployment)) body.put("employment_status", status);
            }
        }
        HrUi.call(this, HrUi.api(this).updateProfile(body), data -> {
            Toast.makeText(this, "Đã lưu thông tin hành chính", Toast.LENGTH_SHORT).show();
            load();
        });
    }

    private String nil(EditText editText) {
        String value = editText.getText().toString().trim();
        return value.isEmpty() ? null : value;
    }
}
