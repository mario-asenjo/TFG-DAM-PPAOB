package com.ppaob.backend.infrastructure.security;

import java.util.Set;
import java.util.UUID;

/**
 * Immutable principal data propagated across the security and application layers.
 *
 * @param userId unique user identifier used as JWT subject
 * @param email user email associated with the authenticated identity
 * @param roles granted application roles without {@code ROLE_} prefix
 */
public record AuthenticatedUser(
        UUID userId,
        String email,
        Set<String> roles
) {
}
