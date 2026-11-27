package com.example.employee_management_app;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class BodSalaryStatsResponse {
    @SerializedName("success")
    private boolean success;

    @SerializedName("data")
    private List<Item> data;

    public boolean isSuccess() { return success; }
    public List<Item> getData() { return data; }

    public static class Item {
        @SerializedName("month")
        private int month;

        @SerializedName("year")
        private int year;

        @SerializedName("label")
        private String label;

        @SerializedName("total_fund")
        private double totalFund;

        public int getMonth() { return month; }
        public int getYear() { return year; }
        public String getLabel() { return label; }
        public double getTotalFund() { return totalFund; }
    }
}
