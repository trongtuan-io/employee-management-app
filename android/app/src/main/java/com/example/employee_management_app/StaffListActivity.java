package com.example.employee_management_app;

import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

// Danh sach nhan vien cho ADMIN: bam vao 1 nguoi -> mo man sua ho so.
public class StaffListActivity extends AppCompatActivity {
    private static final String STATE_SEARCH = "staff_search";
    private static final String STATE_DEPARTMENT = "staff_department";
    private static final String STATE_DEPARTMENT_NAME = "staff_department_name";
    private static final String STATE_SORT = "staff_sort";
    private static final String STATE_PAGE = "staff_page";
    private static final String STATE_FULLTEXT = "staff_fulltext";
    private boolean fulltext;

    private final List<EmployeeListResponse.Item> items = new ArrayList<>();
    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private final Runnable searchRunnable = this::loadStaff;
    private ArrayAdapter<EmployeeListResponse.Item> adapter;
    private ArrayAdapter<DepartmentListResponse.Item> departmentAdapter;
    private StaffListQuery query = new StaffListQuery("", 0, 0, 1);
    private EmployeeListResponse.Pagination pagination;
    private Call<EmployeeListResponse> staffCall;
    private Call<DepartmentListResponse> departmentCall;
    private EmployeeApi api;
    private Spinner departmentSpinner;
    private ListView staffList;
    private TextView listMessage, pageInfo, departmentMessage;
    private Button previousButton, nextButton, retryDepartments;
    private ProgressBar progress;
    private boolean loading;
    private String departmentName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_staff_list);
        applyWindowInsets();
        setTitle(R.string.staff_title);
        api = ApiClient.getClient(this).create(EmployeeApi.class);
        if (savedInstanceState != null) {
            query = new StaffListQuery(savedInstanceState.getString(STATE_SEARCH, ""),
                    savedInstanceState.getInt(STATE_DEPARTMENT, 0),
                    savedInstanceState.getInt(STATE_SORT, 0),
                    savedInstanceState.getInt(STATE_PAGE, 1));
            departmentName = savedInstanceState.getString(STATE_DEPARTMENT_NAME);
            fulltext = savedInstanceState.getBoolean(STATE_FULLTEXT, false);
        } else if (getIntent().hasExtra("DEPARTMENT_ID")) {
            query = query.withDepartment(getIntent().getIntExtra("DEPARTMENT_ID", 0));
            departmentName = getIntent().getStringExtra("DEPARTMENT_NAME");
        }
        androidx.appcompat.widget.SwitchCompat modeSwitch = findViewById(R.id.swFullTextSearch);
        modeSwitch.setChecked(fulltext);
        modeSwitch.setOnCheckedChangeListener((view, checked) -> {
            if (fulltext == checked) return;
            fulltext = checked;
            query = query.withPage(1);
            cancelStaffCall();
            loadStaff();
        });
        boolean admin = "admin".equalsIgnoreCase(TokenManager.getInstance(this).getRole());
        findViewById(R.id.btnManageDepartments).setVisibility(admin ? View.VISIBLE : View.GONE);
        findViewById(R.id.btnImportEmployees).setVisibility(admin ? View.VISIBLE : View.GONE);
        findViewById(R.id.btnManageDepartments).setOnClickListener(v ->
                startActivity(new Intent(this, DepartmentActivity.class)));
        findViewById(R.id.btnImportEmployees).setOnClickListener(v ->
                startActivity(new Intent(this, ImportEmployeesActivity.class)));

        staffList = findViewById(R.id.lvStaff);
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, items);
        staffList.setAdapter(adapter);
        staffList.setOnItemClickListener((parent, view, position, id) -> {
            if (position >= items.size()) return;
            Intent intent = new Intent(this, ProfileActivity.class);
            intent.putExtra("EMPLOYEE_ID", items.get(position).getId());
            startActivity(intent);
        });
        listMessage = findViewById(R.id.tvStaffMessage);
        pageInfo = findViewById(R.id.tvStaffPage);
        progress = findViewById(R.id.progressStaff);
        previousButton = findViewById(R.id.btnPreviousPage);
        nextButton = findViewById(R.id.btnNextPage);
        previousButton.setOnClickListener(v -> {
            if (!loading && pagination != null && query.page > 1) {
                changeQuery(query.withPage(query.page - 1), false);
            }
        });
        nextButton.setOnClickListener(v -> {
            if (!loading && pagination != null && query.page < pagination.getTotalPages()) {
                changeQuery(query.withPage(query.page + 1), false);
            }
        });

        EditText searchInput = findViewById(R.id.edtSearchEmployee);
        searchInput.setText(query.search);
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                changeQuery(query.withSearch(s.toString()), true);
            }
            @Override public void afterTextChanged(Editable s) { }
        });
        findViewById(R.id.btnSearchEmployee).setOnClickListener(v -> loadStaff());
        searchInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId != EditorInfo.IME_ACTION_SEARCH) return false;
            loadStaff();
            return true;
        });

        Spinner sortSpinner = findViewById(R.id.spStaffSort);
        ArrayAdapter<CharSequence> sortAdapter = ArrayAdapter.createFromResource(this,
                R.array.staff_sort_options, android.R.layout.simple_spinner_item);
        sortAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sortSpinner.setAdapter(sortAdapter);
        sortSpinner.setSelection(query.sortIndex);
        sortSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position == parent.getSelectedItemPosition()) {
                    changeQuery(query.withSort(position), false);
                }
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });

        departmentSpinner = findViewById(R.id.spStaffDepartment);
        departmentMessage = findViewById(R.id.tvDepartmentMessage);
        retryDepartments = findViewById(R.id.btnRetryDepartments);
        departmentAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item,
                new ArrayList<>());
        departmentAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        departmentAdapter.add(new DepartmentListResponse.Item(0, getString(R.string.staff_all_departments)));
        if (query.departmentId != 0) {
            if (departmentName == null) {
                departmentName = getString(R.string.staff_department_id, query.departmentId);
            }
            departmentAdapter.add(new DepartmentListResponse.Item(query.departmentId, departmentName));
        }
        departmentSpinner.setAdapter(departmentAdapter);
        departmentSpinner.setSelection(query.departmentId == 0 ? 0 : 1);
        departmentSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position != parent.getSelectedItemPosition()) return;
                DepartmentListResponse.Item selected = departmentAdapter.getItem(position);
                if (selected != null) {
                    departmentName = selected.getName();
                    changeQuery(query.withDepartment(selected.getId()), false);
                }
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });
        retryDepartments.setOnClickListener(v -> loadDepartments());
        loadDepartments();
    }

    private void applyWindowInsets() {
        View root = findViewById(R.id.staffListRoot);
        int left = root.getPaddingLeft(), top = root.getPaddingTop();
        int right = root.getPaddingRight(), bottom = root.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            Insets safe = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout() | WindowInsetsCompat.Type.ime());
            view.setPadding(left + safe.left, top + safe.top, right + safe.right, bottom + safe.bottom);
            return insets;
        });
        boolean dark = (getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView())
                .setAppearanceLightStatusBars(!dark);
        ViewCompat.requestApplyInsets(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Tai lai sau khi sua ho so, giu tu khoa, phong ban, cach sap xep va trang.
        loadStaff();
        loadDepartments();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putString(STATE_SEARCH, query.search);
        outState.putInt(STATE_DEPARTMENT, query.departmentId);
        outState.putString(STATE_DEPARTMENT_NAME, departmentName);
        outState.putInt(STATE_SORT, query.sortIndex);
        outState.putInt(STATE_PAGE, query.page);
        outState.putBoolean(STATE_FULLTEXT, fulltext);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onDestroy() {
        searchHandler.removeCallbacks(searchRunnable);
        cancelStaffCall();
        if (departmentCall != null) {
            departmentCall.cancel();
            departmentCall = null;
        }
        super.onDestroy();
    }

    private void changeQuery(StaffListQuery next, boolean debounce) {
        if (query.equals(next)) return;
        query = next;
        searchHandler.removeCallbacks(searchRunnable);
        cancelStaffCall();
        clearStaff();
        loading = true;
        updatePageControls();
        if (debounce) searchHandler.postDelayed(searchRunnable, 350);
        else loadStaff();
    }

    private void cancelStaffCall() {
        if (staffCall != null) {
            staffCall.cancel();
            staffCall = null;
        }
    }

    private void clearStaff() {
        pagination = null;
        items.clear();
        adapter.notifyDataSetChanged();
        listMessage.setVisibility(View.GONE);
    }

    private void loadStaff() {
        searchHandler.removeCallbacks(searchRunnable);
        // Cac thay doi truy van da huy request cu; bam lap lai cung truy van duoc bo qua.
        if (staffCall != null) return;
        clearStaff();
        loading = true;
        updatePageControls();
        staffCall = api.getEmployees(query.search, query.apiDepartmentId(), query.page,
                StaffListQuery.PAGE_SIZE, query.apiSort(), fulltext ? "fulltext" : "like");
        staffCall.enqueue(new Callback<EmployeeListResponse>() {
            @Override
            public void onResponse(Call<EmployeeListResponse> call, Response<EmployeeListResponse> response) {
                if (call != staffCall || call.isCanceled()) return;
                staffCall = null;
                loading = false;
                EmployeeListResponse body = response.body();
                if (!response.isSuccessful() || body == null || !body.isSuccess() || body.getData() == null) {
                    showStaffError(response.isSuccessful() ? getString(R.string.staff_load_error)
                            : getString(R.string.staff_http_error, response.code()));
                    return;
                }
                if (body.getPagination() == null || !body.getPagination().isValid()) {
                    showStaffError(getString(R.string.staff_pagination_error));
                    return;
                }
                for (EmployeeListResponse.Item item : body.getData()) {
                    if (item == null || item.getId() <= 0) {
                        showStaffError(getString(R.string.staff_load_error));
                        return;
                    }
                }
                pagination = body.getPagination();
                // Server co the dua ve trang cuoi neu so nhan vien da giam.
                query = query.withPage(pagination.getPage());
                items.addAll(body.getData());
                adapter.notifyDataSetChanged();
                staffList.setSelection(0);
                if (items.isEmpty()) {
                    listMessage.setText(R.string.staff_empty);
                    listMessage.setVisibility(View.VISIBLE);
                }
                updatePageControls();
            }

            @Override
            public void onFailure(Call<EmployeeListResponse> call, Throwable error) {
                if (call != staffCall || call.isCanceled()) return;
                staffCall = null;
                loading = false;
                showStaffError(getString(R.string.staff_connection_error));
            }
        });
    }

    private void showStaffError(String message) {
        listMessage.setText(message);
        listMessage.setVisibility(View.VISIBLE);
        updatePageControls();
    }

    private void updatePageControls() {
        progress.setVisibility(loading ? View.VISIBLE : View.GONE);
        previousButton.setEnabled(!loading && pagination != null && query.page > 1);
        nextButton.setEnabled(!loading && pagination != null && query.page < pagination.getTotalPages());
        if (loading) pageInfo.setText(getString(R.string.staff_page_loading, query.page));
        else if (pagination != null) pageInfo.setText(getString(R.string.staff_page_info,
                query.page, pagination.getTotalPages(), pagination.getTotal()));
        else pageInfo.setText(getString(R.string.staff_page_unavailable, query.page));
    }

    private void loadDepartments() {
        if (departmentCall != null) return;
        departmentSpinner.setEnabled(false);
        retryDepartments.setVisibility(View.GONE);
        departmentMessage.setText(R.string.staff_departments_loading);
        departmentMessage.setVisibility(View.VISIBLE);
        departmentCall = api.getDepartments();
        departmentCall.enqueue(new Callback<DepartmentListResponse>() {
            @Override
            public void onResponse(Call<DepartmentListResponse> call, Response<DepartmentListResponse> response) {
                if (call != departmentCall || call.isCanceled()) return;
                departmentCall = null;
                DepartmentListResponse body = response.body();
                if (!response.isSuccessful() || body == null || !body.isSuccess() || body.getData() == null) {
                    showDepartmentError();
                    return;
                }
                List<DepartmentListResponse.Item> departments = body.getData();
                for (DepartmentListResponse.Item item : departments) {
                    if (item == null || item.getId() <= 0 || item.getName() == null) {
                        showDepartmentError();
                        return;
                    }
                }
                departmentAdapter.clear();
                departmentAdapter.add(new DepartmentListResponse.Item(0, getString(R.string.staff_all_departments)));
                departmentAdapter.addAll(departments);
                int selectedPosition = 0;
                for (int i = 1; i < departmentAdapter.getCount(); i++) {
                    if (departmentAdapter.getItem(i).getId() == query.departmentId) selectedPosition = i;
                }
                DepartmentListResponse.Item selected = departmentAdapter.getItem(selectedPosition);
                departmentName = selected.getName();
                departmentSpinner.setSelection(selectedPosition);
                departmentSpinner.setEnabled(true);
                departmentMessage.setText(R.string.staff_departments_empty);
                departmentMessage.setVisibility(departments.isEmpty() ? View.VISIBLE : View.GONE);
                // Phong ban da bi xoa: tro ve tat ca phong ban va trang dau.
                changeQuery(query.withDepartment(selected.getId()), false);
            }

            @Override
            public void onFailure(Call<DepartmentListResponse> call, Throwable error) {
                if (call != departmentCall || call.isCanceled()) return;
                departmentCall = null;
                showDepartmentError();
            }
        });
    }

    private void showDepartmentError() {
        // Giu lua chon da co; van co the chon lai "Tat ca" neu API phong ban loi.
        departmentSpinner.setEnabled(true);
        departmentMessage.setText(R.string.staff_departments_error);
        departmentMessage.setVisibility(View.VISIBLE);
        retryDepartments.setVisibility(View.VISIBLE);
    }
}
