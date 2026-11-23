package com.example.employee_management_app;

// Ket qua salary-generate: so bang luong da tinh + so dong bo qua vi da chot
public class SalaryGenerateResponse {
    private boolean success;
    private Data data;

    public boolean isSuccess() { return success; }
    public Data getData() { return data; }

    public static class Data {
        private int month;
        private int year;
        private int standard_days;
        private int generated;
        private int skipped_finalized;

        public int getMonth() { return month; }
        public int getYear() { return year; }
        public int getStandardDays() { return standard_days; }
        public int getGenerated() { return generated; }
        public int getSkippedFinalized() { return skipped_finalized; }
    }
}
