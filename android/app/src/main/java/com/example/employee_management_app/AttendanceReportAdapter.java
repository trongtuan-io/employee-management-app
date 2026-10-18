package com.example.employee_management_app;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class AttendanceReportAdapter extends RecyclerView.Adapter<AttendanceReportAdapter.ViewHolder> {

    private List<AttendanceResponse> reportList;

    public AttendanceReportAdapter(List<AttendanceResponse> reportList) {
        this.reportList = reportList;
    }

    public void updateData(List<AttendanceResponse> newList) {
        this.reportList = newList;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_attendance_report, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        AttendanceResponse item = reportList.get(position);

        holder.tvEmpName.setText(item.getFullName() != null ? item.getFullName() : "Nhân viên ID: " + item.getEmployeeId());
        holder.tvCheckIn.setText("Vào: " + (item.getCheckInTime() != null ? item.getCheckInTime() : "Chưa vào"));
        holder.tvCheckOut.setText("Ra: " + (item.getCheckOutTime() != null ? item.getCheckOutTime() : "Chưa ra"));
        holder.tvStatus.setText("Trạng thái: " + (item.getStatus() != null ? item.getStatus() : "--"));
        holder.tvWorkHours.setText("Số giờ: " + item.getWorkHours() );
    }

    @Override
    public int getItemCount() {
        return reportList != null ? reportList.size() : 0;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvEmpName, tvCheckIn, tvCheckOut, tvStatus, tvWorkHours;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvEmpName = itemView.findViewById(R.id.tvEmpName);
            tvCheckIn = itemView.findViewById(R.id.tvCheckIn);
            tvCheckOut = itemView.findViewById(R.id.tvCheckOut);
            tvStatus = itemView.findViewById(R.id.tvStatus);
            tvWorkHours = itemView.findViewById(R.id.tvWorkHours);
        }
    }
}
