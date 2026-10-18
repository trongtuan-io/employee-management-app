package com.example.employee_management_app;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONObject;

public class AttendanceHistoryAdapter extends RecyclerView.Adapter<AttendanceHistoryAdapter.ViewHolder> {

    private JSONArray historyList = new JSONArray();

    public void setHistoryData(JSONArray data) {
        this.historyList = data != null ? data : new JSONArray();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_attendance_history, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        try {
            JSONObject item = historyList.getJSONObject(position);
            String workDate = item.optString("work_date", "--");
            String checkIn = item.optString("check_in_time", "--");
            String checkOut = item.optString("check_out_time", "--");
            String status = item.optString("status", "");

            holder.tvWorkDate.setText("Ngày: " + workDate);
            holder.tvCheckIn.setText("Vào ca: " + (checkIn.contains(" ") ? checkIn.split(" ")[1] : checkIn));

            if (checkOut.equals("null") || checkOut.isEmpty() || checkOut.equals("0000-00-00 00:00:00")) {
                holder.tvCheckOut.setText("Tan ca: Chưa ra ca");
                holder.tvCheckOut.setTextColor(Color.parseColor("#888888"));
            } else {
                holder.tvCheckOut.setText("Tan ca: " + (checkOut.contains(" ") ? checkOut.split(" ")[1] : checkOut));
                holder.tvCheckOut.setTextColor(Color.parseColor("#333333"));
            }

            if ("late".equalsIgnoreCase(status)) {
                holder.tvStatus.setText("Đi muộn");
                holder.tvStatus.setTextColor(Color.parseColor("#D32F2F"));
            } else {
                holder.tvStatus.setText("Đúng giờ");
                holder.tvStatus.setTextColor(Color.parseColor("#388E3C"));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public int getItemCount() {
        return historyList.length();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvWorkDate, tvCheckIn, tvCheckOut, tvStatus;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvWorkDate = itemView.findViewById(R.id.tv_work_date);
            tvCheckIn = itemView.findViewById(R.id.tv_check_in);
            tvCheckOut = itemView.findViewById(R.id.tv_check_out);
            tvStatus = itemView.findViewById(R.id.tv_status);
        }
    }
}