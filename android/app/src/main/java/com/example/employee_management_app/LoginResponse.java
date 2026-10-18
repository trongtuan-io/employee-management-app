package com.example.employee_management_app;

import com.google.gson.annotations.SerializedName;

public class LoginResponse {
    @SerializedName("success")
    private boolean success;

    @SerializedName("error")
    private String message;
<<<<<<< HEAD
    
=======

>>>>>>> 2328eaa7a34a7d695abecba54db24530ae3bbd2f
    @SerializedName("data")
    private EmployeeData data;

    public boolean isSuccess() {
        return success;
    }
<<<<<<< HEAD
    
=======

>>>>>>> 2328eaa7a34a7d695abecba54db24530ae3bbd2f
    public String getMessage() {
        return message;
    }

    public EmployeeData getData() {
        return data;
    }

<<<<<<< HEAD
    public String getToken() {
        return data != null ? data.getToken() : null;
    }

=======
>>>>>>> 2328eaa7a34a7d695abecba54db24530ae3bbd2f
    public static class EmployeeData {
        @SerializedName("id")
        private int id;

        @SerializedName("username")
        private String username;

        @SerializedName("role_name")
        private String role;

<<<<<<< HEAD
        @SerializedName("token")
        private String token;

=======
>>>>>>> 2328eaa7a34a7d695abecba54db24530ae3bbd2f
        public int getId() {
            return id;
        }

        public String getUsername() {
            return username;
        }

        public String getRole() {
            return role;
        }
<<<<<<< HEAD

        public String getToken() {
            return token;
        }
=======
>>>>>>> 2328eaa7a34a7d695abecba54db24530ae3bbd2f
    }
}
