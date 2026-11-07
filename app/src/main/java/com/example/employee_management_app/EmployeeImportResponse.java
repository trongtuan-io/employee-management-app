package com.example.employee_management_app;
import java.util.List;
public class EmployeeImportResponse {
    private boolean success;
    private Data data;
    private String error;
    public boolean isSuccess() { return success; }
    public Data getData() { return data; }
    public String getError() { return error; }
    public static class Data {
        private int imported;
        private List<Credential> temporary_credentials;
        public int getImported() { return imported; }
        public List<Credential> getCredentials() { return temporary_credentials; }
    }
    public static class Credential {
        private String username;
        private String temporary_password;
        public String getUsername() { return username; }
        public String getPassword() { return temporary_password; }
    }
}
