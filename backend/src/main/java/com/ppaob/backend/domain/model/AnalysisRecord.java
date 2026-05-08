package com.ppaob.backend.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain representation of an analysis request lifecycle.
 *
 * <p>Lifecycle timestamps model progression from request creation to completion.
 * Typical state progression is created - started - finished; when processing fails,
 * {@code errorSummary} captures a concise reason while preserving pre-exploitation framing.
 *
 * @param analysisId unique analysis identifier.
 * @param binaryId binary identifier selected for analysis.
 * @param binaryOriginalName original binary name snapshot shown to users.
 * @param requestedBy user identifier that initiated the request.
 * @param requestedByEmail requester email snapshot for traceability.
 * @param profile analysis profile/configuration name requested by the user.
 * @param status current analysis status label.
 * @param createdAt request creation timestamp.
 * @param startedAt processing start timestamp, or {@code null} before execution starts.
 * @param finishedAt processing end timestamp, or {@code null} while still running.
 * @param errorSummary optional short error summary for failed analyses.
 */
public record AnalysisRecord(
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
