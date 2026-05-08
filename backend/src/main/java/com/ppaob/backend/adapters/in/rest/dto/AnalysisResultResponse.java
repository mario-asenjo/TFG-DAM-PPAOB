package com.ppaob.backend.adapters.in.rest.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

/**
 * Response payload containing parsed analysis results.
 *
 * @param analysisId analysis identifier
 * @param schemaVersion result schema version
 * @param results parsed result document
 * @param storedAt timestamp when results were stored
 */
public record AnalysisResultResponse(
        UUID analysisId,
        int schemaVersion,
        JsonNode results,
        Instant storedAt
) {
}
