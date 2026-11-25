package com.example.employee_management_app;

import android.content.Intent;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

/** Main entry for personnel operations. Leave and explanation belong to their own workflows. */
public class HrHubActivity extends AppCompatActivity {
    @Override public void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        if(!HrUi.hr(this)){Toast.makeText(this,"Chỉ phòng Nhân sự",Toast.LENGTH_LONG).show();finish();return;}
        LinearLayout root=HrUi.page(this,"Nghiệp vụ nhân sự");
        LinearLayout notice=HrUi.card(root);
        HrUi.title(notice,"Quản lý nhân sự · HR");
        HrUi.label(notice,"Import tạo tài khoản Staff trực tiếp. Hồ sơ cấp cao qua Trưởng phòng HR và BOD.");

        HrUi.section(root,"Hồ sơ và tuyển dụng");
        HrUi.navCard(root,"Danh sách nhân viên","Tra cứu và xem hồ sơ nhân viên",()->open(StaffListActivity.class));
        HrUi.navCard(root,"Hồ sơ ứng viên","Lập, chỉnh sửa và gửi hồ sơ cấp cao",()->open(HrProposalsActivity.class));
        HrUi.navCard(root,"Đề xuất và phê duyệt","Duyệt hồ sơ tuyển dụng, theo dõi đề xuất gửi BOD",()->open(HrProposalsActivity.class));
        HrUi.navCard(root,"Import Excel / CSV","Tạo tài khoản Staff và xem lịch sử import",()->open(ImportEmployeesActivity.class));

        HrUi.section(root,"Danh mục và kiểm tra");
        HrUi.navCard(root,"Danh mục chức vụ","Xem chức vụ đang sử dụng và chỉnh sửa theo quyền",()->open(HrPositionsActivity.class));
        HrUi.navCard(root,"Lịch sử hoạt động nhân sự","Ai thực hiện, vào lúc nào và thay đổi nội dung gì",()->open(HrHistoryActivity.class));
    }
    private void open(Class<?> cls){startActivity(new Intent(this,cls));}
}
