package com.ppaob.backend.application.port.out;

import com.ppaob.backend.domain.model.ArtifactRecord;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Outbound persistence port for analysis artifacts metadata.
 *
 * <p>This port defines application-layer access to artifact records generated from
 * analyses. Implementations are responsible for enforcing requester visibility.
 * Artifact byte transfer is handled by storage ports, not this repository port.</p>
 */
public interface ArtifactRepositoryPort {
    /**
     * Creates an artifact record linked to an analysis and a stored object.
     *
     * @param analysisId parent analysis identifier
     * @param type artifact type label
     * @param bucket storage bucket where artifact bytes are stored
     * @param objectKey storage key where artifact bytes are stored
     * @param checksumSha256 SHA-256 digest used as integrity metadata
     * @param sizeBytes artifact payload size in bytes
     * @return created artifact record
     */
    ArtifactRecord create(UUID analysisId, String type, String bucket, String objectKey, String checksumSha256, long sizeBytes);

    /**
     * Lists artifact records visible to a requester.
     *
     * @param requesterId user performing the listing
     * @param requesterIsAdmin whether requester has admin visibility
     * @param limit maximum number of records to return
     * @return visible artifacts ordered by implementation-defined recency criteria
     */
    List<ArtifactRecord> listForRequester(UUID requesterId, boolean requesterIsAdmin, int limit);

    /**
     * Finds one artifact if it is visible to the requester.
     *
     * @param artifactId artifact identifier
     * @param requesterId user performing the lookup
     * @param requesterIsAdmin whether requester has admin visibility
     * @return artifact record when found and visible, otherwise empty
     */
    Optional<ArtifactRecord> findByIdForRequester(UUID artifactId, UUID requesterId, boolean requesterIsAdmin);
}
