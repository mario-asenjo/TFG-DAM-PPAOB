package com.ppaob.backend.adapters.in.rest.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Request payload used to enable or disable a user account.
 *
 * @param enabled target enabled state to persist
 */
public record AdminUserEnabledUpdateRequest(
        @NotNull Boolean enabled
) {
}
