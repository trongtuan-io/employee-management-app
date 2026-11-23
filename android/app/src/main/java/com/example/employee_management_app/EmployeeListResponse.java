package com.example.employee_management_app;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class EmployeeListResponse {
    @SerializedName("success")
    private boolean success;

    @SerializedName("data")
    private List<Item> data;

    @SerializedName("pagination")
    private Pagination pagination;

    public boolean isSuccess() {
        return success;
    }

    public List<Item> getData() {
        return data;
    }

    public Pagination getPagination() {
        if (pagination == null) {
            pagination = new Pagination();
        }
        return pagination;
    }

    public static class Pagination {
        private int page = 1;
        private int limit = 20;
        private int total = 0;
        @SerializedName("total_pages")
        private int totalPages = 1;

        public int getPage() { return page <= 0 ? 1 : page; }
        public int getLimit() { return limit <= 0 ? 20 : limit; }
        public int getTotal() { return total < 0 ? 0 : total; }
        public int getTotalPages() { return totalPages <= 0 ? 1 : totalPages; }

        public boolean isValid() {
            if (limit <= 0) limit = 20;
            if (page <= 0) page = 1;
            if (totalPages <= 0) totalPages = Math.max(1, (total + limit - 1) / limit);
            return true;
        }
    }

    public static class Item {
        private int id;
        private String username;
        private String full_name;
        private String email;
        private String phone;
        private int status;

        public int getId() { return id; }
        public String getUsername() { return username; }
        public String getFullName() { return full_name; }
        public String getEmail() { return email; }
        public String getPhone() { return phone; }
        public int getStatus() { return status; }

        @Override
        public String toString() {
            return full_name + " (" + username + ")" + (status == 0 ? " [ĐÃ KHÓA]" : "");
        }
    }
}
