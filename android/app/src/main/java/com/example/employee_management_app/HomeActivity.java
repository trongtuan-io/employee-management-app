package com.example.employee_management_app;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

// Trang chu sau dang nhap: loi chao + the cham cong hom nay + bang tin cong ty.
public class HomeActivity extends AppCompatActivity {
    private EmployeeApi api;
    private int myId;
    private TextView tvHomeName, tvHomeRole, tvAttStatus, tvAttDetail, tvFeedStatus;
    private Button btnHomeCheckIn, btnHomeCheckOut;
    private final List<AnnouncementResponse.Item> news = new ArrayList<>();
    private NewsAdapter newsAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        api = ApiClient.getClient(this).create(EmployeeApi.class);
        myId = TokenManager.getInstance(this).getUserId();

        tvHomeName = findViewById(R.id.tvHomeName);
        tvHomeRole = findViewById(R.id.tvHomeRole);
        tvAttStatus = findViewById(R.id.tvAttStatus);
        tvAttDetail = findViewById(R.id.tvAttDetail);
        tvFeedStatus = findViewById(R.id.tvFeedStatus);
        btnHomeCheckIn = findViewById(R.id.btnHomeCheckIn);
        btnHomeCheckOut = findViewById(R.id.btnHomeCheckOut);
        ListView lvNews = findViewById(R.id.lvNews);
        newsAdapter = new NewsAdapter();
        lvNews.setAdapter(newsAdapter);

        tvHomeRole.setText(roleToVietnamese(TokenManager.getInstance(this).getRole()));

        btnHomeCheckIn.setOnClickListener(v -> doAttendance(true));
        btnHomeCheckOut.setOnClickListener(v -> doAttendance(false));

        BottomNavHelper.setup(this, R.id.nav_home);
        loadProfile();
        loadToday();
        loadNews();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadToday();
    }

    private void loadProfile() {
        if (myId <= 0) return;
        api.getDetail(myId).enqueue(new Callback<EmployeeDetailResponse>() {
            @Override
            public void onResponse(Call<EmployeeDetailResponse> c, Response<EmployeeDetailResponse> r) {
                if (r.isSuccessful() && r.body() != null && r.body().getData() != null) {
                    tvHomeName.setText(r.body().getData().getFullName());
                }
            }

            @Override
            public void onFailure(Call<EmployeeDetailResponse> c, Throwable t) { }
        });
    }

    private void loadToday() {
        api.getAttendanceToday().enqueue(new Callback<AttendanceTodayResponse>() {
            @Override
            public void onResponse(Call<AttendanceTodayResponse> c, Response<AttendanceTodayResponse> r) {
                if (r.isSuccessful() && r.body() != null && r.body().getData() != null) {
                    renderToday(r.body().getData());
                }
            }

            @Override
            public void onFailure(Call<AttendanceTodayResponse> c, Throwable t) { }
        });
    }

    private void renderToday(AttendanceTodayResponse.Data d) {
        if (!d.isCheckedIn()) {
            tvAttStatus.setText("Chưa chấm công hôm nay");
            tvAttDetail.setText("Nhấn \"Chấm công vào\" khi bắt đầu ca làm việc.");
            btnHomeCheckIn.setEnabled(true);
            btnHomeCheckOut.setEnabled(false);
        } else if (!d.isCheckedOut()) {
            String late = "late".equals(d.getStatus()) ? " (đi muộn)" : "";
            tvAttStatus.setText("Đã vào lúc " + timeOnly(d.getCheckIn()) + late);
            tvAttDetail.setText("Đang trong ca làm việc. Nhớ chấm công ra khi kết thúc.");
            btnHomeCheckIn.setEnabled(false);
            btnHomeCheckOut.setEnabled(true);
        } else {
            tvAttStatus.setText("Đã hoàn thành hôm nay");
            tvAttDetail.setText("Vào " + timeOnly(d.getCheckIn()) + " · Ra " + timeOnly(d.getCheckOut()));
            btnHomeCheckIn.setEnabled(false);
            btnHomeCheckOut.setEnabled(false);
        }
    }

    private void doAttendance(boolean isIn) {
        Call<AttendanceResponse> call = isIn
                ? api.checkIn(new CheckinRequest(myId))
                : api.checkOut(new CheckinRequest(myId));
        call.enqueue(new Callback<AttendanceResponse>() {
            @Override
            public void onResponse(Call<AttendanceResponse> c, Response<AttendanceResponse> r) {
                if (r.isSuccessful() && r.body() != null && r.body().isSuccess()) {
                    Toast.makeText(HomeActivity.this,
                            isIn ? "Chấm công vào thành công" : "Chấm công ra thành công",
                            Toast.LENGTH_SHORT).show();
                    loadToday();
                } else {
                    Toast.makeText(HomeActivity.this,
                            isIn ? "Hôm nay đã check-in rồi" : "Chưa check-in hôm nay",
                            Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<AttendanceResponse> c, Throwable t) {
                Toast.makeText(HomeActivity.this, "Lỗi kết nối: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadNews() {
        api.getAnnouncements().enqueue(new Callback<AnnouncementResponse>() {
            @Override
            public void onResponse(Call<AnnouncementResponse> c, Response<AnnouncementResponse> r) {
                if (r.isSuccessful() && r.body() != null && r.body().getData() != null) {
                    news.clear();
                    news.addAll(r.body().getData());
                    newsAdapter.notifyDataSetChanged();
                    tvFeedStatus.setVisibility(news.isEmpty() ? View.VISIBLE : View.GONE);
                    if (news.isEmpty()) tvFeedStatus.setText("Chưa có thông báo nào.");
                } else {
                    tvFeedStatus.setVisibility(View.VISIBLE);
                    tvFeedStatus.setText("Không tải được bảng tin.");
                }
            }

            @Override
            public void onFailure(Call<AnnouncementResponse> c, Throwable t) {
                tvFeedStatus.setVisibility(View.VISIBLE);
                tvFeedStatus.setText("Không tải được bảng tin. Kiểm tra kết nối rồi mở lại Trang chủ.");
            }
        });
    }

    private String timeOnly(String datetime) {
        if (datetime == null) return "--:--";
        // "2026-11-07 08:02:11" -> "08:02"
        try {
            return datetime.substring(11, 16);
        } catch (Exception e) {
            return datetime;
        }
    }

    private String dateOnly(String datetime) {
        if (datetime == null) return "";
        try {
            return datetime.substring(8, 10) + "/" + datetime.substring(5, 7) + "/" + datetime.substring(0, 4);
        } catch (Exception e) {
            return datetime;
        }
    }

    private String roleToVietnamese(String role) {
        if (role == null) return "Nhân viên";
        switch (role.toLowerCase()) {
            case "admin": return "Quản trị viên";
            case "manager": return "Quản lý";
            case "staff": return "Nhân viên";
            default: return role;
        }
    }

    private class NewsAdapter extends BaseAdapter {
        @Override public int getCount() { return news.size(); }
        @Override public Object getItem(int position) { return news.get(position); }
        @Override public long getItemId(int position) { return news.get(position).getId(); }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View v = convertView;
            if (v == null) {
                v = LayoutInflater.from(HomeActivity.this)
                        .inflate(R.layout.item_announcement, parent, false);
            }
            AnnouncementResponse.Item item = news.get(position);
            TextView tvTitle = v.findViewById(R.id.tvNewsTitle);
            TextView tvMeta = v.findViewById(R.id.tvNewsMeta);
            TextView tvContent = v.findViewById(R.id.tvNewsContent);
            tvTitle.setText(item.getTitle());
            String author = (item.getAuthorName() != null && !item.getAuthorName().isEmpty())
                    ? item.getAuthorName() : "Ban quản trị";
            tvMeta.setText(dateOnly(item.getCreatedAt()) + " · " + author);
            tvContent.setText(item.getContent());
            return v;
        }
    }
}
