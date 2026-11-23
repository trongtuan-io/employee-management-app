package com.example.employee_management_app;

import java.util.List;

// Danh sach thong bao cua chinh minh (notify-list): so chua doc + cac thong bao moi nhat.
public class NotificationListResponse {
    private boolean success;
    private Data data;

    public boolean isSuccess() { return success; }
    public Data getData() { return data; }

    public static class Data {
        private int unread;
        private List<Item> items;

        public int getUnread() { return unread; }
        public List<Item> getItems() { return items; }
    }

    public static class Item {
        private int id;
        private String type;
        private String title;
        private String body;
        private Integer ref_id;
        private int is_read;
        private String created_at;

        public int getId() { return id; }
        public String getType() { return type; }
        public String getTitle() { return title; }
        public String getBody() { return body; }
        public Integer getRefId() { return ref_id; }
        public boolean isRead() { return is_read == 1; }
        public String getCreatedAt() { return created_at; }
        public void setRead(boolean read) { is_read = read ? 1 : 0; }
    }
}
