package com.ppaob.backend.adapters.in.rest.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Response payload representing a persisted audit event.
 *
 * @param eventId event identifier
 * @param ts event timestamp
 * @param action audited action code
 * @param result action result code
 * @param userId acting user identifier when available
 * @param userEmail acting user email when available
 * @param analysisId related analysis identifier when applicable
 * @param binaryId related binary identifier when applicable
 * @param binaryOriginalName related binary original name when applicable
 * @param detailsJson JSON-encoded additional details
 */
public record AuditEventResponse(
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
