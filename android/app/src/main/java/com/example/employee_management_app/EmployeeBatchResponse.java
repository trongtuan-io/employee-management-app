package com.example.employee_management_app;

import java.util.List;

/** Retrofit/Gson response for Task 22 sync, rollback and batch details. */
public class EmployeeBatchResponse {
    private boolean success;
    private String error;
    private Data data;
    public boolean isSuccess() {return success;}
    public String getError() {return error;}
    public Data getData() {return data;}
    public static class Data {
        private long batch_id;
        private int updated;
        private int total;
        private int rolled_back;
        private Batch batch;
        private List<Batch> batches;
        private List<Row> rows;
        public long getBatchId(){return batch_id;}
        public int getUpdated(){return updated;}
        public int getTotal(){return total;}
        public int getRolledBack(){return rolled_back;}
        public Batch getBatch(){return batch;}
        public List<Batch> getBatches(){return batches;}
        public List<Row> getRows(){return rows;}
    }
    public static class Batch {
        private long id;
        private int total_rows;
        private String status;
        private String source_name;
        private String created_at;
        public String getSourceName(){return source_name;}
        public String getCreatedAt(){return created_at;}
        public long getId(){return id;}
        public int getTotalRows(){return total_rows;}
        public String getStatus(){return status;}
    }
    public static class Row {
        private int row_number;
        private Integer employee_id;
        public int getRowNumber(){return row_number;}
        public Integer getEmployeeId(){return employee_id;}
    }
}
