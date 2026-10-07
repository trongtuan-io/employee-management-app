package com.example.employee_management_app;

import com.google.gson.annotations.SerializedName;
import java.util.List;

// Rieng positions vi field ten la title
public class PositionListResponse {
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
        private String title;

        public int getId() { return id; }
        public String getTitle() { return title; }

        @Override
        public String toString() { return title; }
    }
}
