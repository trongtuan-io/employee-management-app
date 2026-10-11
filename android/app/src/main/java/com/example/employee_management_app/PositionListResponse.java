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
        public Item(int id, String title) { this.id=id; this.title=title; }
        public int getId() { return id; }
        public String getTitle() { return title; }
        @Override public String toString() { return title; }
    }
}
