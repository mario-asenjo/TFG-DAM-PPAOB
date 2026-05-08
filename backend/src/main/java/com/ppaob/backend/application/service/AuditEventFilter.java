package com.ppaob.backend.application.service;

import java.time.Instant;
import java.util.UUID;

/**
 * Filter criteria used to query audit events.
 *
 * @param action optional action name filter, typically normalized to uppercase
 * @param result optional result filter (for example SUCCESS/FAIL)
 * @param userId optional actor user filter
 * @param analysisId optional analysis scope filter
 * @param binaryId optional binary scope filter
 * @param from optional lower bound timestamp (inclusive)
 * @param to optional upper bound timestamp (inclusive)
 * @param limit maximum number of rows to return
 * @param offset row offset for pagination
 */
public record AuditEventFilter(
        String action,
        String result,
        UUID userId,
        UUID analysisId,
        UUID binaryId,
        Instant from,
        Instant to,
        int limit,
        int offset
) {
}
