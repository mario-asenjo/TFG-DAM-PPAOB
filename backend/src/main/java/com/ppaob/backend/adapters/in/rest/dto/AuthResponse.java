package com.ppaob.backend.adapters.in.rest.dto;

import java.time.Instant;
import java.util.Set;

/**
 * Response payload returned after successful authentication operations.
 *
 * @param token access token value
 * @param expiresAt access token expiration timestamp
 * @param email authenticated account email
 * @param roles roles granted to the account
 */
public record AuthResponse(
        String token,
        Instant expiresAt,
        String email,
        Set<String> roles
) {
}
