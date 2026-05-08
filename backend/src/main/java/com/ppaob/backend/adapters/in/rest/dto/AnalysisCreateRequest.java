package com.ppaob.backend.adapters.in.rest.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Request payload used to create an analysis for a stored binary.
 *
 * @param binaryId identifier of the binary to analyze
 * @param profile optional analysis profile name
 */
public record AnalysisCreateRequest(
        @NotNull UUID binaryId,
        String profile
) {
}
