package com.ppaob.backend.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Persisted serialized result payload for an analysis.
 *
 * <p>The result schema is versioned to keep backward-compatible readers possible.
 * {@code resultsJson} is an opaque JSON payload produced by analysis workers.
 *
 * @param analysisId identifier of the analysis these results belong to.
 * @param schemaVersion version of the JSON schema used by {@code resultsJson}.
 * @param resultsJson serialized analysis output payload.
 * @param storedAt persistence timestamp of this result snapshot.
 */
public record AnalysisResultRecord(
        UUID analysisId,
        int schemaVersion,
        String resultsJson,
        Instant storedAt
) {
}
