package com.govlens;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.govlens.common.PaginatedResponse;
import com.govlens.government.api.GovernmentSearchResult;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for core API endpoints.
 * Verifies response contracts, pagination, and error handling.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:postgresql://localhost:5432/govlens",
    "spring.datasource.username=postgres",
    "spring.datasource.password=postgres"
})
public class ApiContractIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    public void testHealthEndpoint() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(anyOf(
                    equalTo("UP"),
                    equalTo("DEGRADED")
                )))
                .andExpect(jsonPath("$.database").exists())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    public void testGovernmentSearchEndpoint_WithValidQuery() throws Exception {
        mockMvc.perform(get("/api/v1/governments")
                    .param("query", "seattle")
                    .param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.pagination").exists())
                .andExpect(jsonPath("$.pagination.limit").value(10))
                .andExpect(jsonPath("$.pagination.offset").value(0))
                .andExpect(jsonPath("$.pagination.total_count").isNumber())
                .andExpect(jsonPath("$.pagination.has_more").isBoolean());
    }

    @Test
    public void testGovernmentSearchEndpoint_WithInvalidQuery() throws Exception {
        mockMvc.perform(get("/api/v1/governments")
                    .param("query", "s"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    public void testGovernmentSearchEndpoint_WithStateFilter() throws Exception {
        mockMvc.perform(get("/api/v1/governments")
                    .param("query", "seattle")
                    .param("state", "WA")
                    .param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.pagination.limit").value(5))
                .andExpect(content().string(containsString("state")));
    }

    @Test
    public void testZipLookupEndpoint_WithValidZip() throws Exception {
        mockMvc.perform(get("/api/v1/governments/by-zip")
                    .param("zip", "98101")
                    .param("limit", "25"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.pagination").exists())
                .andExpect(jsonPath("$.pagination.limit").value(25))
                .andExpect(jsonPath("$.pagination.total_count").isNumber());
    }

    @Test
    public void testZipLookupEndpoint_WithInvalidZip() throws Exception {
        mockMvc.perform(get("/api/v1/governments/by-zip")
                    .param("zip", "invalid"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    public void testExpenseBreakdownEndpoint() throws Exception {
        // First, find a valid government ID via search
        mockMvc.perform(get("/api/v1/governments")
                    .param("query", "seattle")
                    .param("limit", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].unitId").exists());

        // Then, test expense breakdown (requires valid unitId)
        // In production, extract the unitId from the search result
        mockMvc.perform(get("/api/v1/governments/532033184255/expense-breakdown")
                    .param("year", "2023"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.government").exists())
                .andExpect(jsonPath("$.totalExpensesThousands").isNumber())
                .andExpect(jsonPath("$.categories").isArray());
    }

    @Test
    public void testCompareEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/compare")
                    .param("leftUnitId", "532033184255")
                    .param("rightUnitId", "532033176842")
                    .param("year", "2023"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.year").value(2023))
                .andExpect(jsonPath("$.leftGovernment").exists())
                .andExpect(jsonPath("$.rightGovernment").exists())
                .andExpect(jsonPath("$.items").isArray());
    }

    @Test
    public void testRequestIdHeaderPresent() throws Exception {
        mockMvc.perform(get("/health")
                    .header("X-Request-ID", "test-trace-id"))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Request-ID"));
    }

    @Test
    public void testPaginationLimitEnforcement() throws Exception {
        // Test that limits exceeding max (100) are capped
        mockMvc.perform(get("/api/v1/governments")
                    .param("query", "city")
                    .param("limit", "1000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pagination.limit").value(100));
    }

    @Test
    public void testPaginationOffsetHandling() throws Exception {
        mockMvc.perform(get("/api/v1/governments")
                    .param("query", "city")
                    .param("offset", "10")
                    .param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pagination.offset").value(10))
                .andExpect(jsonPath("$.pagination.limit").value(5));
    }
}
