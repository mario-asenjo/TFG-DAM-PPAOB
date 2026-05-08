package com.ppaob.backend.adapters.in.rest;

import com.ppaob.backend.application.service.ArtifactService;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ArtifactController.class)
@AutoConfigureMockMvc(addFilters = false)
class ArtifactControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ArtifactService artifactService;

    @MockBean
    private JwtService jwtService;

    @Test
    void listArtifactsUsesRequesterOwnershipContext() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID analysisId = UUID.randomUUID();
        ArtifactRecord row = new ArtifactRecord(
                UUID.randomUUID(),
                analysisId,
                "unsafe_strcpy",
                "DYNAMIC_TRACE",
                Instant.now(),
                UUID.randomUUID(),
                "ppaob-binaries",
                "artifacts/t.ndjson",
                1024
        );
        given(artifactService.listArtifacts(eq(userId), eq(false), eq(analysisId), eq("DYNAMIC_TRACE"), eq(3)))
                .willReturn(List.of(row));
        given(artifactService.resolveFileName(eq(row))).willReturn("analysis-trace.ndjson");

        mockMvc.perform(get("/api/v1/artifacts")
                        .param("analysisId", analysisId.toString())
                        .param("type", "DYNAMIC_TRACE")
                        .param("limit", "3")
                        .principal(TestAuth.token(userId, "analyst@ppaob.local", Set.of("ANALYST"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].artifactId").value(row.artifactId().toString()))
                .andExpect(jsonPath("$[0].binaryOriginalName").value("unsafe_strcpy"));
    }

    @Test
    void downloadReturnsAttachmentHeaders() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID artifactId = UUID.randomUUID();
        ArtifactRecord record = new ArtifactRecord(
                artifactId,
                UUID.randomUUID(),
                "unsafe_strcpy",
                "DYNAMIC_TRACE",
                Instant.now(),
                UUID.randomUUID(),
                "ppaob-binaries",
                "artifacts/x.ndjson",
                12
        );
        given(artifactService.downloadArtifact(eq(artifactId), eq(userId), eq(false)))
                .willReturn(new ArtifactService.DownloadedArtifact(
                        record,
                        "{}\n".getBytes(),
                        "trace.ndjson",
                        "application/x-ndjson; charset=utf-8"
                ));

        mockMvc.perform(get("/api/v1/artifacts/{artifactId}/download", artifactId)
                .principal(TestAuth.token(userId, "analyst@ppaob.local", Set.of("ANALYST"))))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/x-ndjson;charset=utf-8"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("trace.ndjson")));
    }

    @Test
    void prettyDownloadReturnsJsonHeaders() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID artifactId = UUID.randomUUID();
        ArtifactRecord record = new ArtifactRecord(
                artifactId,
                UUID.randomUUID(),
                "unsafe_strcpy",
                "DYNAMIC_TRACE",
                Instant.now(),
                UUID.randomUUID(),
                "ppaob-binaries",
                "artifacts/x.ndjson",
                12
        );
        given(artifactService.downloadPrettyTrace(eq(artifactId), eq(userId), eq(false)))
                .willReturn(new ArtifactService.DownloadedArtifact(
                        record,
                        "[]\n".getBytes(),
                        "trace.pretty.json",
                        MediaType.APPLICATION_JSON_VALUE
                ));

        mockMvc.perform(get("/api/v1/artifacts/{artifactId}/download/pretty", artifactId)
                        .principal(TestAuth.token(userId, "analyst@ppaob.local", Set.of("ANALYST"))))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", MediaType.APPLICATION_JSON_VALUE))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("trace.pretty.json")));
    }
}
