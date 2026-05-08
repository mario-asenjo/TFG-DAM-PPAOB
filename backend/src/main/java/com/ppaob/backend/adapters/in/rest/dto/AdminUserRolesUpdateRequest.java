package com.ppaob.backend.adapters.in.rest.dto;

import jakarta.validation.constraints.NotNull;

import java.util.Set;

/**
 * Request payload used to replace roles assigned to a user.
 *
 * @param roles complete role set that should remain assigned
 */
public record AdminUserRolesUpdateRequest(
        @NotNull Set<String> roles
) {
}
