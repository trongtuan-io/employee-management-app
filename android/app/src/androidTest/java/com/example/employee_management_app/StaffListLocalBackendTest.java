package com.example.employee_management_app;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.SystemClock;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

import retrofit2.Response;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

// Opt-in read-only smoke test against the configured backend, with no test transport.
@RunWith(AndroidJUnit4.class)
public class StaffListLocalBackendTest {
    @Test public void screenUsesLocalDepartmentsSearchAndSort() throws Exception {
        assumeTrue("Enable with -Pandroid.testInstrumentationRunnerArguments.staffLiveApi=true",
                "true".equals(InstrumentationRegistry.getArguments().getString("staffLiveApi")));
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        EmployeeApi api = ApiClient.getClient(context).create(EmployeeApi.class);
        Response<EmployeeListResponse> expectedResponse = api.getEmployees("", null, 1, 20, "name_asc").execute();
        assertTrue(expectedResponse.isSuccessful());
        EmployeeListResponse expected = expectedResponse.body();
        assertNotNull(expected);
        assertTrue(expected.isSuccess());
        assertNotNull(expected.getPagination());
        assertTrue(expected.getPagination().isValid());
        DepartmentListResponse departments = api.getDepartments().execute().body();
        assertNotNull(departments);
        assertTrue(departments.isSuccess());

        try (ActivityScenario<StaffListActivity> scenario = ActivityScenario.launch(StaffListActivity.class)) {
            await(scenario, a -> pageMatches(a, expected)
                    && a.findViewById(R.id.spStaffDepartment).isEnabled());
            scenario.onActivity(a -> {
                assertEquals(departments.getData().size() + 1,
                        ((Spinner) a.findViewById(R.id.spStaffDepartment)).getCount());
                assertRows(a, expected);
            });
            screenshot(context, "staff-live-all.png");
            if (!departments.getData().isEmpty()) {
                DepartmentListResponse.Item department = departments.getData().get(0);
                EmployeeListResponse filtered = api.getEmployees("", department.getId(), 1, 20, "code_desc").execute().body();
                assertNotNull(filtered);
                scenario.onActivity(a -> {
                    ((Spinner) a.findViewById(R.id.spStaffDepartment)).setSelection(1);
                    ((Spinner) a.findViewById(R.id.spStaffSort)).setSelection(3);
                });
                await(scenario, a -> pageMatches(a, filtered) && rowsMatch(a, filtered));
                if (!filtered.getData().isEmpty()) {
                    String keyword = filtered.getData().get(0).getUsername();
                    EmployeeListResponse searched = api.getEmployees(keyword, department.getId(), 1, 20, "code_desc").execute().body();
                    assertNotNull(searched);
                    scenario.onActivity(a -> ((EditText) a.findViewById(R.id.edtSearchEmployee)).setText(keyword));
                    await(scenario, a -> pageMatches(a, searched) && rowsMatch(a, searched));
                    screenshot(context, "staff-live-filtered.png");
                }
            }
        }
    }

    private static boolean pageMatches(StaffListActivity activity, EmployeeListResponse expected) {
        EmployeeListResponse.Pagination page = expected.getPagination();
        return ((TextView) activity.findViewById(R.id.tvStaffPage)).getText().toString()
                .equals(activity.getString(R.string.staff_page_info, page.getPage(), page.getTotalPages(), page.getTotal()));
    }

    private static boolean rowsMatch(StaffListActivity activity, EmployeeListResponse expected) {
        ListView list = activity.findViewById(R.id.lvStaff);
        if (list.getCount() != expected.getData().size()) return false;
        for (int i = 0; i < list.getCount(); i++) {
            if (((EmployeeListResponse.Item) list.getItemAtPosition(i)).getId() != expected.getData().get(i).getId()) return false;
        }
        return true;
    }

    private static void assertRows(StaffListActivity activity, EmployeeListResponse expected) {
        assertTrue(rowsMatch(activity, expected));
    }

    private static void await(ActivityScenario<StaffListActivity> scenario, Predicate<StaffListActivity> condition) {
        long deadline = SystemClock.uptimeMillis() + 15000;
        AtomicBoolean done = new AtomicBoolean();
        while (SystemClock.uptimeMillis() < deadline) {
            scenario.onActivity(a -> done.set(condition.test(a)));
            if (done.get()) return;
            SystemClock.sleep(50);
        }
        fail("Timed out waiting for the local backend");
    }

    private static void screenshot(Context context, String name) throws Exception {
        Bitmap bitmap = InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
        assertNotNull(bitmap);
        try (FileOutputStream stream = new FileOutputStream(new File(context.getExternalFilesDir(null), name))) {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream));
        } finally {
            bitmap.recycle();
        }
    }
}
