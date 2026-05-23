package com.ecommerce.app.module.product.image;

import java.io.InputStream;
import java.net.URI;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * Cloudflare R2 storage backend.
 *
 * R2 is S3-compatible, so we use the AWS SDK v2 S3 client. The only special
 * configuration is:
 *   - endpoint:        https://<accountId>.r2.cloudflarestorage.com
 *   - region:          "auto"  (R2 has no real region)
 *   - path-style URLs: true    (R2 does not support virtual-hosted-style)
 *
 * Required env vars (read by the factory in {@link StorageFactory}):
 *   R2_ACCOUNT_ID            - Cloudflare account ID (from R2 dashboard)
 *   R2_ACCESS_KEY_ID         - API token access key
 *   R2_SECRET_ACCESS_KEY     - API token secret
 *   R2_BUCKET                - bucket name
 *   R2_PUBLIC_BASE_URL       - optional public URL prefix (r2.dev subdomain or
 *                              custom domain). When set, getFileUrl() returns
 *                              "<R2_PUBLIC_BASE_URL>/<objectKey>" so the
 *                              browser can fetch the image directly.
 *                              When unset, returns the private S3-style URL
 *                              (not browser-fetchable without signed URLs).
 */
public class R2StorageService implements StorageService {
    private static final Logger LOG = LoggerFactory.getLogger(R2StorageService.class);

    private final S3Client s3;
    private final String bucket;
    private final String publicBaseUrl; // may be null
    private final String endpoint;

    public R2StorageService(String accountId, String accessKey, String secretKey,
                            String bucket, String publicBaseUrl) {
        this.bucket = bucket;
        this.publicBaseUrl = (publicBaseUrl == null || publicBaseUrl.isBlank())
                ? null : stripTrailingSlash(publicBaseUrl);
        this.endpoint = "https://" + accountId + ".r2.cloudflarestorage.com";

        this.s3 = S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.of("auto"))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .build())
                .build();
    }

    @Override
    public String upload(String fileName, InputStream inputStream, long contentLength, String contentType) {
        String objectKey = UUID.randomUUID() + "-" + fileName;
        try {
            PutObjectRequest req = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(objectKey)
                    .contentType(contentType)
                    .contentLength(contentLength)
                    .build();
            s3.putObject(req, RequestBody.fromInputStream(inputStream, contentLength));
            LOG.info("r2 upload ok bucket={} key={}", bucket, objectKey);
            return objectKey;
        } catch (Exception e) {
            LOG.error("r2 upload failed bucket={} key={}", bucket, objectKey, e);
            throw new RuntimeException("failed to upload file to R2");
        }
    }

    @Override
    public boolean delete(String objectKey) {
        try {
            s3.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucket).key(objectKey).build());
            return true;
        } catch (Exception e) {
            LOG.error("r2 delete failed bucket={} key={}", bucket, objectKey, e);
            return false;
        }
    }

    @Override
    public String getFileUrl(String objectKey) {
        if (publicBaseUrl != null) {
            return publicBaseUrl + "/" + objectKey;
        }
        // Fallback: private API URL. Not directly browser-accessible without
        // a signed URL, but unique and stable.
        return endpoint + "/" + bucket + "/" + objectKey;
    }

    private static String stripTrailingSlash(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }
}
