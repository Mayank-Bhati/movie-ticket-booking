package com.moviebooking.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.moviebooking.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class AuthIntegrationTest extends IntegrationTest {

    @Test
    void registerThenLoginReturnsToken() throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"alice@example.com","password":"secret123","fullName":"Alice"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").exists())
                .andExpect(jsonPath("$.role").value("CUSTOMER"));

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"alice@example.com","password":"secret123"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists());
    }

    @Test
    void invalidPayloadIsRejectedWithValidationErrors() throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"not-an-email","password":"x","fullName":""}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.email").exists())
                .andExpect(jsonPath("$.fieldErrors.password").exists());
    }

    @Test
    void duplicateEmailIsRejected() throws Exception {
        String body = """
                {"email":"bob@example.com","password":"secret123","fullName":"Bob"}""";
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void adminEndpointRejectsAnonymousAndCustomer() throws Exception {
        // No token -> 401
        mockMvc.perform(post("/api/admin/cities").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Pune"}"""))
                .andExpect(status().isUnauthorized());

        // Customer token -> 403
        String token = registerCustomer("carol@example.com");
        mockMvc.perform(post("/api/admin/cities").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Pune"}"""))
                .andExpect(status().isForbidden());
    }

    @Test
    void seededAdminCanReachAdminEndpoints() throws Exception {
        mockMvc.perform(get("/api/admin/pricing-tiers").header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk());
    }
}
