package com.ppaob.backend.adapters.in.rest;

import com.ppaob.backend.application.service.AdminUserService;
import com.ppaob.backend.domain.model.UserAccount;
import com.ppaob.backend.infrastructure.security.JwtAuthenticationFilter;
import com.ppaob.backend.infrastructure.security.JwtService;
import com.ppaob.backend.infrastructure.security.SecurityConfig;
import com.ppaob.backend.support.TestAuth;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AdminUserController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class AdminUserControllerSecurityWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AdminUserService adminUserService;

    @MockBean
    private JwtService jwtService;

    @Test
    void nonAdminGetsForbiddenWithUnifiedErrorPayload() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users")
                        .with(authentication(TestAuth.token(UUID.randomUUID(), "viewer@ppaob.local", Set.of("VIEWER")))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"))
                .andExpect(jsonPath("$.path").value("/api/v1/admin/users"));
    }

    @Test
    void adminGetsUsersList() throws Exception {
        UserAccount account = new UserAccount(UUID.randomUUID(), "analyst@ppaob.local", "hash", true, Set.of("ANALYST"));
        given(adminUserService.listUsers()).willReturn(List.of(account));

        mockMvc.perform(get("/api/v1/admin/users")
                        .with(authentication(TestAuth.token(UUID.randomUUID(), "admin@ppaob.local", Set.of("ADMIN")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].email").value("analyst@ppaob.local"));
    }
}
