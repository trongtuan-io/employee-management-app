package com.example.employee_management_app;

import java.util.List;
public class PositionListResponse {
    private boolean success;
    private List<Item> data;
    public boolean isSuccess() { return success; }
    public List<Item> getData() { return data; }
    public static class Item {
        private int id;
        private String title;
        private Integer department_id; // null = chuc vu dung chung moi phong
        public Item(int id, String title) { this.id=id; this.title=title; }
        public int getId() { return id; }
        public String getTitle() { return title; }
        public Integer getDepartmentId() { return department_id; }
        @Override public String toString() { return title; }
    }
}
