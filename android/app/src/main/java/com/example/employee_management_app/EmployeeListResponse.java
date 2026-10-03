package com.example.employee_management_app;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class EmployeeListResponse {
    @SerializedName("success")
    private boolean success;

    @SerializedName("data")
    private List<Item> data;

    public boolean isSuccess() {
        return success;
    }

    public List<Item> getData() {
        return data;
    }

    public static class Item {
        private int id;
        private String username;
        private String full_name;
        private String email;
        private String phone;

        public int getId() { return id; }
        public String getUsername() { return username; }
        public String getFullName() { return full_name; }
        public String getEmail() { return email; }
        public String getPhone() { return phone; }

        @Override
        public String toString() {
            return full_name + " (" + username + ")";
        }
    }
}
