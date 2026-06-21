package com.govlens.map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MapController.class)
class MapControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MapRepository repository;

    @Test
    void getItemCodes_returnsEntries() throws Exception {
        when(repository.findAllItemCodes()).thenReturn(List.of(
                new ItemCodeEntry("E12", "Elementary and secondary education")
        ));

        mockMvc.perform(get("/api/v1/map/item-codes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].itemCode").value("E12"));

        verify(repository).findAllItemCodes();
    }

    @Test
    void getGovTypes_returnsEntries() throws Exception {
        when(repository.findAllGovTypes()).thenReturn(List.of(
                new GovTypeEntry("2", "City")
        ));

        mockMvc.perform(get("/api/v1/map/gov-types"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].govTypeCode").value("2"));

        verify(repository).findAllGovTypes();
    }

    @Test
    void getCountySpending_invalidYear_returnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/map/county-spending")
                        .param("itemCode", "E12")
                        .param("year", "1800"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid year."));

        verifyNoInteractions(repository);
    }

    @Test
    void getCountySpending_allItemsAndAllTypes_callsAllItemsAllTypesQuery() throws Exception {
        when(repository.findCountySpendingAll(anyInt())).thenReturn(List.of(
                new CountySpendingResult("53033", "53", "WA", "King", 1000L, 100)
        ));

        mockMvc.perform(get("/api/v1/map/county-spending")
                        .param("itemCode", "__ALL__")
                        .param("year", "2023"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].countyFips").value("53033"));

        verify(repository).findCountySpendingAll(2023);
    }

    @Test
    void getCountySpending_allItemsWithSpecificType_callsAllItemsByTypeQuery() throws Exception {
        when(repository.findCountySpendingAllByType(anyInt(), eq("2"))).thenReturn(List.of(
                new CountySpendingResult("53033", "53", "WA", "King", 1000L, 100)
        ));

        mockMvc.perform(get("/api/v1/map/county-spending")
                        .param("itemCode", "__ALL__")
                        .param("year", "2023")
                        .param("govTypeCode", "2"))
                .andExpect(status().isOk());

        verify(repository).findCountySpendingAllByType(2023, "2");
    }

    @Test
    void getCountySpending_specificItemAndAllTypes_callsItemQuery() throws Exception {
        when(repository.findCountySpending(eq("E12"), anyInt())).thenReturn(List.of(
                new CountySpendingResult("53033", "53", "WA", "King", 1000L, 100)
        ));

        mockMvc.perform(get("/api/v1/map/county-spending")
                        .param("itemCode", "E12")
                        .param("year", "2023")
                        .param("govTypeCode", "__ALL__"))
                .andExpect(status().isOk());

        verify(repository).findCountySpending("E12", 2023);
    }

    @Test
    void getCountySpending_specificItemWithBlankType_callsItemQuery() throws Exception {
        when(repository.findCountySpending(eq("E12"), anyInt())).thenReturn(List.of(
                new CountySpendingResult("53033", "53", "WA", "King", 1000L, 100)
        ));

        mockMvc.perform(get("/api/v1/map/county-spending")
                        .param("itemCode", "E12")
                        .param("year", "2023")
                        .param("govTypeCode", "   "))
                .andExpect(status().isOk());

        verify(repository).findCountySpending("E12", 2023);
        verify(repository, never()).findCountySpendingByType("E12", 2023, "   ");
    }

    @Test
    void getCountySpending_specificItemAndSpecificType_callsItemByTypeQuery() throws Exception {
        when(repository.findCountySpendingByType(eq("E12"), anyInt(), eq("2"))).thenReturn(List.of(
                new CountySpendingResult("53033", "53", "WA", "King", 1000L, 100)
        ));

        mockMvc.perform(get("/api/v1/map/county-spending")
                        .param("itemCode", "E12")
                        .param("year", "2023")
                        .param("govTypeCode", "2"))
                .andExpect(status().isOk());

        verify(repository).findCountySpendingByType("E12", 2023, "2");
    }
}
