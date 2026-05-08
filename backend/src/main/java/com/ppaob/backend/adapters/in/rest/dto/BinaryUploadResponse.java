package com.ppaob.backend.adapters.in.rest.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Response payload returned after binary upload processing.
 *
 * @param binaryId binary identifier
 * @param originalName original filename
 * @param sha256 binary SHA-256 hash
 * @param format detected format label
 * @param sizeBytes binary size in bytes
 * @param uploadedAt upload timestamp
 * @param deduplicated whether an existing binary entry was reused
 * @param restoredObject whether object storage content had to be restored
 */
public record BinaryUploadResponse(
        UUID binaryId,
        String originalName,
        String sha256,
        String format,
        long sizeBytes,
        Instant uploadedAt,
        boolean deduplicated,
        boolean restoredObject
) {
}
