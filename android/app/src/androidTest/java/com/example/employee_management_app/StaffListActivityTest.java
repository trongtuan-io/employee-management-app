package com.example.employee_management_app;

import android.os.SystemClock;
import android.view.View;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.ResponseBody;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class StaffListActivityTest {
    private ActivityScenario<StaffListActivity> scenario;
    private Retrofit originalRetrofit;
    private Field retrofitField;
    private volatile boolean departmentError;
    private volatile boolean networkError;
    private volatile boolean emptyDepartments;
    private volatile boolean missingPagination;
    private final AtomicInteger slowRequests = new AtomicInteger();

    @Before public void setUp() throws Exception {
        // Test-only transport. No changes to ApiClient, credentials or local backend configuration.
        retrofitField = ApiClient.class.getDeclaredField("retrofit");
        retrofitField.setAccessible(true);
        originalRetrofit = (Retrofit) retrofitField.get(null);
        OkHttpClient client = new OkHttpClient.Builder().retryOnConnectionFailure(false)
                .addInterceptor(chain -> fakeResponse(chain.request())).build();
        retrofitField.set(null, new Retrofit.Builder().baseUrl("http://127.0.0.1/")
                .client(client).addConverterFactory(GsonConverterFactory.create()).build());
        scenario = ActivityScenario.launch(StaffListActivity.class);
        await(a -> page(a, 1, 3, 45) && a.findViewById(R.id.spStaffDepartment).isEnabled());
    }

    @After public void tearDown() throws Exception {
        if (scenario != null) scenario.close();
        if (retrofitField != null) retrofitField.set(null, originalRetrofit);
    }

    @Test public void pagingReplacesRowsAndStopsAtBothEnds() {
        List<Integer> first = ids();
        assertEquals(20, first.size());
        scenario.onActivity(a -> {
            assertFalse(a.findViewById(R.id.btnPreviousPage).isEnabled());
            a.findViewById(R.id.btnNextPage).performClick();
            a.findViewById(R.id.btnNextPage).performClick();
        });
        await(a -> page(a, 2, 3, 45));
        List<Integer> second = ids();
        assertEquals(20, second.size());
        assertTrue(java.util.Collections.disjoint(first, second));
        click(R.id.btnNextPage);
        await(a -> page(a, 3, 3, 45));
        assertEquals(5, ids().size());
        scenario.onActivity(a -> assertFalse(a.findViewById(R.id.btnNextPage).isEnabled()));
        click(R.id.btnPreviousPage);
        await(a -> page(a, 2, 3, 45));
        assertEquals(second, ids());
    }

    @Test public void searchDepartmentAndSortComposeAcrossPagesAndRecreation() {
        search("Employee");
        select(R.id.spStaffDepartment, 1); // HR has 25 employees.
        await(a -> page(a, 1, 2, 25));
        click(R.id.btnNextPage);
        await(a -> page(a, 2, 2, 25));
        scenario.recreate();
        await(a -> page(a, 2, 2, 25) && a.findViewById(R.id.spStaffDepartment).isEnabled());
        scenario.onActivity(a -> {
            assertEquals("Employee", ((EditText) a.findViewById(R.id.edtSearchEmployee)).getText().toString());
            assertEquals(1, ((Spinner) a.findViewById(R.id.spStaffDepartment)).getSelectedItemPosition());
        });
        select(R.id.spStaffSort, 1);
        await(a -> page(a, 1, 2, 25) && firstId(a) == 25);
        List<Integer> first = ids();
        click(R.id.btnNextPage);
        await(a -> page(a, 2, 2, 25));
        assertTrue(java.util.Collections.disjoint(first, ids()));
        select(R.id.spStaffSort, 2);
        await(a -> page(a, 1, 2, 25) && firstId(a) == 1);
        select(R.id.spStaffSort, 3);
        await(a -> page(a, 1, 2, 25) && firstId(a) == 25);
        select(R.id.spStaffDepartment, 2); // IT.
        await(a -> page(a, 1, 1, 20) && firstId(a) == 45);
        select(R.id.spStaffDepartment, 0);
        await(a -> page(a, 1, 3, 45) && firstId(a) == 45);
        click(R.id.btnNextPage);
        await(a -> page(a, 2, 3, 45));
        search("NV002");
        await(a -> page(a, 1, 1, 1) && firstId(a) == 2);
        scenario.onActivity(a -> assertEquals(3,
                ((Spinner) a.findViewById(R.id.spStaffSort)).getSelectedItemPosition()));
    }

    @Test public void lateResponseCannotOverwriteNewSearchAndEmptyResultIsVisible() {
        search("slow");
        long deadline = SystemClock.uptimeMillis() + 5000;
        while (slowRequests.get() == 0 && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(20);
        assertTrue(slowRequests.get() > 0);
        search("NV002");
        await(a -> page(a, 1, 1, 1) && firstId(a) == 2);
        SystemClock.sleep(900); // Let the older, deliberately delayed response finish.
        assertEquals(java.util.Collections.singletonList(2), ids());
        search("no-such-employee");
        await(a -> page(a, 1, 1, 0));
        assertTrue(ids().isEmpty());
        scenario.onActivity(a -> {
            assertEquals(View.VISIBLE, a.findViewById(R.id.tvStaffMessage).getVisibility());
            assertFalse(a.findViewById(R.id.btnPreviousPage).isEnabled());
            assertFalse(a.findViewById(R.id.btnNextPage).isEnabled());
        });
    }

    @Test public void apiAndNetworkFailuresCanBeRetriedWithoutLosingFilters() {
        select(R.id.spStaffDepartment, 1);
        await(a -> page(a, 1, 2, 25));
        search("http-error");
        await(a -> text(a, R.id.tvStaffMessage).contains("HTTP 500"));
        networkError = true;
        search("Employee");
        await(a -> text(a, R.id.tvStaffMessage).contains("Kiểm tra kết nối"));
        assertTrue(ids().isEmpty());
        networkError = false;
        click(R.id.btnSearchEmployee);
        await(a -> page(a, 1, 2, 25));
        scenario.onActivity(a -> assertEquals(1,
                ((Spinner) a.findViewById(R.id.spStaffDepartment)).getSelectedItemPosition()));
        missingPagination = true;
        click(R.id.btnSearchEmployee);
        await(a -> text(a, R.id.tvStaffMessage).contains("phân trang hợp lệ"));
        missingPagination = false;
        click(R.id.btnSearchEmployee);
        await(a -> page(a, 1, 2, 25));
    }

    @Test public void failedDepartmentReloadPreservesSelectionAndSupportsRetryAndEmptyList() {
        select(R.id.spStaffDepartment, 1);
        await(a -> page(a, 1, 2, 25));
        departmentError = true;
        scenario.recreate();
        await(a -> page(a, 1, 2, 25)
                && a.findViewById(R.id.btnRetryDepartments).getVisibility() == View.VISIBLE);
        scenario.onActivity(a -> assertEquals(1,
                ((Spinner) a.findViewById(R.id.spStaffDepartment)).getSelectedItemPosition()));
        departmentError = false;
        click(R.id.btnRetryDepartments);
        await(a -> a.findViewById(R.id.spStaffDepartment).isEnabled()
                && ((Spinner) a.findViewById(R.id.spStaffDepartment)).getCount() == 3);
        emptyDepartments = true;
        scenario.recreate();
        await(a -> page(a, 1, 3, 45)
                && text(a, R.id.tvDepartmentMessage).equals(a.getString(R.string.staff_departments_empty)));
        scenario.onActivity(a -> assertEquals(0,
                ((Spinner) a.findViewById(R.id.spStaffDepartment)).getSelectedItemPosition()));
    }

    private void search(String value) {
        scenario.onActivity(a -> ((EditText) a.findViewById(R.id.edtSearchEmployee)).setText(value));
    }

    private void select(int id, int position) {
        scenario.onActivity(a -> ((Spinner) a.findViewById(id)).setSelection(position));
    }

    private void click(int id) {
        scenario.onActivity(a -> a.findViewById(id).performClick());
    }

    private boolean page(StaffListActivity activity, int page, int pages, int total) {
        return text(activity, R.id.tvStaffPage).equals(
                activity.getString(R.string.staff_page_info, page, pages, total));
    }

    private String text(StaffListActivity activity, int id) {
        return ((TextView) activity.findViewById(id)).getText().toString();
    }

    private int firstId(StaffListActivity activity) {
        ListView list = activity.findViewById(R.id.lvStaff);
        return list.getCount() == 0 ? -1 : ((EmployeeListResponse.Item) list.getItemAtPosition(0)).getId();
    }

    private List<Integer> ids() {
        List<Integer> result = new ArrayList<>();
        scenario.onActivity(a -> {
            ListView list = a.findViewById(R.id.lvStaff);
            for (int i = 0; i < list.getCount(); i++) {
                result.add(((EmployeeListResponse.Item) list.getItemAtPosition(i)).getId());
            }
        });
        assertEquals(result.size(), new HashSet<>(result).size());
        return result;
    }

    private void await(Predicate<StaffListActivity> condition) {
        long deadline = SystemClock.uptimeMillis() + 10000;
        AtomicBoolean done = new AtomicBoolean();
        while (SystemClock.uptimeMillis() < deadline) {
            scenario.onActivity(a -> done.set(condition.test(a)));
            if (done.get()) return;
            SystemClock.sleep(40);
        }
        fail("Timed out waiting for staff screen state");
    }

    private okhttp3.Response fakeResponse(Request request) throws IOException {
        if (networkError) throw new IOException("Simulated offline");
        HttpUrl url = request.url();
        int code = 200;
        JSONObject body = new JSONObject();
        try {
            if ("departments".equals(url.queryParameter("action"))) {
                code = departmentError ? 500 : 200;
                body.put("success", !departmentError);
                body.put("data", emptyDepartments ? new JSONArray()
                        : new JSONArray("[{'id':1,'name':'HR'},{'id':2,'name':'IT'}]"));
            } else {
                String search = url.queryParameter("search");
                if ("slow".equals(search)) {
                    slowRequests.incrementAndGet();
                    SystemClock.sleep(900);
                }
                if ("http-error".equals(search)) code = 500;
                int department = Integer.parseInt(url.queryParameter("dept_id") == null
                        ? "0" : url.queryParameter("dept_id"));
                int limit = Integer.parseInt(url.queryParameter("limit"));
                List<Integer> rows = new ArrayList<>();
                for (int id = 1; id <= 45; id++) {
                    if (department != 0 && department != (id <= 25 ? 1 : 2)) continue;
                    if (search != null && !name(id).contains(search) && !username(id).contains(search)) continue;
                    rows.add(id);
                }
                String sort = url.queryParameter("sort");
                Comparator<Integer> order = sort.startsWith("name")
                        ? Comparator.comparing(this::name).thenComparingInt(i -> i)
                        : Comparator.comparing(this::username).thenComparingInt(i -> i);
                if (sort.endsWith("desc")) order = order.reversed();
                rows.sort(order);
                int totalPages = Math.max(1, (rows.size() + limit - 1) / limit);
                int page = Math.min(totalPages, Integer.parseInt(url.queryParameter("page")));
                JSONArray data = new JSONArray();
                for (int i = (page - 1) * limit; i < Math.min(rows.size(), page * limit); i++) {
                    int id = rows.get(i);
                    data.put(new JSONObject().put("id", id).put("full_name", name(id))
                            .put("username", username(id)).put("status", 1));
                }
                body.put("success", code == 200).put("data", data);
                if (!missingPagination) body.put("pagination", new JSONObject().put("page", page)
                        .put("limit", limit).put("total", rows.size()).put("total_pages", totalPages));
            }
        } catch (Exception e) {
            throw new IOException(e);
        }
        return new okhttp3.Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                .code(code).message(code == 200 ? "OK" : "Server error")
                .body(ResponseBody.create(MediaType.parse("application/json"), body.toString())).build();
    }

    private String name(int id) { return String.format(Locale.US, "Employee %02d", (id - 1) / 3); }
    private String username(int id) { return String.format(Locale.US, "NV%03d", id); }
}
