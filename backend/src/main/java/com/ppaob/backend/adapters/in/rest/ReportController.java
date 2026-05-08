package com.ppaob.backend.adapters.in.rest;

import com.ppaob.backend.adapters.in.rest.dto.ReportCreateRequest;
import com.ppaob.backend.adapters.in.rest.dto.ReportResponse;
import com.ppaob.backend.application.service.ReportService;
import com.ppaob.backend.domain.model.ArtifactRecord;
import com.ppaob.backend.infrastructure.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@RestController
@RequestMapping("/api/v1/reports")
/**
 * Exposes report generation, listing and download endpoints.
 */
public class ReportController {

    private static final String HTML_REPORT = "HTML";

    private final ReportService reportService;

    /**
     * Creates a report controller that delegates generation and retrieval.
     *
     * @param reportService service used to create and fetch reports
     */
    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    /**
     * Generates a report artifact for a selected analysis.
     *
     * @param request payload with analysis identifier and optional report type
     * @param authentication authenticated requester principal
     * @return created report metadata
     * @throws IllegalArgumentException when a non-HTML type is requested
     * @throws ClassCastException if the security principal is not an {@link AuthenticatedUser}
     */
    @PostMapping
    public ReportResponse create(@Valid @RequestBody ReportCreateRequest request, Authentication authentication) {
        AuthenticatedUser user = (AuthenticatedUser) authentication.getPrincipal();
        if (request.type() != null && !request.type().isBlank() && !HTML_REPORT.equalsIgnoreCase(request.type())) {
            throw new IllegalArgumentException("Only HTML report type is currently supported");
        }

        ArtifactRecord artifact = reportService.generateHtmlReport(
                request.analysisId(),
                user.userId(),
                user.roles().contains("ADMIN")
        );
        return toResponse(artifact);
    }

    /**
     * Lists report artifacts visible to the caller.
     *
     * @param limit maximum number of reports to return
     * @param authentication authenticated requester principal
     * @return report metadata collection
     * @throws ClassCastException if the security principal is not an {@link AuthenticatedUser}
     */
    @GetMapping
    public List<ReportResponse> list(
            @RequestParam(defaultValue = "50") int limit,
            Authentication authentication
    ) {
        AuthenticatedUser user = (AuthenticatedUser) authentication.getPrincipal();
        return reportService.listReports(user.userId(), user.roles().contains("ADMIN"), limit)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Downloads a report artifact as an HTML attachment.
     *
     * @param artifactId report artifact identifier
     * @param authentication authenticated requester principal
     * @return HTML bytes with download headers
     * @throws ResponseStatusException when the selected artifact is not an HTML report
     * @throws ClassCastException if the security principal is not an {@link AuthenticatedUser}
     */
    @GetMapping("/{artifactId}/download")
    public ResponseEntity<byte[]> download(@PathVariable UUID artifactId, Authentication authentication) {
        AuthenticatedUser user = (AuthenticatedUser) authentication.getPrincipal();
        ReportService.DownloadedReport downloaded = reportService.downloadReport(
                artifactId,
                user.userId(),
                user.roles().contains("ADMIN")
        );

        if (!HTML_REPORT.equalsIgnoreCase(downloaded.artifact().type())) {
            throw new ResponseStatusException(BAD_REQUEST, "Selected artifact is not an HTML report");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.TEXT_HTML);
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(downloaded.fileName(), StandardCharsets.UTF_8)
                .build());

        return ResponseEntity.ok()
                .headers(headers)
                .body(downloaded.content());
    }

    private ReportResponse toResponse(ArtifactRecord artifact) {
        return new ReportResponse(
                artifact.artifactId(),
                artifact.analysisId(),
                artifact.binaryOriginalName(),
                artifact.type(),
                artifact.createdAt(),
                artifact.sizeBytes(),
                reportService.resolveFileName(artifact)
        );
    }
}
