package com.example.employee_management_app;

import com.google.gson.Gson;
import org.junit.Test;
import okhttp3.HttpUrl;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import static org.junit.Assert.*;

public class EmployeeListContractTest {
    private final Gson gson = new Gson();
    private final EmployeeApi api = new Retrofit.Builder().baseUrl("http://localhost/")
            .addConverterFactory(GsonConverterFactory.create()).build().create(EmployeeApi.class);

    @Test public void requestIncludesAllFiltersAndCorrectlyEncodesSearch() {
        HttpUrl url = api.getEmployees("Nguyễn & An", 2, 3, 20, "name_desc").request().url();
        assertEquals("list", url.queryParameter("action"));
        assertEquals("Nguyễn & An", url.queryParameter("search"));
        assertEquals("2", url.queryParameter("dept_id"));
        assertEquals("3", url.queryParameter("page"));
        assertEquals("20", url.queryParameter("limit"));
        assertEquals("name_desc", url.queryParameter("sort"));
    }

    @Test public void allDepartmentsDoesNotSendZeroId() {
        HttpUrl url = api.getEmployees("", null, 1, 20, "name_asc").request().url();
        assertNull(url.queryParameter("dept_id"));
        assertEquals("departments", api.getDepartments().request().url().queryParameter("action"));
    }

    @Test public void parsesExistingDataArrayAndNewPagination() {
        EmployeeListResponse response = gson.fromJson(
                "{'success':true,'data':[{'id':42,'username':'NV042','full_name':'An','status':1}],"
                + "'pagination':{'page':3,'limit':20,'total':41,'total_pages':3}}",
                EmployeeListResponse.class);
        assertTrue(response.isSuccess());
        assertEquals(42, response.getData().get(0).getId());
        assertTrue(response.getPagination().isValid());
        assertEquals(3, response.getPagination().getPage());
        assertEquals(41, response.getPagination().getTotal());
    }

    @Test public void emptyResultHasOneEmptyPage() {
        EmployeeListResponse.Pagination page = page("{'page':1,'limit':20,'total':0,'total_pages':1}");
        assertTrue(page.isValid());
    }

    @Test public void invalidPaginationIsRejected() {
        assertFalse(page("{}").isValid());
        assertFalse(page("{'page':1,'limit':0,'total':1,'total_pages':1}").isValid());
        assertFalse(page("{'page':3,'limit':20,'total':21,'total_pages':2}").isValid());
        assertFalse(page("{'page':1,'limit':20,'total':21,'total_pages':1}").isValid());
        assertFalse(page("{'page':1,'limit':20,'total':-1,'total_pages':1}").isValid());
    }

    @Test public void oldBackendIsDetectableWithoutBreakingDataParsing() {
        EmployeeListResponse response = gson.fromJson("{'success':true,'data':[]}", EmployeeListResponse.class);
        assertTrue(response.isSuccess());
        assertTrue(response.getData().isEmpty());
        assertNull(response.getPagination());
    }

    @Test public void departmentIdsAreSeparateFromDisplayNames() {
        DepartmentListResponse response = gson.fromJson(
                "{'success':true,'data':[{'id':2,'name':'HR'}]}", DepartmentListResponse.class);
        assertTrue(response.isSuccess());
        assertEquals(2, response.getData().get(0).getId());
        assertEquals("HR", response.getData().get(0).toString());
    }

    private EmployeeListResponse.Pagination page(String json) {
        return gson.fromJson(json, EmployeeListResponse.Pagination.class);
    }
}
