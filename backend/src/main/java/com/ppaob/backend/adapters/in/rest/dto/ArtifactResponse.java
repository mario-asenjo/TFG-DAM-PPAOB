package com.ppaob.backend.adapters.in.rest.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Response payload representing an analysis artifact entry.
 *
 * @param artifactId artifact identifier
 * @param analysisId related analysis identifier
 * @param binaryOriginalName original binary filename
 * @param type artifact type label
 * @param createdAt artifact creation timestamp
 * @param sizeBytes artifact size in bytes
 * @param fileName resolved filename used for downloads
 */
public record ArtifactResponse(
        UUID artifactId,
        UUID analysisId,
        String binaryOriginalName,
        String type,
        Instant createdAt,
        long sizeBytes,
        String fileName
) {
}
