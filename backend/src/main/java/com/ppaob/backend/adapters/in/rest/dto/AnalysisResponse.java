package com.ppaob.backend.adapters.in.rest.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Response payload with analysis metadata and execution status timestamps.
 *
 * @param analysisId analysis identifier
 * @param binaryId analyzed binary identifier
 * @param binaryOriginalName original filename of the binary
 * @param requestedBy user identifier who requested the analysis
 * @param requestedByEmail requester email
 * @param profile selected profile name
 * @param status current lifecycle status
 * @param createdAt request creation timestamp
 * @param startedAt execution start timestamp, when available
 * @param finishedAt execution finish timestamp, when available
 * @param errorSummary short failure reason when status is failed
 */
public record AnalysisResponse(
        UUID analysisId,
        UUID binaryId,
        String binaryOriginalName,
        UUID requestedBy,
        String requestedByEmail,
        String profile,
        String status,
        Instant createdAt,
        Instant startedAt,
        Instant finishedAt,
        String errorSummary
) {
}
