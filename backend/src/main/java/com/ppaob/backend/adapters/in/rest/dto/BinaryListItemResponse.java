package com.ppaob.backend.adapters.in.rest.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Response payload for a binary row returned by listing endpoints.
 *
 * @param binaryId binary identifier
 * @param originalName original filename
 * @param sha256 binary SHA-256 hash
 * @param format detected format label
 * @param sizeBytes binary size in bytes
 * @param uploadedAt upload timestamp
 * @param objectAvailable whether object storage currently contains the payload
 */
public record BinaryListItemResponse(
        UUID binaryId,
        String originalName,
        String sha256,
        String format,
        long sizeBytes,
        Instant uploadedAt,
        boolean objectAvailable
) {
}
