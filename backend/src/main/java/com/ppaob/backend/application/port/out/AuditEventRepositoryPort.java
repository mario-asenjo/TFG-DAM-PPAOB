package com.ppaob.backend.application.port.out;

import com.ppaob.backend.domain.model.AuditEventRecord;
import com.ppaob.backend.application.service.AuditEventFilter;

import java.util.List;
import java.util.UUID;

/**
 * Outbound persistence port for audit trail events.
 *
 * <p>This port defines append-only audit writes and filtered reads used by the
 * application layer. Implementations persist observability metadata for user and
 * system actions in the pre-exploitation workflow.</p>
 */
public interface AuditEventRepositoryPort {
    /**
     * Appends one audit event entry.
     *
     * <p>Implementations persist the event as an immutable log record.</p>
     *
     * @param action normalized action identifier
     * @param result normalized outcome identifier
     * @param userId optional actor user identifier
     * @param analysisId optional related analysis identifier
     * @param binaryId optional related binary identifier
     * @param detailsJson optional JSON object encoded as text
     */
    void append(String action, String result, UUID userId, UUID analysisId, UUID binaryId, String detailsJson);

    /**
     * Lists most recent audit events.
     *
     * @param limit maximum number of records to return
     * @return recent audit events ordered by implementation-defined recency criteria
     */
    List<AuditEventRecord> listRecent(int limit);

    /**
     * Lists audit events matching a filter.
     *
     * @param filter filter values and pagination controls
     * @return audit events matching provided constraints
     */
    List<AuditEventRecord> listByFilter(AuditEventFilter filter);
}
