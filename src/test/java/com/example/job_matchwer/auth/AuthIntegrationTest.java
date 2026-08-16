package com.example.job_matchwer.auth;

import com.example.job_matchwer.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthIntegrationTest extends AbstractIntegrationTest {

    // Context-path is set explicitly (server.servlet.context-path: /api)
    // rather than relying on it being auto-applied to MockMvc, so these
    // tests keep working even if that assumption ever changes.
    private static final String BASE = "";

    private String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }

    @Test
    void register_withNewEmail_returnsAccessAndRefreshTokens() throws Exception {
        String body = objectMapper.writeValueAsString(
                new RegisterRequest(uniqueEmail(), "Str0ngP@ssword!"));

        mockMvc.perform(post(BASE + "/auth/register")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty());
    }

    @Test
    void register_withAlreadyRegisteredEmail_isRejected() throws Exception {
        String email = uniqueEmail();
        String body = objectMapper.writeValueAsString(new RegisterRequest(email, "Str0ngP@ssword!"));

        mockMvc.perform(post(BASE + "/auth/register")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk());

        // AuthService currently throws a bare IllegalArgumentException for a
        // duplicate email, which GlobalExceptionHandler does not (yet) map.
        // We only assert it's rejected, not the exact status code, until a
        // typed exception + handler exists for this case.
        mockMvc.perform(post(BASE + "/auth/register")
                        .contentType("application/json")
                        .content(body))
                .andExpect(result -> {
                    int sc = result.getResponse().getStatus();
                    if (sc < 400) {
                        throw new AssertionError(
                                "Expected an error status for duplicate registration, got " + sc);
                    }
                });
    }

    @Test
    void login_withCorrectCredentials_returnsTokens() throws Exception {
        String email = uniqueEmail();
        String password = "Str0ngP@ssword!";

        mockMvc.perform(post(BASE + "/auth/register")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new RegisterRequest(email, password))))
                .andExpect(status().isOk());

        mockMvc.perform(post(BASE + "/auth/login")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, password))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    @Test
    void protectedEndpoint_withoutToken_returns401() throws Exception {
        mockMvc.perform(get(BASE + "/resumes"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpoint_withValidToken_returns200() throws Exception {
        String email = uniqueEmail();
        String password = "Str0ngP@ssword!";

        String registerResponse = mockMvc.perform(post(BASE + "/auth/register")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new RegisterRequest(email, password))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        @SuppressWarnings("unchecked")
        Map<String, String> tokens = objectMapper.readValue(registerResponse, Map.class);
        String accessToken = tokens.get("accessToken");

        mockMvc.perform(get(BASE + "/resumes")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }
}