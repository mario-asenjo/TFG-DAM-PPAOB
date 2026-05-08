package com.ppaob.backend.adapters.in.rest.dto;

import java.util.Set;
import java.util.UUID;

/**
 * Response payload representing a user entry in admin listings.
 *
 * @param userId unique user identifier
 * @param email account email address
 * @param enabled whether the account is enabled
 * @param roles assigned role set
 */
public record AdminUserResponse(
        UUID userId,
        String email,
        boolean enabled,
        Set<String> roles
) {
}
