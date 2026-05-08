package com.ppaob.backend.adapters.out.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Externalized settings used to configure S3-compatible object storage.
 *
 * @param endpoint optional endpoint override (for example local/object-store URL)
 * @param region AWS region identifier used by the SDK client
 * @param accessKey access key for static credentials
 * @param secretKey secret key for static credentials
 * @param bucket default bucket name used by application services
 * @param pathStyleAccess whether path-style addressing is forced
 */
@ConfigurationProperties(prefix = "storage.s3")
public record S3StorageProperties(
        String endpoint,
        String region,
        String accessKey,
        String secretKey,
        String bucket,
        boolean pathStyleAccess
) {
}
