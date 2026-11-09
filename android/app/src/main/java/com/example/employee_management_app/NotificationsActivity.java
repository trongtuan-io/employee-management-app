package com.example.employee_management_app;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

// Trung tam thong bao: su kien don nghi (moi/duyet/tu choi) va luong
// (chot/mo lai/da tra) cua CHINH nguoi dang nhap. Cham vao 1 thong bao se
// danh dau da doc va mo man hinh lien quan (Xin nghi hoac Bang luong).
public class NotificationsActivity extends AppCompatActivity {
    private EmployeeApi api;
    private TextView tvStatus;
    private final List<NotificationListResponse.Item> items = new ArrayList<>();
    private NotifyAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notifications);
        api = ApiClient.getClient(this).create(EmployeeApi.class);

        tvStatus = findViewById(R.id.tvNotifyStatus);
        ListView lv = findViewById(R.id.lvNotifications);
        adapter = new NotifyAdapter();
        lv.setAdapter(adapter);
        findViewById(R.id.btnNotifyBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnNotifyReadAll).setOnClickListener(v -> markAllRead());
        lv.setOnItemClickListener((parent, view, position, id) -> openItem(items.get(position)));
        load();
    }

    private void load() {
        api.getNotifications().enqueue(new Callback<NotificationListResponse>() {
            @Override
            public void onResponse(Call<NotificationListResponse> c, Response<NotificationListResponse> r) {
                if (r.isSuccessful() && r.body() != null && r.body().getData() != null) {
                    items.clear();
                    if (r.body().getData().getItems() != null) items.addAll(r.body().getData().getItems());
                    adapter.notifyDataSetChanged();
                    int unread = r.body().getData().getUnread();
                    tvStatus.setText(items.isEmpty()
                            ? "Chưa có thông báo nào."
                            : unread > 0 ? "Bạn có " + unread + " thông báo chưa đọc."
                            : "Bạn đã đọc hết thông báo.");
                } else if (r.code() != 401) {
                    tvStatus.setText(ApiError.message(r, "Không tải được thông báo"));
                }
            }

            @Override
            public void onFailure(Call<NotificationListResponse> c, Throwable t) {
                tvStatus.setText("Không tải được thông báo. Kiểm tra kết nối rồi mở lại màn hình này.");
            }
        });
    }

    private void openItem(NotificationListResponse.Item item) {
        if (!item.isRead()) {
            item.setRead(true);
            adapter.notifyDataSetChanged();
            refreshStatus();
            api.markNotificationRead(new NotificationReadRequest(item.getId()))
                    .enqueue(new Callback<ApiResponse>() {
                        @Override public void onResponse(Call<ApiResponse> c, Response<ApiResponse> r) { }
                        @Override public void onFailure(Call<ApiResponse> c, Throwable t) { }
                    });
        }
        String type = item.getType() == null ? "" : item.getType();
        if (type.startsWith("leave")) {
            startActivity(new Intent(this, LeaveActivity.class));
        } else if (type.startsWith("salary")) {
            startActivity(new Intent(this, SalaryActivity.class));
        } else if (type.startsWith("expl")) {
            startActivity(new Intent(this, ExplanationActivity.class));
        }
    }

    private void markAllRead() {
        api.markNotificationRead(new NotificationReadRequest(true)).enqueue(new Callback<ApiResponse>() {
            @Override
            public void onResponse(Call<ApiResponse> c, Response<ApiResponse> r) {
                if (r.isSuccessful() && r.body() != null && r.body().isSuccess()) {
                    for (NotificationListResponse.Item it : items) it.setRead(true);
                    adapter.notifyDataSetChanged();
                    refreshStatus();
                    Toast.makeText(NotificationsActivity.this, "Đã đánh dấu tất cả đã đọc", Toast.LENGTH_SHORT).show();
                } else if (r.code() != 401) {
                    Toast.makeText(NotificationsActivity.this, ApiError.message(r, "Không đánh dấu được"), Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<ApiResponse> c, Throwable t) {
                Toast.makeText(NotificationsActivity.this, "Lỗi kết nối", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void refreshStatus() {
        int unread = 0;
        for (NotificationListResponse.Item it : items) if (!it.isRead()) unread++;
        tvStatus.setText(items.isEmpty() ? "Chưa có thông báo nào."
                : unread > 0 ? "Bạn có " + unread + " thông báo chưa đọc."
                : "Bạn đã đọc hết thông báo.");
    }

    private static String displayTime(String createdAt) {
        if (createdAt == null || createdAt.length() < 16) return createdAt == null ? "" : createdAt;
        // "2026-11-09 13:25:00" -> "09/11/2026 13:25"
        return createdAt.substring(8, 10) + "/" + createdAt.substring(5, 7) + "/" + createdAt.substring(0, 4)
                + " " + createdAt.substring(11, 16);
    }

    private class NotifyAdapter extends BaseAdapter {
        @Override public int getCount() { return items.size(); }
        @Override public Object getItem(int position) { return items.get(position); }
        @Override public long getItemId(int position) { return items.get(position).getId(); }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View v = convertView;
            if (v == null) {
                v = LayoutInflater.from(NotificationsActivity.this)
                        .inflate(R.layout.item_notification, parent, false);
            }
            NotificationListResponse.Item item = items.get(position);
            v.findViewById(R.id.tvNotifyDot).setVisibility(item.isRead() ? View.INVISIBLE : View.VISIBLE);
            TextView tvTitle = v.findViewById(R.id.tvNotifyTitle);
            TextView tvBody = v.findViewById(R.id.tvNotifyBody);
            TextView tvTime = v.findViewById(R.id.tvNotifyTime);
            tvTitle.setText(item.getTitle());
            tvTitle.setAlpha(item.isRead() ? 0.65f : 1f);
            tvBody.setText(item.getBody() == null ? "" : item.getBody());
            tvBody.setVisibility(item.getBody() == null || item.getBody().isEmpty() ? View.GONE : View.VISIBLE);
            tvTime.setText(displayTime(item.getCreatedAt()));
            return v;
        }
    }
}
