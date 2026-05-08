package com.ppaob.backend.adapters.in.rest;

import com.ppaob.backend.application.service.ReportService;
import com.ppaob.backend.domain.model.ArtifactRecord;
import com.ppaob.backend.infrastructure.security.JwtService;
import com.ppaob.backend.support.TestAuth;
import org.junit.jupiter.api.Test;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ReportController.class)
@AutoConfigureMockMvc(addFilters = false)
class ReportControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ReportService reportService;

    @MockBean
    private JwtService jwtService;

    @Test
    void createRejectsUnsupportedReportTypeWithUnifiedError() throws Exception {
        mockMvc.perform(post("/api/v1/reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{" +
                                "\"analysisId\":\"" + UUID.randomUUID() + "\"," +
                                "\"type\":\"PDF\"}")
                        .principal(TestAuth.token(UUID.randomUUID(), "analyst@ppaob.local", Set.of("ANALYST"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Only HTML report type is currently supported"));
    }

    @Test
    void listReportsUsesRequesterOwnershipContext() throws Exception {
        UUID userId = UUID.randomUUID();
        ArtifactRecord row = new ArtifactRecord(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "unsafe_strcpy",
                "HTML",
                Instant.now(),
                UUID.randomUUID(),
                "ppaob-binaries",
                "reports/a.html",
                1024
        );
        given(reportService.listReports(eq(userId), eq(false), eq(3))).willReturn(List.of(row));
        given(reportService.resolveFileName(eq(row))).willReturn("analysis-report.html");

        mockMvc.perform(get("/api/v1/reports?limit=3")
                        .principal(TestAuth.token(userId, "analyst@ppaob.local", Set.of("ANALYST"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].artifactId").value(row.artifactId().toString()));
    }

    @Test
    void downloadReturnsAttachmentHeaders() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID artifactId = UUID.randomUUID();
        ArtifactRecord record = new ArtifactRecord(
                artifactId,
                UUID.randomUUID(),
                "unsafe_strcpy",
                "HTML",
                Instant.now(),
                UUID.randomUUID(),
                "ppaob-binaries",
                "reports/x.html",
                12
        );
        given(reportService.downloadReport(eq(artifactId), eq(userId), eq(false)))
                .willReturn(new ReportService.DownloadedReport(record, "<html></html>".getBytes(), "report.html"));

        mockMvc.perform(get("/api/v1/reports/{artifactId}/download", artifactId)
                        .principal(TestAuth.token(userId, "analyst@ppaob.local", Set.of("ANALYST"))))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/html"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("report.html")));
    }
}
