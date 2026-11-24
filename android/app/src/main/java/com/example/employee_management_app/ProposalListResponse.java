package com.example.employee_management_app;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class ProposalListResponse {
    @SerializedName("success")
    private boolean success;

    @SerializedName("data")
    private List<ProposalItem> data;

    public boolean isSuccess() { return success; }
    public List<ProposalItem> getData() { return data; }
}
