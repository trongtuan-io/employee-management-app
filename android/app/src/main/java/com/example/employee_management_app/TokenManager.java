package com.example.employee_management_app;

import android.content.Context;
import android.content.SharedPreferences;

public class TokenManager {
    private static final String PREF_NAME = "EmployeeAppPrefs";
    private static final String KEY_TOKEN = "jwt_token";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_ROLE = "user_role";
    private static final String KEY_DEPT = "user_department";
    private static final String KEY_DEPT_ID = "user_department_id";
    private static final String KEY_CAN_APPROVE_BOD = "can_approve_bod";

    private SharedPreferences prefs;
    private static TokenManager instance;

    private TokenManager(Context context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static synchronized TokenManager getInstance(Context context) {
        if (instance == null) {
            instance = new TokenManager(context.getApplicationContext());
        }
        return instance;
    }

    public void saveToken(String token) {
        prefs.edit().putString(KEY_TOKEN, token).apply();
    }

    // Luu ca phien dang nhap trong MOT lan ghi: tranh app bi kill giua chung
    // de lai trang thai co token nhung mat user_id/role.
    public void saveSession(String token, int userId, String role) {
        saveSession(token, userId, role, null);
    }

    // Ban luu them ma phong ban (IT/HR/ACC...) de app phan luong giao dien theo
    // Role x Phong. Server van tu kiem tra quyen o moi API, day chi la hien thi.
    public void saveSession(String token, int userId, String role, String departmentName) {
        saveSession(token, userId, role, departmentName, 0);
    }

    public void saveSession(String token, int userId, String role, String departmentName, int departmentId) {
        saveSession(token, userId, role, departmentName, departmentId, false);
    }

    public void saveSession(String token, int userId, String role, String departmentName, int departmentId, boolean canApproveBod) {
        prefs.edit()
                .putString(KEY_TOKEN, token)
                .putInt(KEY_USER_ID, userId)
                .putString(KEY_ROLE, role)
                .putString(KEY_DEPT, departmentName)
                .putInt(KEY_DEPT_ID, departmentId)
                .putBoolean(KEY_CAN_APPROVE_BOD, canApproveBod)
                .commit();
    }

    public String getDepartmentName() {
        return prefs.getString(KEY_DEPT, null);
    }

    public int getDepartmentId() {
        return prefs.getInt(KEY_DEPT_ID, 0);
    }

    public boolean isExecutive() {
        return prefs.getBoolean(KEY_CAN_APPROVE_BOD, false);
    }

    public String getToken() {
        return prefs.getString(KEY_TOKEN, null);
    }

    public void saveUserId(int userId) {
        prefs.edit().putInt(KEY_USER_ID, userId).apply();
    }

    public int getUserId() {
        return prefs.getInt(KEY_USER_ID, -1);
    }

    public void saveRole(String role) {
        prefs.edit().putString(KEY_ROLE, role).apply();
    }

    public String getRole() {
        return prefs.getString(KEY_ROLE, "staff");
    }

    public void clearToken() {
        prefs.edit().remove(KEY_TOKEN).remove(KEY_USER_ID).remove(KEY_ROLE).remove(KEY_DEPT).remove(KEY_DEPT_ID).remove(KEY_CAN_APPROVE_BOD).apply();
    }
}
