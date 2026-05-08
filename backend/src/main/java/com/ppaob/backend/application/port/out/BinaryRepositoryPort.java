package com.ppaob.backend.application.port.out;

import com.ppaob.backend.domain.model.BinaryRecord;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Outbound persistence port for binary metadata and uploader associations.
 *
 * <p>This port is used by application services to deduplicate binaries, create
 * metadata records, and enforce requester visibility via ownership/admin rules.
 * Raw object bytes are handled by the storage port.</p>
 */
public interface BinaryRepositoryPort {
    /**
     * Finds a binary by its SHA-256 digest.
     *
     * @param sha256 binary content digest in hexadecimal form
     * @return matching binary record, or empty when no binary has that digest
     */
    Optional<BinaryRecord> findBySha256(String sha256);

    /**
     * Finds one binary when visible to the requester.
     *
     * @param binaryId binary identifier
     * @param uploadedBy requester user identifier
     * @param uploaderIsAdmin whether requester has admin visibility
     * @return binary record when found and visible, otherwise empty
     */
    Optional<BinaryRecord> findByIdForUploader(UUID binaryId, UUID uploadedBy, boolean uploaderIsAdmin);

    /**
     * Creates a binary metadata record and links it to an uploader.
     *
     * <p>Side effects include persistence writes for object metadata, binary
     * metadata, and uploader association records.</p>
     *
     * @param originalName original filename
     * @param sha256 binary content digest in hexadecimal form
     * @param format detected or declared binary format label
     * @param sizeBytes binary size in bytes
     * @param uploadedBy uploader user identifier
     * @param bucket storage bucket containing the binary bytes
     * @param objectKey storage key containing the binary bytes
     * @return created binary record
     */
    BinaryRecord create(String originalName, String sha256, String format, long sizeBytes, UUID uploadedBy, String bucket, String objectKey);

    /**
     * Links an existing binary to a user uploader relationship.
     *
     * <p>Implementations should treat this operation as idempotent for duplicate
     * binary/user relationships when supported by the persistence backend.</p>
     *
     * @param binaryId binary identifier
     * @param uploadedBy uploader user identifier
     * @param source source label describing how the link was created
     */
    void linkToUploader(UUID binaryId, UUID uploadedBy, String source);

    /**
     * Lists binaries linked to one uploader.
     *
     * @param uploadedBy uploader user identifier
     * @return binaries linked to the user, ordered by implementation-defined recency
     */
    List<BinaryRecord> listByUploader(UUID uploadedBy);
}
