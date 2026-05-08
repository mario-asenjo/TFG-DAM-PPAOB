package com.ppaob.backend.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Refresh token session state used to rotate and revoke long-lived authentication sessions.
 *
 * <p>The lifecycle is modeled through timestamps and replacement linkage:
 * active sessions have {@code revokedAt == null}; rotated sessions can point to the successor
 * through {@code replacedBy}; revoked sessions should include a human-readable reason.
 *
 * @param sessionId unique identifier of this refresh session.
 * @param userId owner account identifier.
 * @param tokenHash one-way hash of the refresh token value.
 * @param expiresAt instant after which the session is no longer valid.
 * @param createdAt session creation instant.
 * @param revokedAt revocation instant, or {@code null} when the session is still active.
 * @param replacedBy identifier of the replacement session after token rotation, or {@code null}.
 * @param revokedReason operator-facing reason for revocation, or {@code null} when not revoked.
 */
public record RefreshSessionRecord(
        UUID sessionId,
        UUID userId,
        String tokenHash,
        Instant expiresAt,
        Instant createdAt,
        Instant revokedAt,
        UUID replacedBy,
        String revokedReason
) {
}
