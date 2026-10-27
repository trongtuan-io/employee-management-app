package com.example.employee_management_app;

import com.google.gson.annotations.SerializedName;
import java.util.List;

// Dung chung cho departments + roles (deu co id, name)
public class IdNameListResponse {
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
        private String name;

        public int getId() { return id; }
        public String getName() { return name; }

        @Override
        public String toString() { return name; }
    }
}
