package com.example.employee_management_app;

public class ImportBatchActionRequest {
    private final int requester_id;
    private final long batch_id;
    public ImportBatchActionRequest(int requesterId,long batchId) {
        this.requester_id=requesterId;
        this.batch_id=batchId;
    }
}
