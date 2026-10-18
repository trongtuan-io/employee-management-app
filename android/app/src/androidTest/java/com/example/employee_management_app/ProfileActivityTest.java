package com.example.employee_management_app;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.ResponseBody;
import okio.Buffer;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import static org.junit.Assert.*;

/** Instrumented UI tests use a test transport and never write to the live backend. */
@RunWith(AndroidJUnit4.class)
public class ProfileActivityTest {
    private Context context;
    private ActivityScenario<ProfileActivity> scenario;
    private Field retrofitField;
    private Retrofit originalRetrofit;
    private SharedPreferences preferences;
    private boolean hadUserId, hadRole;
    private int originalUserId;
    private String originalRole;
    private volatile boolean offline, detailError, saveError;
    private volatile JSONObject savedBody;
    private volatile String name = "Nguyễn An";
    private volatile String email = "an@example.com";
    private final AtomicInteger saves = new AtomicInteger();

    @Before public void setUp() throws Exception {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        preferences = context.getSharedPreferences("EmployeeAppPrefs", Context.MODE_PRIVATE);
        hadUserId = preferences.contains("user_id");
        hadRole = preferences.contains("user_role");
        originalUserId = preferences.getInt("user_id", -1);
        originalRole = preferences.getString("user_role", "staff");
        retrofitField = ApiClient.class.getDeclaredField("retrofit");
        retrofitField.setAccessible(true);
        originalRetrofit = (Retrofit) retrofitField.get(null);
        OkHttpClient client = new OkHttpClient.Builder().retryOnConnectionFailure(false)
                .addInterceptor(chain -> respond(chain.request())).build();
        retrofitField.set(null, new Retrofit.Builder().baseUrl("http://127.0.0.1/")
                .client(client).addConverterFactory(GsonConverterFactory.create()).build());
    }

    @After public void tearDown() throws Exception {
        if (scenario != null) scenario.close();
        if (retrofitField != null) retrofitField.set(null, originalRetrofit);
        SharedPreferences.Editor editor = preferences.edit();
        if (hadUserId) editor.putInt("user_id", originalUserId); else editor.remove("user_id");
        if (hadRole) editor.putString("user_role", originalRole); else editor.remove("user_role");
        assertTrue(editor.commit());
    }

    private void launch(boolean admin) {
        TokenManager.getInstance(context).saveUserId(admin ? 8 : 42);
        TokenManager.getInstance(context).saveRole(admin ? "manager" : "staff");
        Intent intent = new Intent(context, ProfileActivity.class);
        if (admin) intent.putExtra("EMPLOYEE_ID", 42);
        scenario = ActivityScenario.launch(intent);
    }

    @Test public void staffSeesIdentityAndMissingFieldsWithoutGainingEditRights() {
        launch(false);
        await(a -> a.findViewById(R.id.btnSaveProfile).isEnabled());
        scenario.onActivity(a -> {
            assertTrue(text(a, R.id.tvProfileCode).contains("NV042"));
            assertEquals("Chưa cập nhật", text(a, R.id.tvProfileDepartment));
            assertEquals("Chưa cập nhật", text(a, R.id.tvProfilePosition));
            assertEquals("", text(a, R.id.etProfilePhone));
            assertTrue(text(a, R.id.tvProfileStatus).contains("Đang hoạt động"));
            assertFalse(a.findViewById(R.id.etProfileEmail).isEnabled());
            assertFalse(a.findViewById(R.id.etProfilePhone).isEnabled());
            assertEquals(View.GONE, a.findViewById(R.id.layoutLock).getVisibility());
        });
    }

    @Test public void httpAndNetworkFailuresDisableSavingAndCanBeRetried() {
        detailError = true;
        launch(false);
        await(a -> text(a, R.id.tvProfileMessage).contains("Không tìm thấy nhân viên"));
        scenario.onActivity(a -> assertFalse(a.findViewById(R.id.btnSaveProfile).isEnabled()));
        detailError = false;
        offline = true;
        click(R.id.btnRetryProfile);
        await(a -> text(a, R.id.tvProfileMessage).contains("Kiểm tra kết nối"));
        offline = false;
        click(R.id.btnRetryProfile);
        await(a -> a.findViewById(R.id.btnSaveProfile).isEnabled());
    }

    @Test public void invalidFieldsNeverReachApiAndFailedSaveKeepsDraft() {
        launch(true);
        await(a -> a.findViewById(R.id.btnSaveProfile).isEnabled());
        scenario.onActivity(a -> {
            edit(a, R.id.etProfileName, "  ");
            edit(a, R.id.etProfileEmail, "bad-email");
            edit(a, R.id.etProfilePhone, "abc");
            a.findViewById(R.id.btnSaveProfile).performClick();
            assertNotNull(((EditText) a.findViewById(R.id.etProfileName)).getError());
            assertNotNull(((EditText) a.findViewById(R.id.etProfileEmail)).getError());
            assertNotNull(((EditText) a.findViewById(R.id.etProfilePhone)).getError());
        });
        assertEquals(0, saves.get());
        saveError = true;
        scenario.onActivity(a -> {
            edit(a, R.id.etProfileName, "Tên mới");
            edit(a, R.id.etProfileEmail, "duplicate@example.com");
            edit(a, R.id.etProfilePhone, "+84901234567");
            a.findViewById(R.id.btnSaveProfile).performClick();
        });
        await(a -> text(a, R.id.tvProfileMessage).contains("Email đã được sử dụng"));
        scenario.onActivity(a -> {
            assertEquals("Tên mới", text(a, R.id.etProfileName));
            assertTrue(a.findViewById(R.id.btnSaveProfile).isEnabled());
        });
    }

    @Test public void unchangedLegacyContactsAreOmittedAndReopenedProfileLoadsNewName() throws Exception {
        email = "legacy-invalid-email";
        launch(true);
        await(a -> a.findViewById(R.id.btnSaveProfile).isEnabled());
        scenario.onActivity(a -> {
            edit(a, R.id.etProfileName, "Tên đã cập nhật");
            a.findViewById(R.id.btnSaveProfile).performClick();
            a.findViewById(R.id.btnSaveProfile).performClick();
        });
        long deadline = SystemClock.uptimeMillis() + 5000;
        while (savedBody == null && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(25);
        assertNotNull(savedBody);
        assertFalse(savedBody.has("email"));
        assertFalse(savedBody.has("phone"));
        assertFalse(savedBody.has("base_salary"));
        assertFalse(savedBody.has("role_id"));
        assertFalse(savedBody.has("status"));
        assertEquals(1, saves.get());
        scenario.close();
        launch(true);
        await(a -> a.findViewById(R.id.btnSaveProfile).isEnabled()
                && "Tên đã cập nhật".equals(text(a, R.id.etProfileName)));
    }

    private void click(int id) { scenario.onActivity(a -> a.findViewById(id).performClick()); }

    private void edit(ProfileActivity activity, int id, String value) {
        ((EditText) activity.findViewById(id)).setText(value);
    }

    private String text(ProfileActivity activity, int id) {
        return ((TextView) activity.findViewById(id)).getText().toString();
    }

    private void await(Predicate<ProfileActivity> condition) {
        long deadline = SystemClock.uptimeMillis() + 10000;
        AtomicBoolean done = new AtomicBoolean();
        while (SystemClock.uptimeMillis() < deadline) {
            scenario.onActivity(a -> done.set(condition.test(a)));
            if (done.get()) return;
            SystemClock.sleep(40);
        }
        fail("Timed out waiting for profile state");
    }

    private okhttp3.Response respond(Request request) throws IOException {
        if (offline) throw new IOException("Test offline");
        int status = 200;
        JSONObject body = new JSONObject();
        try {
            String action = request.url().queryParameter("action");
            if ("departments".equals(action)) {
                body.put("success", true).put("data", new org.json.JSONArray()
                        .put(new JSONObject().put("id", 1).put("name", "IT")));
            } else if ("positions".equals(action)) {
                body.put("success", true).put("data", new org.json.JSONArray()
                        .put(new JSONObject().put("id", 1).put("title", "Developer")));
            } else if ("detail".equals(action)) {
                status = detailError ? 404 : 200;
                body.put("success", !detailError).put("error", "Không tìm thấy nhân viên");
                if (!detailError) body.put("data", new JSONObject().put("id", 42).put("username", "NV042")
                        .put("full_name", name).put("email", email).put("phone", JSONObject.NULL)
                        .put("department_id", JSONObject.NULL).put("dept_name", JSONObject.NULL)
                        .put("position_id", JSONObject.NULL).put("position_name", JSONObject.NULL)
                        .put("status", 1).put("avatar_url", JSONObject.NULL));
            } else if ("update".equals(action)) {
                saves.incrementAndGet();
                Buffer buffer = new Buffer();
                request.body().writeTo(buffer);
                JSONObject payload = new JSONObject(buffer.readUtf8());
                status = saveError ? 409 : 200;
                if (!saveError) name = payload.getString("full_name");
                savedBody = payload;
                body.put("success", !saveError).put("error", "Email đã được sử dụng");
            } else {
                throw new IOException("Unexpected profile request: " + action);
            }
        } catch (Exception e) { throw new IOException(e); }
        return new okhttp3.Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                .code(status).message("Test response")
                .body(ResponseBody.create(MediaType.parse("application/json"), body.toString())).build();
    }
}
