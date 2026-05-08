package com.ppaob.backend.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable audit trail event emitted by backend actions.
 *
 * <p>The record stores actor, target resources and optional structured details.
 * {@code detailsJson} is an opaque JSON payload intended for traceability and compliance,
 * not for driving domain decisions.
 *
 * @param eventId unique audit event identifier.
 * @param ts event timestamp.
 * @param action normalized action name that was attempted.
 * @param result action outcome label (for example success/failure).
 * @param userId acting user identifier, or {@code null} for non-user events.
 * @param userEmail acting user email snapshot, when available.
 * @param analysisId related analysis identifier, when the event targets an analysis.
 * @param binaryId related binary identifier, when the event targets a binary.
 * @param binaryOriginalName related binary name snapshot, when available.
 * @param detailsJson optional JSON object serialized as text with extra event context.
 */
public record AuditEventRecord(
        UUID eventId,
        Instant ts,
        String action,
        String result,
        UUID userId,
        String userEmail,
        UUID analysisId,
        UUID binaryId,
        String binaryOriginalName,
        String detailsJson
) {
}
