package com.moviebooking.pricing;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.moviebooking.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class PricingAdminIntegrationTest extends IntegrationTest {

    @Test
    void listAndUpsertPricingTiers() throws Exception {
        String admin = adminToken();
        mockMvc.perform(authed(get("/api/admin/pricing-tiers"), admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.code=='SEAT_PREMIUM')]").exists());

        mockMvc.perform(authed(put("/api/admin/pricing-tiers"), admin)
                        .content("{\"code\":\"SEAT_PREMIUM\",\"description\":\"Premium\",\"multiplier\":2.0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.multiplier").value(2.0));

        long newTierId = ((Number) com.jayway.jsonpath.JsonPath.read(
                mockMvc.perform(authed(put("/api/admin/pricing-tiers"), admin)
                                .content("{\"code\":\"HOLIDAY\",\"description\":\"Holiday\",\"multiplier\":1.4}"))
                        .andReturn().getResponse().getContentAsString(), "$.id")).longValue();

        mockMvc.perform(authed(delete("/api/admin/pricing-tiers/" + newTierId), admin))
                .andExpect(status().isNoContent());
    }

    @Test
    void deletingUnknownTierReturnsNotFound() throws Exception {
        mockMvc.perform(authed(delete("/api/admin/pricing-tiers/9999"), adminToken()))
                .andExpect(status().isNotFound());
    }

    @Test
    void discountCodeCrudAndPreview() throws Exception {
        String admin = adminToken();
        long id = createId(post("/api/admin/discount-codes"), admin,
                "{\"code\":\"WELCOME\",\"type\":\"PERCENT\",\"amount\":15,\"minAmount\":100,\"maxUses\":5}");

        mockMvc.perform(authed(get("/api/admin/discount-codes"), admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.code=='WELCOME')]").exists());

        mockMvc.perform(authed(put("/api/admin/discount-codes/" + id), admin)
                        .content("{\"code\":\"WELCOME\",\"type\":\"PERCENT\",\"amount\":20,\"active\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(20));

        String customer = registerCustomer("shopper@example.com");
        mockMvc.perform(authed(post("/api/discounts/preview"), customer)
                        .content("{\"code\":\"WELCOME\",\"subtotal\":1000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.discountAmount").value(200.0))
                .andExpect(jsonPath("$.total").value(800.0));

        mockMvc.perform(authed(delete("/api/admin/discount-codes/" + id), admin))
                .andExpect(status().isNoContent());
    }

    @Test
    void duplicateDiscountCodeIsRejected() throws Exception {
        String admin = adminToken();
        createId(post("/api/admin/discount-codes"), admin, "{\"code\":\"DUP\",\"type\":\"FLAT\",\"amount\":50}");
        mockMvc.perform(authed(post("/api/admin/discount-codes"), admin)
                        .content("{\"code\":\"dup\",\"type\":\"FLAT\",\"amount\":10}"))
                .andExpect(status().isConflict());
    }

    @Test
    void updatingUnknownDiscountCodeReturnsNotFound() throws Exception {
        mockMvc.perform(authed(put("/api/admin/discount-codes/9999"), adminToken())
                        .content("{\"code\":\"X\",\"type\":\"FLAT\",\"amount\":10}"))
                .andExpect(status().isNotFound());
    }
}
