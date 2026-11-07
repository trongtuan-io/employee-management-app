package com.example.employee_management_app;

import android.app.Activity;
import android.content.Intent;

import com.google.android.material.bottomnavigation.BottomNavigationView;

// Gan thanh dieu huong duoi vao 1 man hinh chinh:
// - Menu theo vai tro: admin/manager co them "Tong quan" + "Nhan vien", muc Nghi phep thanh "Duyet phep".
// - Bam muc khac -> mo Activity tuong ung (khong animation) va dong man hien tai de back stack gon.
public class BottomNavHelper {

    public static void setup(Activity activity, int selectedItemId) {
        BottomNavigationView nav = activity.findViewById(R.id.bottomNav);
        if (nav == null) return;

        String role = TokenManager.getInstance(activity).getRole();
        boolean isManager = "admin".equalsIgnoreCase(role) || "manager".equalsIgnoreCase(role);
        nav.inflateMenu(isManager ? R.menu.bottom_nav_admin : R.menu.bottom_nav_staff);
        nav.setBackgroundColor(android.graphics.Color.WHITE);
        nav.setItemIconTintList(activity.getColorStateList(R.color.bottom_nav_colors));
        nav.setItemTextColor(activity.getColorStateList(R.color.bottom_nav_colors));
        nav.setSelectedItemId(selectedItemId);

        nav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == selectedItemId) return true;
            Class<?> target = null;
            if (id == R.id.nav_home) target = HomeActivity.class;
            else if (id == R.id.nav_leave) target = LeaveActivity.class;
            else if (id == R.id.nav_salary) target = SalaryActivity.class;
            else if (id == R.id.nav_profile) target = ProfileActivity.class;
            else if (id == R.id.nav_overview) target = AdminActivity.class;
            else if (id == R.id.nav_staff) target = StaffListActivity.class;
            if (target == null) return false;
            Intent intent = new Intent(activity, target);
            activity.startActivity(intent);
            activity.overridePendingTransition(0, 0);
            activity.finish();
            return true;
        });
    }
}
