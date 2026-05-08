package com.ppaob.backend.application.service;

import com.ppaob.backend.application.port.out.BinaryRepositoryPort;
import com.ppaob.backend.application.port.out.ObjectStoragePort;
import com.ppaob.backend.domain.model.BinaryRecord;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
/**
 * Handles binary ingestion, deduplication, and visibility-aware lookup.
 *
 * <p>The service validates upload constraints, accepts only ELF binaries for this slice, stores
 * payloads in object storage, and persists/link records in the binary repository.
 */
public class BinaryService {

    private final BinaryRepositoryPort binaryRepository;
    private final ObjectStoragePort objectStorage;
    private final long maxUploadBytes;

/**
 * Creates the binary service.
 *
 * @param binaryRepository binary metadata repository
 * @param objectStorage object storage adapter for payload existence and upload
 * @param maxUploadBytes maximum accepted upload size in bytes
 */
    public BinaryService(
            BinaryRepositoryPort binaryRepository,
            ObjectStoragePort objectStorage,
            @Value("${app.upload.max-bytes:20971520}") long maxUploadBytes
    ) {
        this.binaryRepository = binaryRepository;
        this.objectStorage = objectStorage;
        this.maxUploadBytes = maxUploadBytes;
    }

    @Transactional
/**
 * Uploads a binary and creates or reuses a deduplicated binary record.
 *
 * <p>Business rules:
 * - file must be non-empty and within configured size limit,
 * - only ELF binaries are accepted (magic bytes or .elf extension fallback),
 * - SHA-256 is used as deduplication key.
 *
 * <p>Dedup behavior:
 * - existing hash links uploader to existing binary,
 * - if dedup object is missing, payload is re-uploaded and marked as restored,
 * - race-condition duplicate inserts are recovered by lookup on unique-key conflict.
 *
 * <p>Side effects: writes object to storage and creates/updates rows in binary/link persistence.
 *
 * @param file multipart upload payload
 * @param uploadedBy uploader user id
 * @param bucketName target storage bucket for new objects
 * @return upload result with binary metadata and dedup/restore flags
 * @throws IllegalArgumentException when validation fails, file cannot be read, or format is not ELF
 */
    public UploadBinaryResult uploadBinary(MultipartFile file, UUID uploadedBy, String bucketName) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }

        if (file.getSize() > maxUploadBytes) {
            throw new IllegalArgumentException("File exceeds maximum allowed size");
        }

        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException ex) {
            throw new IllegalArgumentException("Cannot read uploaded file");
        }

        String sha256 = sha256(content);
        var existing = binaryRepository.findBySha256(sha256);
        if (existing.isPresent()) {
            BinaryRecord deduplicated = existing.get();
            boolean objectExists = objectStorage.exists(deduplicated.bucket(), deduplicated.objectKey());
            if (!objectExists) {
                String contentType = file.getContentType() == null ? "application/octet-stream" : file.getContentType();
                objectStorage.upload(deduplicated.bucket(), deduplicated.objectKey(), content, contentType);
            }
            binaryRepository.linkToUploader(deduplicated.binaryId(), uploadedBy, objectExists ? "DEDUP_LINK" : "DEDUP_RESTORE");
            return new UploadBinaryResult(deduplicated, true, !objectExists);
        }

        String originalName = sanitizeName(file.getOriginalFilename());
        String format = detectFormat(originalName, content);
        if (!"ELF".equals(format)) {
            throw new IllegalArgumentException("Only ELF binaries are accepted in this MVP slice");
        }
        String objectKey = "binaries/" + sha256;
        String contentType = file.getContentType() == null ? "application/octet-stream" : file.getContentType();

        objectStorage.upload(bucketName, objectKey, content, contentType);

        BinaryRecord created;
        try {
            created = binaryRepository.create(
                    originalName,
                    sha256,
                    format,
                    file.getSize(),
                    uploadedBy,
                    bucketName,
                    objectKey
            );
        } catch (DataIntegrityViolationException ex) {
            var dedup = binaryRepository.findBySha256(sha256)
                    .orElseThrow(() -> ex);
            binaryRepository.linkToUploader(dedup.binaryId(), uploadedBy, "DEDUP_LINK");
            boolean restored = false;
            if (!objectStorage.exists(dedup.bucket(), dedup.objectKey())) {
                objectStorage.upload(dedup.bucket(), dedup.objectKey(), content, contentType);
                restored = true;
            }
            return new UploadBinaryResult(dedup, true, restored);
        }

        return new UploadBinaryResult(created, false, false);
    }

/**
 * Lists binaries associated with an uploader and enriches with object availability.
 *
 * @param uploadedBy uploader user id
 * @return visible binaries plus storage-availability indicator per item
 */
    public List<BinaryListItem> listByUploader(UUID uploadedBy) {
        return binaryRepository.listByUploader(uploadedBy).stream()
                .map(binary -> new BinaryListItem(
                        binary.binaryId(),
                        binary.originalName(),
                        binary.sha256(),
                        binary.format(),
                        binary.sizeBytes(),
                        binary.uploadedAt(),
                        objectStorage.exists(binary.bucket(), binary.objectKey())
                ))
                .toList();
    }

/**
 * Returns a binary only if visible to the requester.
 *
 * @param binaryId binary identifier
 * @param requesterId requester user id
 * @param requesterIsAdmin whether requester has admin visibility
 * @return binary metadata
 * @throws IllegalArgumentException when binary is not found or not visible
 */
    public BinaryRecord requireBinaryForRequester(UUID binaryId, UUID requesterId, boolean requesterIsAdmin) {
        return binaryRepository.findByIdForUploader(binaryId, requesterId, requesterIsAdmin)
                .orElseThrow(() -> new IllegalArgumentException("Binary not found for current user"));
    }

/**
 * Checks whether the binary payload currently exists in object storage.
 *
 * @param binary binary metadata containing bucket/object key
 * @return {@code true} when object exists in storage
 */
    public boolean isBinaryObjectAvailable(BinaryRecord binary) {
        return objectStorage.exists(binary.bucket(), binary.objectKey());
    }

    private static String sanitizeName(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return "binary.bin";
        }
        return originalFilename.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private static String detectFormat(String filename, byte[] content) {
        if (content.length >= 4 && content[0] == 0x7f && content[1] == 0x45 && content[2] == 0x4c && content[3] == 0x46) {
            return "ELF";
        }

        String lower = filename.toLowerCase();
        if (lower.endsWith(".elf")) {
            return "ELF";
        }

        return "UNKNOWN";
    }

    private static String sha256(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content);
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available");
        }
    }

/**
 * Result DTO for binary uploads.
 *
 * @param binary resolved binary metadata (created or deduplicated)
 * @param deduplicated whether an existing SHA-256 record was reused
 * @param restoredObject whether object payload had to be re-uploaded for a dedup record
 */
    public record UploadBinaryResult(BinaryRecord binary, boolean deduplicated, boolean restoredObject) {
        /**
         * Returns binary metadata associated with the upload outcome.
         *
         * @return binary metadata
         */
        @Override
        public BinaryRecord binary() {
            return binary;
        }
    }

/**
 * Projection used when listing binaries for a user.
 *
 * @param binaryId binary identifier
 * @param originalName sanitized original filename
 * @param sha256 SHA-256 checksum used as dedup key
 * @param format detected binary format
 * @param sizeBytes file size in bytes
 * @param uploadedAt upload timestamp
 * @param objectAvailable whether payload exists in object storage
 */
    public record BinaryListItem(
            UUID binaryId,
            String originalName,
            String sha256,
            String format,
            long sizeBytes,
            java.time.Instant uploadedAt,
            boolean objectAvailable
    ) {
        /**
         * Returns the binary identifier.
         *
         * @return binary id
         */
        @Override
        public UUID binaryId() {
            return binaryId;
        }
    }
}
