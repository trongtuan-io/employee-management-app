package com.example.employee_management_app;

import java.util.List;

public class DepartmentListResponse {
    private boolean success;
    private List<Item> data;

    public boolean isSuccess() { return success; }
    public List<Item> getData() { return data; }

    public static class Item {
        private int id;
        private String name;

        public Item(int id, String name) {
            this.id = id;
            this.name = name;
        }

        public int getId() { return id; }
        public String getName() { return name; }

        @Override
        public String toString() { return name; }
    }
}
