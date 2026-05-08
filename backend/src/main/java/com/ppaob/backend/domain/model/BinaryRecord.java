package com.ppaob.backend.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Metadata for a binary uploaded for pre-exploitation analysis.
 *
 * <p>This record keeps identity, integrity hash and storage location metadata.
 * The binary payload itself is externalized in object storage and referenced via
 * {@code objectId}/{@code bucket}/{@code objectKey}.
 *
 * @param binaryId unique binary identifier.
 * @param originalName original client-provided file name.
 * @param sha256 SHA-256 digest of the uploaded content.
 * @param format normalized file format label used by downstream analyzers.
 * @param sizeBytes uploaded object size in bytes.
 * @param uploadedAt upload timestamp.
 * @param uploadedBy user identifier that submitted the binary.
 * @param objectId storage object identifier in the persistence layer.
 * @param bucket storage bucket/container name.
 * @param objectKey object path/key inside the bucket.
 */
public record BinaryRecord(
        UUID binaryId,
        String originalName,
        String sha256,
        String format,
        long sizeBytes,
        Instant uploadedAt,
        UUID uploadedBy,
        UUID objectId,
        String bucket,
        String objectKey
) {
}
