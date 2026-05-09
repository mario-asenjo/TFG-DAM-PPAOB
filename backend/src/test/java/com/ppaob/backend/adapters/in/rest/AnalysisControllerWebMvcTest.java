package com.ppaob.backend.adapters.in.rest;

import com.ppaob.backend.application.service.AnalysisService;
import com.ppaob.backend.domain.model.AnalysisRecord;
import com.ppaob.backend.domain.model.AnalysisResultRecord;
import com.ppaob.backend.infrastructure.security.JwtService;
import com.ppaob.backend.support.TestAuth;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AnalysisController.class)
@AutoConfigureMockMvc(addFilters = false)
class AnalysisControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AnalysisService analysisService;

    @MockBean
    private JwtService jwtService;

    @Test
    void createAnalysisPassesRequesterContextForOwnership() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID binaryId = UUID.randomUUID();
        UUID analysisId = UUID.randomUUID();
        AnalysisRecord created = new AnalysisRecord(analysisId, binaryId, "unsafe_strcpy", userId, "analyst@ppaob.local", "DYNAMIC_BASELINE", "PENDING", Instant.now(), null, null, null);

        given(analysisService.request(eq(binaryId), eq("DYNAMIC_BASELINE"), eq(userId), eq(false))).willReturn(created);

        mockMvc.perform(post("/api/v1/analyses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{" +
                                "\"binaryId\":\"" + binaryId + "\"," +
                                "\"profile\":\"DYNAMIC_BASELINE\"}")
                        .principal(TestAuth.token(userId, "analyst@ppaob.local", Set.of("ANALYST"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.analysisId").value(analysisId.toString()))
                .andExpect(jsonPath("$.binaryOriginalName").value("unsafe_strcpy"))
                .andExpect(jsonPath("$.profile").value("DYNAMIC_BASELINE"));
    }

    @Test
    void listAnalysesPassesAdminFlagAndLimit() throws Exception {
        UUID userId = UUID.randomUUID();
        AnalysisRecord row = new AnalysisRecord(UUID.randomUUID(), UUID.randomUUID(), "hello", userId, "admin@ppaob.local", "STATIC_BASELINE", "DONE", Instant.now(), Instant.now(), Instant.now(), null);
        given(analysisService.list(eq(userId), eq(true), eq(null), eq(5))).willReturn(List.of(row));

        mockMvc.perform(get("/api/v1/analyses?limit=5")
                        .principal(TestAuth.token(userId, "admin@ppaob.local", Set.of("ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].analysisId").value(row.analysisId().toString()));
    }

    @Test
    void getResultsReturnsStructuredPayload() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID analysisId = UUID.randomUUID();
        given(analysisService.getResultByAnalysisId(eq(analysisId), eq(userId), eq(false)))
                .willReturn(new AnalysisResultRecord(analysisId, 1, "{\"summary\":{\"riskLevel\":\"LOW\"}}", Instant.now()));

        mockMvc.perform(get("/api/v1/analyses/{analysisId}/results", analysisId)
                        .principal(TestAuth.token(userId, "analyst@ppaob.local", Set.of("ANALYST"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results.summary.riskLevel").value("LOW"));
    }

    @Test
    void invalidAnalysisIdReturnsUnifiedBadRequestError() throws Exception {
        mockMvc.perform(get("/api/v1/analyses/not-a-uuid")
                        .principal(TestAuth.token(UUID.randomUUID(), "analyst@ppaob.local", Set.of("ANALYST"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.path").value("/api/v1/analyses/not-a-uuid"));
    }
}
