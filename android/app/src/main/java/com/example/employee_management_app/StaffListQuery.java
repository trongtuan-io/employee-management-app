package com.example.employee_management_app;

import java.util.Objects;

// Immutable query: every page request carries the complete filter and sort selection.
final class StaffListQuery {
    static final int PAGE_SIZE = 20;
    static final String[] SORTS = {"name_asc", "name_desc", "code_asc", "code_desc"};

    final String search;
    final int departmentId; // 0 means all departments; omitted from the API query.
    final int sortIndex;
    final int page;

    StaffListQuery(String search, int departmentId, int sortIndex, int page) {
        this.search = search == null ? "" : search.trim();
        this.departmentId = Math.max(0, departmentId);
        this.sortIndex = sortIndex >= 0 && sortIndex < SORTS.length ? sortIndex : 0;
        this.page = Math.max(1, page);
    }

    StaffListQuery withSearch(String value) {
        String normalized = value == null ? "" : value.trim();
        return search.equals(normalized) ? this
                : new StaffListQuery(normalized, departmentId, sortIndex, 1);
    }

    StaffListQuery withDepartment(int value) {
        return departmentId == value ? this : new StaffListQuery(search, value, sortIndex, 1);
    }

    StaffListQuery withSort(int value) {
        return sortIndex == value ? this : new StaffListQuery(search, departmentId, value, 1);
    }

    StaffListQuery withPage(int value) {
        return new StaffListQuery(search, departmentId, sortIndex, value);
    }

    Integer apiDepartmentId() { return departmentId == 0 ? null : departmentId; }
    String apiSort() { return SORTS[sortIndex]; }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof StaffListQuery)) return false;
        StaffListQuery query = (StaffListQuery) other;
        return search.equals(query.search) && departmentId == query.departmentId
                && sortIndex == query.sortIndex && page == query.page;
    }

    @Override
    public int hashCode() { return Objects.hash(search, departmentId, sortIndex, page); }
}
