package com.example.employee_management_app;

import java.util.List;

// Lich su thay doi cua 1 dong bang luong (salary-history)
public class SalaryHistoryResponse {
    private boolean success;
    private List<Item> data;

    public boolean isSuccess() { return success; }
    public List<Item> getData() { return data; }

    public static class Item {
        private String actor_name;
        private String field;
        private String old_value;
        private String new_value;
        private String created_at;

        public String getActorName() { return actor_name; }
        public String getField() { return field; }
        public String getOldValue() { return old_value; }
        public String getNewValue() { return new_value; }
        public String getCreatedAt() { return created_at; }
    }
}
