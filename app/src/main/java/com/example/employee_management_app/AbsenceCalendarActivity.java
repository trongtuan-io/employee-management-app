package com.example.employee_management_app;

import android.content.Context;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.GridLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

// Lich vang theo thang: luoi thang to mau ngay co nguoi nghi da duyet +
// danh sach chi tiet ai vang, phong nao, loai nghi gi.
public class AbsenceCalendarActivity extends AppCompatActivity {
    private EmployeeApi api;
    private final List<LeaveCalendarResponse.Item> items = new ArrayList<>();
    private final Map<Integer, Integer> absentByDay = new HashMap<>();
    private AbsenceAdapter adapter;
    private TextView tvMonth, tvScope, tvEmpty;
    private GridLayout grid;
    private int curMonth, curYear;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_absence_calendar);
        api = ApiClient.getClient(this).create(EmployeeApi.class);
        tvMonth = findViewById(R.id.tvAbsenceMonth);
        tvScope = findViewById(R.id.tvAbsenceScope);
        tvEmpty = findViewById(R.id.tvAbsenceEmpty);
        grid = findViewById(R.id.gridAbsence);
        ListView lv = findViewById(R.id.lvAbsence);
        adapter = new AbsenceAdapter(this);
        lv.setAdapter(adapter);

        Calendar now = Calendar.getInstance();
        curMonth = now.get(Calendar.MONTH) + 1;
        curYear = now.get(Calendar.YEAR);

        findViewById(R.id.btnAbsenceBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnAbsencePrev).setOnClickListener(v -> shiftMonth(-1));
        findViewById(R.id.btnAbsenceNext).setOnClickListener(v -> shiftMonth(1));
        load();
    }

    private void shiftMonth(int delta) {
        curMonth += delta;
        if (curMonth < 1) { curMonth = 12; curYear--; }
        if (curMonth > 12) { curMonth = 1; curYear++; }
        load();
    }

    private void load() {
        tvMonth.setText("Tháng " + curMonth + "/" + curYear);
        api.getLeaveCalendar(curMonth, curYear).enqueue(new Callback<LeaveCalendarResponse>() {
            @Override
            public void onResponse(Call<LeaveCalendarResponse> c, Response<LeaveCalendarResponse> r) {
                if (r.isSuccessful() && r.body() != null && r.body().getData() != null) {
                    LeaveCalendarResponse.Data d = r.body().getData();
                    items.clear();
                    if (d.getItems() != null) items.addAll(d.getItems());
                    tvScope.setText("company".equals(d.getScope())
                            ? "Phạm vi: toàn công ty · Lưới tháng tô màu theo đơn ĐÃ duyệt"
                            : "Phạm vi: phòng của bạn · Lưới tháng tô màu theo đơn ĐÃ duyệt");
                    buildDayCounts();
                    renderGrid();
                    adapter.notifyDataSetChanged();
                    tvEmpty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
                } else if (r.code() != 401) {
                    Toast.makeText(AbsenceCalendarActivity.this, ApiError.message(r, "Không tải được lịch vắng"), Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<LeaveCalendarResponse> c, Throwable t) {
                Toast.makeText(AbsenceCalendarActivity.this, "Không tải được lịch vắng", Toast.LENGTH_SHORT).show();
            }
        });
    }

    // Dem so nguoi vang DA DUYET theo tung ngay trong thang dang xem.
    private void buildDayCounts() {
        absentByDay.clear();
        Calendar cal = Calendar.getInstance();
        cal.set(curYear, curMonth - 1, 1);
        int daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH);
        String monthStart = String.format(Locale.US, "%04d-%02d-01", curYear, curMonth);
        String monthEnd = String.format(Locale.US, "%04d-%02d-%02d", curYear, curMonth, daysInMonth);
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        for (LeaveCalendarResponse.Item it : items) {
            if (!"approved".equals(it.getStatus())) continue;
            if (it.getStartDate() == null || it.getEndDate() == null) continue;
            String s = it.getStartDate().compareTo(monthStart) < 0 ? monthStart : it.getStartDate();
            String e = it.getEndDate().compareTo(monthEnd) > 0 ? monthEnd : it.getEndDate();
            try {
                Calendar cur = Calendar.getInstance();
                cur.setTime(fmt.parse(s));
                Calendar last = Calendar.getInstance();
                last.setTime(fmt.parse(e));
                while (!cur.after(last)) {
                    int day = cur.get(Calendar.DAY_OF_MONTH);
                    absentByDay.put(day, (absentByDay.get(day) == null ? 0 : absentByDay.get(day)) + 1);
                    cur.add(Calendar.DAY_OF_MONTH, 1);
                }
            } catch (Exception ignored) { }
        }
    }

    private void renderGrid() {
        grid.removeAllViews();
        String[] dowNames = {"T2", "T3", "T4", "T5", "T6", "T7", "CN"};
        for (int i = 0; i < 7; i++) {
            grid.addView(headerCell(dowNames[i]), cellParams(i));
        }
        Calendar cal = Calendar.getInstance();
        cal.set(curYear, curMonth - 1, 1);
        int daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH);
        int dow = cal.get(Calendar.DAY_OF_WEEK); // 1=CN, 2=T2 ...
        int offset = (dow + 5) % 7; // so o trong truoc ngay 1 (tuan bat dau tu T2)
        for (int i = 0; i < offset; i++) {
            grid.addView(new View(this), cellParams(i));
        }
        Calendar today = Calendar.getInstance();
        for (int day = 1; day <= daysInMonth; day++) {
            int col = (offset + day - 1) % 7;
            TextView tv = new TextView(this);
            Integer count = absentByDay.get(day);
            tv.setText(count != null ? day + "\n" + count + " vắng" : String.valueOf(day));
            tv.setGravity(Gravity.CENTER);
            tv.setTextSize(12);
            tv.setPadding(2, 6, 2, 6);
            tv.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
            if (count != null) {
                tv.setBackgroundResource(R.drawable.bg_badge_pending);
                tv.setTypeface(null, Typeface.BOLD);
            }
            if (day == today.get(Calendar.DAY_OF_MONTH)
                    && curMonth == today.get(Calendar.MONTH) + 1
                    && curYear == today.get(Calendar.YEAR)) {
                tv.setTextColor(ContextCompat.getColor(this, R.color.primary));
                tv.setTypeface(null, Typeface.BOLD);
            }
            grid.addView(tv, cellParams(col));
        }
    }

    private TextView headerCell(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setGravity(Gravity.CENTER);
        tv.setTextSize(12);
        tv.setPadding(2, 4, 2, 4);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
        return tv;
    }

    private GridLayout.LayoutParams cellParams(int col) {
        GridLayout.LayoutParams p = new GridLayout.LayoutParams();
        p.columnSpec = GridLayout.spec(col, 1f);
        p.width = 0;
        p.height = GridLayout.LayoutParams.WRAP_CONTENT;
        p.setMargins(2, 2, 2, 2);
        return p;
    }

    private class AbsenceAdapter extends BaseAdapter {
        private final LayoutInflater inflater;

        AbsenceAdapter(Context ctx) { inflater = LayoutInflater.from(ctx); }

        @Override public int getCount() { return items.size(); }
        @Override public Object getItem(int position) { return items.get(position); }
        @Override public long getItemId(int position) { return items.get(position).getId(); }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View v = convertView;
            if (v == null) v = inflater.inflate(R.layout.item_absence, parent, false);
            LeaveCalendarResponse.Item item = items.get(position);
            TextView tvName = v.findViewById(R.id.tvAbsName);
            TextView tvBadge = v.findViewById(R.id.tvAbsBadge);
            TextView tvDetail = v.findViewById(R.id.tvAbsDetail);
            tvName.setText(item.getFullName());
            String detail = "";
            if (item.getDeptName() != null && !item.getDeptName().isEmpty()) detail += "Phòng " + item.getDeptName() + " · ";
            detail += LeaveActivity.leaveTypeLabel(item.getLeaveType()) + " · "
                    + LeaveActivity.formatDate(item.getStartDate());
            if (item.getEndDate() != null && !item.getEndDate().equals(item.getStartDate())) {
                detail += " → " + LeaveActivity.formatDate(item.getEndDate());
            }
            tvDetail.setText(detail);
            tvBadge.setText(LeaveActivity.statusLabel(item.getStatus()));
            if ("approved".equals(item.getStatus())) {
                tvBadge.setBackgroundResource(R.drawable.bg_badge_approved);
                tvBadge.setTextColor(ContextCompat.getColor(AbsenceCalendarActivity.this, R.color.success));
            } else {
                tvBadge.setBackgroundResource(R.drawable.bg_badge_pending);
                tvBadge.setTextColor(ContextCompat.getColor(AbsenceCalendarActivity.this, R.color.warning));
            }
            return v;
        }
    }
}
