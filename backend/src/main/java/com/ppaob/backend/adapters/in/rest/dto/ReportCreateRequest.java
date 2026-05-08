package com.ppaob.backend.adapters.in.rest.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Request payload used to generate a report artifact.
 *
 * @param analysisId identifier of the analysis to report
 * @param type optional report type, currently restricted by controller contract
 */
public record ReportCreateRequest(
        @NotNull UUID analysisId,
        String type
) {
}
