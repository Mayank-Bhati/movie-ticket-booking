package com.moviebooking.refund;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.moviebooking.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class RefundPolicyAdminIntegrationTest extends IntegrationTest {

    @Test
    void listUpsertAndDeleteRefundTiers() throws Exception {
        String admin = adminToken();

        mockMvc.perform(authed(get("/api/admin/refund-policies"), admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].minHoursBeforeShow").value(24));

        // update an existing tier (upsert by threshold)
        mockMvc.perform(authed(put("/api/admin/refund-policies"), admin)
                        .content("{\"minHoursBeforeShow\":2,\"refundPercent\":75}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refundPercent").value(75));

        // create a brand new tier
        long id = ((Number) JsonPath.read(
                mockMvc.perform(authed(put("/api/admin/refund-policies"), admin)
                                .content("{\"minHoursBeforeShow\":72,\"refundPercent\":100}"))
                        .andReturn().getResponse().getContentAsString(), "$.id")).longValue();

        mockMvc.perform(authed(delete("/api/admin/refund-policies/" + id), admin))
                .andExpect(status().isNoContent());
    }

    @Test
    void deletingUnknownTierReturnsNotFound() throws Exception {
        mockMvc.perform(authed(delete("/api/admin/refund-policies/9999"), adminToken()))
                .andExpect(status().isNotFound());
    }

    @Test
    void invalidRefundPercentIsRejected() throws Exception {
        mockMvc.perform(authed(put("/api/admin/refund-policies"), adminToken())
                        .content("{\"minHoursBeforeShow\":5,\"refundPercent\":150}"))
                .andExpect(status().isBadRequest());
    }
}
