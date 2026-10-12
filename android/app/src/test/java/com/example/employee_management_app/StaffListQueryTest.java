package com.example.employee_management_app;

import org.junit.Test;
import static org.junit.Assert.*;

public class StaffListQueryTest {
    @Test public void changingSearchResetsPageAndKeepsDepartmentAndSort() {
        StaffListQuery result = new StaffListQuery("old", 2, 1, 3).withSearch("  new  ");
        assertEquals(new StaffListQuery("new", 2, 1, 1), result);
    }

    @Test public void changingDepartmentResetsPageAndKeepsSearchAndSort() {
        StaffListQuery result = new StaffListQuery("An", 1, 3, 4).withDepartment(2);
        assertEquals(new StaffListQuery("An", 2, 3, 1), result);
    }

    @Test public void changingSortResetsPageAndKeepsBothFilters() {
        StaffListQuery result = new StaffListQuery("An", 2, 0, 4).withSort(1);
        assertEquals(new StaffListQuery("An", 2, 1, 1), result);
    }

    @Test public void pageNavigationPreservesEntireQuery() {
        StaffListQuery original = new StaffListQuery("An", 2, 3, 1);
        StaffListQuery next = original.withPage(2);
        assertEquals(new StaffListQuery("An", 2, 3, 2), next);
        assertEquals(original, next.withPage(1));
        assertEquals("code_desc", next.apiSort());
        assertEquals(Integer.valueOf(2), next.apiDepartmentId());
    }

    @Test public void sameSelectionDoesNotResetCurrentPage() {
        StaffListQuery original = new StaffListQuery("An", 2, 3, 4);
        assertSame(original, original.withSearch(" An "));
        assertSame(original, original.withDepartment(2));
        assertSame(original, original.withSort(3));
    }

    @Test public void allDepartmentsOmitsApiParameter() {
        StaffListQuery result = new StaffListQuery("", 2, 0, 3).withDepartment(0);
        assertNull(result.apiDepartmentId());
        assertEquals(1, result.page);
    }

    @Test public void invalidSavedStateUsesSafeDefaults() {
        StaffListQuery result = new StaffListQuery(null, -1, 99, -3);
        assertEquals(new StaffListQuery("", 0, 0, 1), result);
    }
}
