package com.ppaob.backend.adapters.in.rest;

import com.ppaob.backend.adapters.in.rest.dto.ArtifactResponse;
import com.ppaob.backend.application.service.ArtifactService;
import com.ppaob.backend.domain.model.ArtifactRecord;
import com.ppaob.backend.infrastructure.security.AuthenticatedUser;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/artifacts")
/**
 * Exposes endpoints to list and download artifacts produced by analyses.
 */
public class ArtifactController {

    private final ArtifactService artifactService;

    /**
     * Creates a controller that delegates artifact retrieval to the service layer.
     *
     * @param artifactService service used for artifact listing and content download
     */
    public ArtifactController(ArtifactService artifactService) {
        this.artifactService = artifactService;
    }

    /**
     * Lists artifacts visible to the authenticated user.
     *
     * @param analysisId optional analysis identifier filter
     * @param type optional artifact type filter
     * @param limit maximum number of artifacts to return
     * @param authentication authenticated requester principal
     * @return artifact metadata collection
     * @throws ClassCastException if the security principal is not an {@link AuthenticatedUser}
     */
    @GetMapping
    public List<ArtifactResponse> list(
            @RequestParam(required = false) UUID analysisId,
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "50") int limit,
            Authentication authentication
    ) {
        AuthenticatedUser user = (AuthenticatedUser) authentication.getPrincipal();
        return artifactService.listArtifacts(user.userId(), user.roles().contains("ADMIN"), analysisId, type, limit)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Downloads an artifact as an attachment response.
     *
     * @param artifactId artifact identifier
     * @param authentication authenticated requester principal
     * @return binary response with content-type and content-disposition headers
     * @throws ClassCastException if the security principal is not an {@link AuthenticatedUser}
     */
    @GetMapping("/{artifactId}/download")
    public ResponseEntity<byte[]> download(@PathVariable UUID artifactId, Authentication authentication) {
        AuthenticatedUser user = (AuthenticatedUser) authentication.getPrincipal();
        ArtifactService.DownloadedArtifact downloaded = artifactService.downloadArtifact(
                artifactId,
                user.userId(),
                user.roles().contains("ADMIN")
        );
        return toDownloadResponse(downloaded);
    }

    /**
     * Downloads a pretty-printed trace representation when supported by the artifact.
     *
     * @param artifactId artifact identifier
     * @param authentication authenticated requester principal
     * @return binary response containing a transformed human-readable trace
     * @throws ClassCastException if the security principal is not an {@link AuthenticatedUser}
     */
    @GetMapping("/{artifactId}/download/pretty")
    public ResponseEntity<byte[]> downloadPretty(@PathVariable UUID artifactId, Authentication authentication) {
        AuthenticatedUser user = (AuthenticatedUser) authentication.getPrincipal();
        ArtifactService.DownloadedArtifact downloaded = artifactService.downloadPrettyTrace(
                artifactId,
                user.userId(),
                user.roles().contains("ADMIN")
        );
        return toDownloadResponse(downloaded);
    }

    private ResponseEntity<byte[]> toDownloadResponse(ArtifactService.DownloadedArtifact downloaded) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(downloaded.contentType()));
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(downloaded.fileName(), StandardCharsets.UTF_8)
                .build());
        return ResponseEntity.ok().headers(headers).body(downloaded.content());
    }

    private ArtifactResponse toResponse(ArtifactRecord artifact) {
        return new ArtifactResponse(
                artifact.artifactId(),
                artifact.analysisId(),
                artifact.binaryOriginalName(),
                artifact.type(),
                artifact.createdAt(),
                artifact.sizeBytes(),
                artifactService.resolveFileName(artifact)
        );
    }
}
