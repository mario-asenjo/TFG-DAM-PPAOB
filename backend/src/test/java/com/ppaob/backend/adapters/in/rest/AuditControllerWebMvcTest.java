package com.ppaob.backend.adapters.in.rest;

import com.ppaob.backend.application.service.AuditEventFilter;
import com.ppaob.backend.application.service.AuditService;
import com.ppaob.backend.domain.model.AuditEventRecord;
import com.ppaob.backend.infrastructure.security.JwtService;
import com.ppaob.backend.support.TestAuth;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AuditController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuditControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuditService auditService;

    @MockBean
    private JwtService jwtService;

    @Test
    void listEventsAcceptsAdvancedFilters() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID analysisId = UUID.randomUUID();
        UUID binaryId = UUID.randomUUID();
        Instant now = Instant.now();

        given(auditService.listByFilter(any())).willReturn(List.of(
                new AuditEventRecord(UUID.randomUUID(), now, "LOGIN", "SUCCESS", userId, "admin@ppaob.local", analysisId, binaryId, "unsafe_strcpy", "{}")
        ));

        mockMvc.perform(get("/api/v1/audit/events")
                        .queryParam("action", "LOGIN")
                        .queryParam("result", "SUCCESS")
                        .queryParam("userId", userId.toString())
                        .queryParam("analysisId", analysisId.toString())
                        .queryParam("binaryId", binaryId.toString())
                        .queryParam("from", "2026-05-01T00:00:00Z")
                        .queryParam("to", "2026-05-02T00:00:00Z")
                        .queryParam("limit", "25")
                        .queryParam("offset", "5")
                        .principal(TestAuth.token(UUID.randomUUID(), "admin@ppaob.local", Set.of("ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].action").value("LOGIN"));

        ArgumentCaptor<AuditEventFilter> captor = ArgumentCaptor.forClass(AuditEventFilter.class);
        verify(auditService).listByFilter(captor.capture());
        AuditEventFilter sent = captor.getValue();
        assertEquals("LOGIN", sent.action());
        assertEquals("SUCCESS", sent.result());
        assertEquals(userId, sent.userId());
        assertEquals(analysisId, sent.analysisId());
        assertEquals(binaryId, sent.binaryId());
        assertEquals(25, sent.limit());
        assertEquals(5, sent.offset());
    }
}
