package com.ppaob.backend.adapters.in.rest;

import com.ppaob.backend.application.service.AuthService;
import com.ppaob.backend.domain.model.UserAccount;
import com.ppaob.backend.infrastructure.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuthService authService;

    @MockBean
    private JwtService jwtService;

    @Test
    void loginReturnsTokenAndRefreshCookie() throws Exception {
        JwtService.JwtToken token = new JwtService.JwtToken("access-token", Instant.now().plusSeconds(900), "admin@ppaob.local", Set.of("ADMIN"));
        given(authService.login(eq("admin@ppaob.local"), eq("password"), any(), any()))
                .willReturn(new AuthService.AuthSessionResult(token, "refresh-token"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"admin@ppaob.local","password":"password"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("access-token"))
                .andExpect(cookie().exists("ppaob_refresh"));
    }

    @Test
    void refreshWithoutCookieReturnsUnifiedUnauthorizedError() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.path").value("/api/v1/auth/refresh"));
    }

    @Test
    void registerWithInvalidEmailReturnsValidationDetails() throws Exception {
        given(authService.register(any(), any()))
                .willReturn(new UserAccount(UUID.randomUUID(), "user@ppaob.local", "hash", true, Set.of("VIEWER")));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"bad-email","password":"12345678"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.details.fieldErrors").isArray());
    }
}
