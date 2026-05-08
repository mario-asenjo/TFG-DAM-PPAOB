package com.ppaob.backend.adapters.in.rest.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Response payload representing a generated report artifact.
 *
 * @param artifactId report artifact identifier
 * @param analysisId related analysis identifier
 * @param binaryOriginalName original binary filename
 * @param type report type label
 * @param createdAt report creation timestamp
 * @param sizeBytes report size in bytes
 * @param fileName resolved filename used for downloads
 */
public record ReportResponse(
        UUID artifactId,
        UUID analysisId,
        String binaryOriginalName,
        String type,
        Instant createdAt,
        long sizeBytes,
        String fileName
) {
}
