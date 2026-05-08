package com.ppaob.backend.application.port.out;

/**
 * Outbound object storage port for binary and artifact payloads.
 *
 * <p>This port abstracts byte-level storage operations used by application
 * services. Metadata persistence and access control remain in repository ports.</p>
 */
public interface ObjectStoragePort {
    /**
     * Uploads a full object payload.
     *
     * <p>Side effects: writes bytes to the backing storage provider.</p>
     *
     * @param bucket target bucket or container name
     * @param objectKey target object key/path
     * @param content full payload bytes
     * @param contentType MIME type associated with the object
     */
    void upload(String bucket, String objectKey, byte[] content, String contentType);

    /**
     * Checks whether an object exists in storage.
     *
     * @param bucket bucket or container name
     * @param objectKey object key/path
     * @return {@code true} when the object exists, otherwise {@code false}
     */
    boolean exists(String bucket, String objectKey);

    /**
     * Downloads the full payload of one object.
     *
     * @param bucket bucket or container name
     * @param objectKey object key/path
     * @return payload bytes for the requested object
     */
    byte[] download(String bucket, String objectKey);
}
