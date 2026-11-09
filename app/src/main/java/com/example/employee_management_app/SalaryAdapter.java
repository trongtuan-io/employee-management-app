package com.example.employee_management_app;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import java.util.List;

// Dong bang luong trong danh sach theo thang: Thang M/YYYY + badge trang thai
// + ngay cong + thuc linh. Cham vao dong -> man chi tiet thang do.
public class SalaryAdapter extends ArrayAdapter<SalaryListResponse.Item> {
    public SalaryAdapter(Context context, List<SalaryListResponse.Item> items) {
        super(context, 0, items);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = LayoutInflater.from(getContext()).inflate(R.layout.item_salary, parent, false);
        }
        SalaryListResponse.Item item = getItem(position);
        TextView tvMonth = convertView.findViewById(R.id.tvSalaryMonth);
        TextView tvBadge = convertView.findViewById(R.id.tvSalaryBadge);
        TextView tvWorkDays = convertView.findViewById(R.id.tvSalaryWorkDays);
        TextView tvNet = convertView.findViewById(R.id.tvSalaryNet);
        if (item == null) return convertView;

        tvMonth.setText("Tháng " + item.getMonth() + "/" + item.getYear());
        if (item.isFinalized()) {
            tvBadge.setText("Đã chốt");
            tvBadge.setBackgroundResource(R.drawable.bg_badge_approved);
            tvBadge.setTextColor(ContextCompat.getColor(getContext(), R.color.success));
        } else {
            tvBadge.setText("Bản nháp");
            tvBadge.setBackgroundResource(R.drawable.bg_badge_pending);
            tvBadge.setTextColor(ContextCompat.getColor(getContext(), R.color.warning));
        }
        String wd = item.workDaysText();
        tvWorkDays.setText(wd.isEmpty() ? "" : "Ngày công: " + wd);
        tvWorkDays.setVisibility(wd.isEmpty() ? View.GONE : View.VISIBLE);
        tvNet.setText("Thực lĩnh: " + SalaryListResponse.Item.money(item.getNetSalary()));
        return convertView;
    }
}
