package com.example.employee_management_app;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        TextView tvWelcome = findViewById(R.id.tvWelcome);
        TextView tvRole = findViewById(R.id.tvRole);
        Button btnChangePassword = findViewById(R.id.btnChangePassword);
        Button btnLogout = findViewById(R.id.btnLogout);

        // Lấy username và role từ Intent
        String username = getIntent().getStringExtra("USERNAME");
        String role = getIntent().getStringExtra("ROLE");
        
        if (username != null && !username.isEmpty()) {
            tvWelcome.setText("Chào mừng " + username + "!");
        }
        
        if (role != null && !role.isEmpty()) {
            tvRole.setText("Phân quyền: " + role.toUpperCase());
            
            // Ví dụ: Bạn có thể ẩn/hiện nút bấm tùy theo quyền
            if (role.equalsIgnoreCase("admin")) {
                // Hiển thị các chức năng của Admin
            } else {
                // Ẩn các chức năng của Admin, chỉ hiện chức năng Nhân viên
            }
        }

        // Xử lý chuyển sang màn hình Đổi Mật Khẩu
        btnChangePassword.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, ChangePasswordActivity.class);
            startActivity(intent);
        });

        // Xử lý đăng xuất
        btnLogout.setOnClickListener(v -> {
            // Xóa JWT Token khi đăng xuất
            TokenManager.getInstance(MainActivity.this).clearToken();
            
            Intent intent = new Intent(MainActivity.this, LoginActivity.class);
            // Xoá các Activity cũ khỏi stack
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });
    }
}