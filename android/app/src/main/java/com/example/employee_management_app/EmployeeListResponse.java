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

    public Pagination getPagination() { return pagination; }

    public static class Pagination {
        private int page;
        private int limit;
        private int total;
        @SerializedName("total_pages")
        private int totalPages;

        public int getPage() { return page; }
        public int getLimit() { return limit; }
        public int getTotal() { return total; }
        public int getTotalPages() { return totalPages; }

        public boolean isValid() {
            return limit > 0 && limit <= 50 && total >= 0 && page >= 1
                    && totalPages == Math.max(1L, (total + (long) limit - 1) / limit)
                    && page <= totalPages;
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
