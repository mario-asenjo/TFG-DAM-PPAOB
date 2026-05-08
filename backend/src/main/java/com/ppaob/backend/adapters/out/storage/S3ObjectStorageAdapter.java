package com.ppaob.backend.adapters.out.storage;

import com.ppaob.backend.application.port.out.ObjectStoragePort;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * S3-backed implementation of {@link ObjectStoragePort}.
 *
 * <p>This adapter ensures the target bucket exists before uploading and delegates
 * object operations to the configured AWS SDK {@link S3Client}.</p>
 */
@Component
public class S3ObjectStorageAdapter implements ObjectStoragePort {

    private final S3Client s3Client;

    /**
     * Creates the adapter with an SDK client.
     *
     * @param s3Client synchronous S3 client used for bucket/object operations
     */
    public S3ObjectStorageAdapter(S3Client s3Client) {
        this.s3Client = s3Client;
    }

    @Override
    /**
     * Uploads binary content to an S3 object.
     *
     * <p>Side effects: may create the bucket when it does not exist (or when S3
     * returns status {@code 404}), then writes object bytes to remote storage.</p>
     *
     * @param bucket target bucket name
     * @param objectKey key within the bucket
     * @param content full object payload
     * @param contentType MIME type stored as object metadata
     * @throws S3Exception when S3 rejects the operation for errors other than
     *         already-existing bucket conflict ({@code 409}) during bucket check
     */
    public void upload(String bucket, String objectKey, byte[] content, String contentType) {
        ensureBucket(bucket);

        var request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey)
                .contentType(contentType)
                .contentLength((long) content.length)
                .build();

        s3Client.putObject(request, RequestBody.fromBytes(content));
    }

    @Override
    /**
     * Checks whether an object key exists in a bucket.
     *
     * @param bucket bucket name to inspect
     * @param objectKey key to verify
     * @return {@code true} when the object metadata can be resolved; {@code false}
     *         only for not-found responses ({@code 404})
     */
    public boolean exists(String bucket, String objectKey) {
        try {
            s3Client.headObject(HeadObjectRequest.builder().bucket(bucket).key(objectKey).build());
            return true;
        } catch (NoSuchKeyException ex) {
            return false;
        } catch (S3Exception ex) {
            return ex.statusCode() != 404;
        }
    }

    @Override
    /**
     * Downloads and returns the full content of an S3 object.
     *
     * @param bucket source bucket
     * @param objectKey source object key
     * @return object payload bytes
     * @throws S3Exception when the object cannot be fetched (for example missing
     *         key, missing bucket, or permission errors)
     */
    public byte[] download(String bucket, String objectKey) {
        return s3Client.getObjectAsBytes(
                GetObjectRequest.builder().bucket(bucket).key(objectKey).build()
        ).asByteArray();
    }

    private void ensureBucket(String bucket) {
        try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
        } catch (NoSuchBucketException e) {
            s3Client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                s3Client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
                return;
            }
            if (e.statusCode() == 409) {
                return;
            }
            throw e;
        }
    }
}
