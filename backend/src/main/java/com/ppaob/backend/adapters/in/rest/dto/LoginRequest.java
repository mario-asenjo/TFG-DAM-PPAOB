package com.ppaob.backend.adapters.in.rest.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request payload used to authenticate an existing account.
 *
 * @param email account email address
 * @param password raw password provided by the user
 */
public record LoginRequest(
        @Email @NotBlank String email,
        @NotBlank @Size(min = 8, max = 128) String password
) {
}
