package com.example.employee_management_app;

import android.content.Intent;
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
    private TextView tvHomeName, tvHomeRole, tvAttStatus, tvAttDetail, tvFeedStatus, tvNotifyBadge;
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
        tvNotifyBadge = findViewById(R.id.tvNotifyBadge);
        findViewById(R.id.btnNotifyBell).setOnClickListener(v ->
                startActivity(new Intent(this, NotificationsActivity.class)));
        btnHomeCheckIn = findViewById(R.id.btnHomeCheckIn);
        btnHomeCheckOut = findViewById(R.id.btnHomeCheckOut);
        ListView lvNews = findViewById(R.id.lvNews);
        newsAdapter = new NewsAdapter();
        lvNews.setAdapter(newsAdapter);

        String myRole = TokenManager.getInstance(this).getRole();
        String myDept = TokenManager.getInstance(this).getDepartmentName();
        tvHomeRole.setText(roleToVietnamese(myRole)
                + (myDept != null && !myDept.isEmpty() ? " · " + deptToVietnamese(myDept) : ""));
        setupDeptSection(myRole, myDept);

        // Mac dinh khoa ca 2 nut cham cong; renderToday() se mo dung nut khi biet trang thai.
        btnHomeCheckIn.setEnabled(false);
        btnHomeCheckOut.setEnabled(false);
        btnHomeCheckIn.setOnClickListener(v -> doAttendance(true));
        btnHomeCheckOut.setOnClickListener(v -> doAttendance(false));

        BottomNavHelper.setup(this, R.id.nav_home);
        loadProfile();
        loadNews();
        // loadToday() de onResume goi (tranh goi trung 2 lan khi vua mo man).
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadToday();
        loadNotifyBadge();
    }

    // Badge so thong bao chua doc tren chuong o Trang chu (an khi = 0).
    private void loadNotifyBadge() {
        api.getNotifications().enqueue(new Callback<NotificationListResponse>() {
            @Override
            public void onResponse(Call<NotificationListResponse> c, Response<NotificationListResponse> r) {
                if (r.isSuccessful() && r.body() != null && r.body().getData() != null) {
                    int unread = r.body().getData().getUnread();
                    tvNotifyBadge.setVisibility(unread > 0 ? View.VISIBLE : View.GONE);
                    tvNotifyBadge.setText(unread > 99 ? "99+" : String.valueOf(unread));
                }
            }

            @Override
            public void onFailure(Call<NotificationListResponse> c, Throwable t) { }
        });
    }

    // Khoi nghiep vu theo phong: Trang chu chi hien khoi dung phong/vai tro cua
    // nguoi dang nhap (Ke toan thay khoi luong, Nhan su thay khoi ho so...).
    // Day chi la hien thi - API tu kiem tra quyen that o moi thao tac.
    private void setupDeptSection(String role, String dept) {
        View section = findViewById(R.id.layoutDeptSection);
        TextView tvTitle = findViewById(R.id.tvDeptTitle);
        TextView tvDesc = findViewById(R.id.tvDeptDesc);
        String title = null, desc = null;
        boolean isAdmin = "admin".equalsIgnoreCase(role);
        boolean isManager = "manager".equalsIgnoreCase(role);
        if (isAdmin) {
            title = "Quản trị hệ thống";
            desc = "Toàn quyền: phòng ban, tài khoản, bảng lương và dữ liệu toàn công ty.";
        } else if ("ACC".equalsIgnoreCase(dept)) {
            title = "Phòng Kế toán";
            desc = isManager
                    ? "Tính lương, điều chỉnh và chốt bảng lương toàn công ty tại tab Bảng lương."
                    : "Soạn bảng lương nháp và điều chỉnh các khoản tại tab Bảng lương; chốt lương do Kế toán trưởng thực hiện.";
        } else if ("HR".equalsIgnoreCase(dept)) {
            title = "Phòng Nhân sự";
            desc = isManager
                    ? "Quản lý hồ sơ nhân viên trong phòng Nhân sự và đặt lương cơ bản; tạo nhân viên cho phòng khác do Admin phụ trách."
                    : "Xem và tổng hợp chấm công của phòng mình; danh sách nhân viên do Trưởng phòng quản lý.";
        } else if (isManager) {
            title = "Quản lý phòng ban";
            desc = "Quản lý nhân viên, duyệt nghỉ và báo cáo chấm công của phòng mình tại các tab Tổng quan, Nhân viên, Duyệt phép.";
        }
        if (title == null) {
            section.setVisibility(View.GONE);
        } else {
            section.setVisibility(View.VISIBLE);
            tvTitle.setText(title);
            tvDesc.setText(desc);
        }
    }

    private String deptToVietnamese(String dept) {
        if (dept == null) return "";
        switch (dept.toUpperCase()) {
            case "IT": return "Phòng IT";
            case "HR": return "Phòng Nhân sự";
            case "ACC": return "Phòng Kế toán";
            case "SALES": return "Phòng Kinh doanh";
            case "MKT": return "Phòng Marketing";
            case "OPS": return "Phòng Hành chính";
            case "CS": return "Phòng CSKH";
            default: return "Phòng " + dept;
        }
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
                } else if (r.code() != 401) {
                    tvAttStatus.setText("Không tải được trạng thái chấm công");
                    tvAttDetail.setText(ApiError.message(r, "Vui lòng thử lại sau"));
                }
            }

            @Override
            public void onFailure(Call<AttendanceTodayResponse> c, Throwable t) {
                tvAttStatus.setText("Không tải được trạng thái chấm công");
                tvAttDetail.setText("Kiểm tra kết nối mạng rồi mở lại Trang chủ.");
            }
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
        if (myId <= 0) {
            Toast.makeText(this, "Phiên đăng nhập không hợp lệ, vui lòng đăng nhập lại", Toast.LENGTH_SHORT).show();
            return;
        }
        // Khoa ca 2 nut trong luc cho server, tranh bam 2 lan gui trung request.
        btnHomeCheckIn.setEnabled(false);
        btnHomeCheckOut.setEnabled(false);
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
                } else {
                    // Hien dung loi server (409 da cham cong, 401 het phien...) thay vi doan sai.
                    Toast.makeText(HomeActivity.this,
                            ApiError.message(r, isIn ? "Không chấm công vào được" : "Không chấm công ra được"),
                            Toast.LENGTH_LONG).show();
                }
                loadToday(); // renderToday se mo/khoa nut dung trang thai moi nhat
            }

            @Override
            public void onFailure(Call<AttendanceResponse> c, Throwable t) {
                Toast.makeText(HomeActivity.this, "Lỗi kết nối: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                loadToday();
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
