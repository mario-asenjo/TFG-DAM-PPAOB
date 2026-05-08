package com.ppaob.backend.application.port.out;

import com.ppaob.backend.domain.model.AnalysisRecord;
import com.ppaob.backend.domain.model.AnalysisResultRecord;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Outbound persistence port for analysis requests and analysis results.
 *
 * <p>This port belongs to the application layer and defines how use cases store and
 * retrieve analysis data while enforcing requester visibility constraints. Adapters
 * are responsible for applying ownership/admin access rules and returning empty
 * results when entities are not visible.</p>
 */
public interface AnalysisRepositoryPort {
    /**
     * Creates a new analysis request for a binary visible to the requester.
     *
     * <p>Implementations may reject creation by returning an empty value when
     * the requester is not allowed to request analysis for the target binary.</p>
     *
     * @param binaryId binary identifier to analyze
     * @param requestedBy requester user identifier
     * @param profile analysis profile name
     * @return created analysis record, or empty when creation is not allowed
     */
    Optional<AnalysisRecord> create(UUID binaryId, UUID requestedBy, String profile);

    /**
     * Finds one analysis if it is visible to the requester.
     *
     * @param analysisId analysis identifier
     * @param requesterId user performing the lookup
     * @param requesterIsAdmin whether requester has admin visibility
     * @return analysis record when found and visible, otherwise empty
     */
    Optional<AnalysisRecord> findById(UUID analysisId, UUID requesterId, boolean requesterIsAdmin);

    /**
     * Finds stored result data for one analysis if visible to the requester.
     *
     * @param analysisId analysis identifier
     * @param requesterId user performing the lookup
     * @param requesterIsAdmin whether requester has admin visibility
     * @return stored result record when present and visible, otherwise empty
     */
    Optional<AnalysisResultRecord> findResultByAnalysisId(UUID analysisId, UUID requesterId, boolean requesterIsAdmin);

    /**
     * Lists analyses visible to the requester.
     *
     * <p>When {@code binaryId} is {@code null}, the list is not constrained to a
     * specific binary.</p>
     *
     * @param requesterId user performing the listing
     * @param requesterIsAdmin whether requester has admin visibility
     * @param binaryId optional binary filter, or {@code null} for all visible binaries
     * @param limit maximum number of records to return
     * @return visible analyses ordered by the implementation-defined recency criteria
     */
    List<AnalysisRecord> list(UUID requesterId, boolean requesterIsAdmin, UUID binaryId, int limit);
}
