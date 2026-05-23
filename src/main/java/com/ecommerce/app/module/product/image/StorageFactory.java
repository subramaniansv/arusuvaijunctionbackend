package com.ecommerce.app.module.product.image;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Picks a {@link StorageService} backend based on the STORAGE_PROVIDER env var.
 *
 *   STORAGE_PROVIDER=r2    -> Cloudflare R2 (requires R2_* env vars)
 *   STORAGE_PROVIDER=minio (default) -> local MinIO via env vars or hardcoded
 *                                       localhost:9000 fallback
 *
 * Reading config from env (and never System.exit-ing) lets a dev run the app
 * without any storage env vars set - it still boots, just won't be able to
 * upload until the bucket is reachable.
 */
public final class StorageFactory {
    private static final Logger LOG = LoggerFactory.getLogger(StorageFactory.class);

    private StorageFactory() {}

    private static volatile StorageService instance;

    public static StorageService get() {
        StorageService local = instance;
        if (local != null) {
            return local;
        }
        synchronized (StorageFactory.class) {
            if (instance == null) {
                instance = build();
            }
            return instance;
        }
    }

    private static StorageService build() {
        String provider = env("STORAGE_PROVIDER", "minio").toLowerCase();
        switch (provider) {
            case "r2":
            case "cloudflare":
                return buildR2();
            case "minio":
            default:
                return buildMinio();
        }
    }

    private static StorageService buildR2() {
        String accountId = env("R2_ACCOUNT_ID", null);
        String accessKey = env("R2_ACCESS_KEY_ID", null);
        String secretKey = env("R2_SECRET_ACCESS_KEY", null);
        String bucket    = env("R2_BUCKET", null);
        String publicUrl = env("R2_PUBLIC_BASE_URL", null);
        if (accountId == null || accessKey == null || secretKey == null || bucket == null) {
            throw new IllegalStateException(
                    "STORAGE_PROVIDER=r2 but one of R2_ACCOUNT_ID/R2_ACCESS_KEY_ID/"
                  + "R2_SECRET_ACCESS_KEY/R2_BUCKET is missing");
        }
        LOG.info("storage backend: Cloudflare R2 (bucket={}, publicBaseUrl={})",
                bucket, publicUrl == null ? "<none>" : publicUrl);
        return new R2StorageService(accountId, accessKey, secretKey, bucket, publicUrl);
    }

    private static StorageService buildMinio() {
        String endpoint  = env("MINIO_ENDPOINT", "http://localhost:9000");
        String accessKey = env("MINIO_ACCESS_KEY", "minioadmin");
        String secretKey = env("MINIO_SECRET_KEY", "minioadmin");
        String bucket    = env("MINIO_BUCKET", "products");
        LOG.info("storage backend: MinIO (endpoint={}, bucket={})", endpoint, bucket);
        return new MinIOStorageService(endpoint, accessKey, secretKey, bucket);
    }

    private static String env(String key, String fallback) {
        // Prefer the dotenv-aware lookup (checks .env first, then the OS
        // environment) so secrets dropped into the project's .env file are
        // honoured without having to also export them via setenv.sh.
        String value = com.ecommerce.app.module.iam.config.ENVConfig.get(key);
        if (value == null) {
            return fallback;
        }
        value = value.trim();
        // Defensive: strip a single pair of surrounding quotes that a user may
        // have wrapped around the value in their .env (KEY="value" / KEY='value').
        // dotenv-java *usually* strips these, but only when they perfectly
        // enclose the whole line; a stray quote inside an otherwise plain value
        // would otherwise bleed into URI parsing and crash startup.
        if (value.length() >= 2) {
            char first = value.charAt(0);
            char last  = value.charAt(value.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                value = value.substring(1, value.length() - 1).trim();
            }
        }
        if (value.isBlank()) {
            return fallback;
        }
        return value;
    }
}
