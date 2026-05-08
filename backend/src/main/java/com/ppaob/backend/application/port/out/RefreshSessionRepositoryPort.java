package com.ppaob.backend.application.port.out;

import com.ppaob.backend.domain.model.RefreshSessionRecord;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Outbound persistence port for refresh-token sessions.
 *
 * <p>This port defines lifecycle operations for refresh sessions (issue, resolve,
 * revoke) used by authentication services. It stores hashed tokens and session
 * metadata, never raw token values.</p>
 */
public interface RefreshSessionRepositoryPort {
    /**
     * Creates a refresh session.
     *
     * @param userId owner user identifier
     * @param tokenHash refresh token hash
     * @param expiresAt absolute expiration instant
     * @param createdIp optional client IP captured at creation time
     * @param createdUserAgent optional client user-agent captured at creation time
     * @return created refresh session record
     */
    RefreshSessionRecord create(UUID userId, String tokenHash, Instant expiresAt, String createdIp, String createdUserAgent);

    /**
     * Finds an active session by token hash at a given reference instant.
     *
     * <p>Active means not revoked and not expired at {@code now}.</p>
     *
     * @param tokenHash refresh token hash
     * @param now reference instant for expiration checks
     * @return matching active session, or empty when absent, revoked, or expired
     */
    Optional<RefreshSessionRecord> findActiveByTokenHash(String tokenHash, Instant now);

    /**
     * Revokes a refresh session.
     *
     * <p>Implementations may treat repeated revocation of an already revoked
     * session as a no-op.</p>
     *
     * @param sessionId session identifier to revoke
     * @param revokedAt revocation timestamp
     * @param replacedBy optional replacement session identifier
     * @param reason revocation reason code
     */
    void revoke(UUID sessionId, Instant revokedAt, UUID replacedBy, String reason);
}
