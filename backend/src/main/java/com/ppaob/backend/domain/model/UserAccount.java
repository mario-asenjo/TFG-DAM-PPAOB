package com.ppaob.backend.domain.model;

import java.util.Set;
import java.util.UUID;

/**
 * Domain view of an authenticated user account.
 *
 * <p>Instances are immutable snapshots returned by domain repositories and services.
 * The {@code roles} set represents granted authorities at read time and is owned by
 * the record instance (callers should treat it as read-only).
 *
 * @param userId stable user identifier.
 * @param email login and contact email associated with the account.
 * @param passwordHash one-way password hash, never plain text credentials.
 * @param enabled whether the account can authenticate and request analyses.
 * @param roles granted authorization roles for access control checks.
 */
public record UserAccount(
        UUID userId,
        String email,
        String passwordHash,
        boolean enabled,
        Set<String> roles
) {
}
