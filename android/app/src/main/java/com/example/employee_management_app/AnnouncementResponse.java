package com.example.employee_management_app;

import java.util.List;

// Danh sach tin tu announcements.php?action=list (Bang tin cong ty o Trang chu)
public class AnnouncementResponse {
    private boolean success;
    private List<Item> data;

    public boolean isSuccess() { return success; }
    public List<Item> getData() { return data; }

    public static class Item {
        private int id;
        private String title;
        private String content;
        private String author_name;
        private String created_at;

        public int getId() { return id; }
        public String getTitle() { return title; }
        public String getContent() { return content; }
        public String getAuthorName() { return author_name; }
        public String getCreatedAt() { return created_at; }
    }
}
