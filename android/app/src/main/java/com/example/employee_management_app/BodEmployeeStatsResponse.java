package com.example.employee_management_app;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class BodEmployeeStatsResponse {
    @SerializedName("success")
    private boolean success;

    @SerializedName("data")
    private Data data;

    public boolean isSuccess() { return success; }
    public Data getData() { return data; }

    public static class Data {
        @SerializedName("by_department")
        private List<DeptItem> byDepartment;

        @SerializedName("workforce_structure")
        private Structure workforceStructure;

        public List<DeptItem> getByDepartment() { return byDepartment; }
        public Structure getWorkforceStructure() { return workforceStructure; }
    }

    public static class DeptItem {
        @SerializedName("dept_name")
        private String deptName;

        @SerializedName("emp_count")
        private int empCount;

        public String getDeptName() { return deptName; }
        public int getEmpCount() { return empCount; }
    }

    public static class Structure {
        @SerializedName("working")
        private int working;

        @SerializedName("probation")
        private int probation;

        @SerializedName("inactive")
        private int inactive;

        public int getWorking() { return working; }
        public int getProbation() { return probation; }
        public int getInactive() { return inactive; }
    }
}
