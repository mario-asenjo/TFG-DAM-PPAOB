package com.ppaob.backend.application.service;

import com.ppaob.backend.application.port.out.AnalysisRepositoryPort;
import com.ppaob.backend.application.port.out.ArtifactRepositoryPort;
import com.ppaob.backend.application.port.out.ObjectStoragePort;
import com.ppaob.backend.domain.model.AnalysisRecord;
import com.ppaob.backend.domain.model.AnalysisResultRecord;
import com.ppaob.backend.domain.model.ArtifactRecord;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReportServiceTest {

    @Test
    void generateHtmlReportRendersExecutiveAndEthicalSections() {
        AnalysisRepositoryPort analyses = mock(AnalysisRepositoryPort.class);
        ArtifactRepositoryPort artifacts = mock(ArtifactRepositoryPort.class);
        ObjectStoragePort storage = mock(ObjectStoragePort.class);
        AuditService auditService = mock(AuditService.class);

        UUID analysisId = UUID.randomUUID();
        UUID binaryId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();

        AnalysisRecord analysis = new AnalysisRecord(
                analysisId,
                binaryId,
                "sample.bin",
                requesterId,
                "analyst@ppaob.local",
                "DYNAMIC_BASELINE",
                "DONE",
                Instant.now(),
                Instant.now(),
                Instant.now(),
                null
        );

        String resultJson = """
                {
                  "schemaVersion":1,
                  "metadata":{"requestedProfile":"DYNAMIC_BASELINE"},
                  "summary":{"riskLevel":"HIGH","riskScore":91,"findingsCount":2},
                  "correlation":{"priority":"P1","environmentProfile":"LINUX_SERVER","topReasons":["Network to command chain"]},
                  "dynamic":{"runtime":{"durationMs":12,"exitCode":0,"timedOut":false},"policy":{"name":"SECCOMP_BASELINE_V1","deniedCount":1},"topSyscalls":[{"name":"execve","count":2}]},
                  "signals":[{"id":"DYN_TIMEOUT","kind":"EXECUTION_TIMEOUT","severity":"HIGH","title":"Execution timeout"}],
                  "artifacts":[{"artifactId":"abc","type":"DYNAMIC_TRACE","bucket":"ppaob-binaries","objectKey":"artifacts/trace.ndjson","sizeBytes":12}]
                }
                """;

        AnalysisResultRecord result = new AnalysisResultRecord(analysisId, 1, resultJson, Instant.now());

        when(analyses.findById(any(UUID.class), any(UUID.class), anyBoolean())).thenReturn(Optional.of(analysis));
        when(analyses.findResultByAnalysisId(any(UUID.class), any(UUID.class), anyBoolean())).thenReturn(Optional.of(result));
        when(artifacts.create(any(UUID.class), anyString(), anyString(), anyString(), anyString(), anyLong())).thenReturn(
                new ArtifactRecord(UUID.randomUUID(), analysisId, "sample.bin", "HTML", Instant.now(), UUID.randomUUID(), "ppaob-binaries", "reports/x.html", 100)
        );
        doNothing().when(storage).upload(anyString(), anyString(), any(byte[].class), anyString());

        ReportService service = new ReportService(
                analyses,
                artifacts,
                storage,
                new com.fasterxml.jackson.databind.ObjectMapper(),
                auditService,
                "ppaob-binaries"
        );

        service.generateHtmlReport(analysisId, requesterId, false);

        var payloadCaptor = org.mockito.ArgumentCaptor.forClass(byte[].class);
        verify(storage).upload(anyString(), anyString(), payloadCaptor.capture(), anyString());
        String html = new String(payloadCaptor.getValue(), StandardCharsets.UTF_8);
        assertTrue(html.contains("Executive Summary"));
        assertTrue(html.contains("Dynamic Summary"));
        assertTrue(html.contains("Ethical Scope Disclaimer"));
        assertTrue(html.contains("Risk Score"));
    }
}
