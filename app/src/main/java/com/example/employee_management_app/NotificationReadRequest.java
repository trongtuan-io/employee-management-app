package com.example.employee_management_app;

// Danh dau thong bao da doc: theo 1 id, hoac tat ca (all=true).
public class NotificationReadRequest {
    private Integer id;
    private Boolean all;

    public NotificationReadRequest(int id) {
        this.id = id;
    }

    public NotificationReadRequest(boolean all) {
        this.all = all;
    }
}
