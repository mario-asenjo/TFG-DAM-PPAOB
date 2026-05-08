package com.ppaob.backend.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ppaob.backend.application.port.out.ArtifactRepositoryPort;
import com.ppaob.backend.application.port.out.ObjectStoragePort;
import com.ppaob.backend.domain.model.ArtifactRecord;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Service
/**
 * Provides read/download operations for analysis artifacts.
 *
 * <p>This service applies requester visibility constraints via repository ports, validates object
 * availability in storage, and offers optional NDJSON-to-pretty-JSON rendering for dynamic traces.
 */
public class ArtifactService {

    private final ArtifactRepositoryPort artifacts;
    private final ObjectStoragePort storage;
    private final ObjectMapper objectMapper;

/**
 * Creates the artifact service.
 *
 * @param artifacts artifact repository used for visibility-aware metadata access
 * @param storage object storage port used to verify/download artifact payloads
 * @param objectMapper JSON mapper used by pretty-trace rendering
 */
    public ArtifactService(ArtifactRepositoryPort artifacts, ObjectStoragePort storage, ObjectMapper objectMapper) {
        this.artifacts = artifacts;
        this.storage = storage;
        this.objectMapper = objectMapper;
    }

/**
 * Lists artifacts visible to the requester with optional filters.
 *
 * <p>The service requests up to 200 visible artifacts from persistence and applies in-memory
 * filtering by {@code analysisId} and {@code type}. Returned list length is capped by a sanitized
 * limit in {@code [1, 200]}.
 *
 * @param requesterId authenticated requester user id
 * @param requesterIsAdmin whether requester has admin visibility
 * @param analysisId optional analysis filter; {@code null} means all visible analyses
 * @param type optional artifact type filter; blank/null means no type filter
 * @param limit requested maximum number of returned items
 * @return filtered list of artifact metadata records
 */
    public List<ArtifactRecord> listArtifacts(UUID requesterId, boolean requesterIsAdmin, UUID analysisId, String type, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 200));
        String normalizedType = normalizeType(type);
        return artifacts.listForRequester(requesterId, requesterIsAdmin, 200)
                .stream()
                .filter(artifact -> analysisId == null || artifact.analysisId().equals(analysisId))
                .filter(artifact -> normalizedType == null || normalizedType.equalsIgnoreCase(artifact.type()))
                .limit(safeLimit)
                .toList();
    }

/**
 * Downloads an artifact payload when visible to the requester.
 *
 * <p>Side effects: reads artifact bytes from object storage.
 *
 * @param artifactId artifact identifier
 * @param requesterId authenticated requester user id
 * @param requesterIsAdmin whether requester has admin visibility
 * @return artifact metadata plus payload bytes and response metadata
 * @throws IllegalArgumentException when artifact is not visible or backing object is missing
 */
    public DownloadedArtifact downloadArtifact(UUID artifactId, UUID requesterId, boolean requesterIsAdmin) {
        ArtifactRecord artifact = artifacts.findByIdForRequester(artifactId, requesterId, requesterIsAdmin)
                .orElseThrow(() -> new IllegalArgumentException("Artifact not found"));

        if (!storage.exists(artifact.bucket(), artifact.objectKey())) {
            throw new IllegalArgumentException("Artifact object is not available in storage");
        }

        byte[] payload = storage.download(artifact.bucket(), artifact.objectKey());
        return new DownloadedArtifact(artifact, payload, resolveFileName(artifact), resolveContentType(artifact.type()));
    }

/**
 * Downloads a dynamic trace artifact and returns a pretty-printed JSON array view.
 *
 * <p>Input NDJSON lines that fail JSON parsing are skipped to preserve best-effort rendering.
 *
 * <p>Side effects: reads artifact bytes from object storage.
 *
 * @param artifactId artifact identifier
 * @param requesterId authenticated requester user id
 * @param requesterIsAdmin whether requester has admin visibility
 * @return transformed artifact payload with {@code .pretty.json} filename and JSON content type
 * @throws IllegalArgumentException when artifact is not visible, missing in storage, or not of
 *                                  type {@code DYNAMIC_TRACE}
 */
    public DownloadedArtifact downloadPrettyTrace(UUID artifactId, UUID requesterId, boolean requesterIsAdmin) {
        DownloadedArtifact downloaded = downloadArtifact(artifactId, requesterId, requesterIsAdmin);
        if (!"DYNAMIC_TRACE".equalsIgnoreCase(downloaded.artifact().type())) {
            throw new IllegalArgumentException("Pretty trace is only available for DYNAMIC_TRACE artifacts");
        }

        String ndjson = new String(downloaded.content(), StandardCharsets.UTF_8);
        StringBuilder out = new StringBuilder();
        out.append("[\n");
        String[] lines = ndjson.split("\\R");
        boolean first = true;
        for (String line : lines) {
            if (line == null || line.isBlank()) {
                continue;
            }
            try {
                String pretty = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(objectMapper.readTree(line));
                if (!first) {
                    out.append(",\n");
                }
                out.append(pretty);
                first = false;
            } catch (Exception ignored) {
            }
        }
        out.append("\n]\n");

        String fileName = downloaded.fileName().replace(".ndjson", ".pretty.json");
        return new DownloadedArtifact(downloaded.artifact(), out.toString().getBytes(StandardCharsets.UTF_8), fileName, "application/json; charset=utf-8");
    }

/**
 * Resolves the default download filename for an artifact.
 *
 * @param artifact artifact metadata
 * @return deterministic filename with UTC timestamp and extension based on artifact type
 */
    public String resolveFileName(ArtifactRecord artifact) {
        String stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
                .withZone(ZoneOffset.UTC)
                .format(artifact.createdAt());
        if ("DYNAMIC_TRACE".equalsIgnoreCase(artifact.type())) {
            return "analysis-" + artifact.analysisId() + "-" + stamp + ".ndjson";
        }
        return "analysis-" + artifact.analysisId() + "-" + stamp + ".html";
    }

    private String resolveContentType(String type) {
        if ("DYNAMIC_TRACE".equalsIgnoreCase(type)) {
            return "application/x-ndjson; charset=utf-8";
        }
        return "text/html; charset=utf-8";
    }

    private String normalizeType(String type) {
        if (type == null || type.isBlank()) {
            return null;
        }
        return type.trim().toUpperCase();
    }

/**
 * Transport object for artifact download responses.
 *
 * @param artifact artifact metadata
 * @param content artifact payload bytes
 * @param fileName filename suggested for download
 * @param contentType MIME type to expose to clients
 */
    public record DownloadedArtifact(ArtifactRecord artifact, byte[] content, String fileName, String contentType) {
        /**
         * Returns artifact metadata.
         *
         * @return artifact metadata
         */
        @Override
        public ArtifactRecord artifact() {
            return artifact;
        }
    }
}
