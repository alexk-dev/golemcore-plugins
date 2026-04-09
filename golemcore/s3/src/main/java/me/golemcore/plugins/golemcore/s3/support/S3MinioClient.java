package me.golemcore.plugins.golemcore.s3.support;

import io.minio.BucketExistsArgs;
import io.minio.CopyObjectArgs;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.ListObjectsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.Result;
import io.minio.SourceObject;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.errors.ErrorResponseException;
import io.minio.errors.MinioException;
import io.minio.messages.Item;
import me.golemcore.plugins.golemcore.s3.S3PluginConfig;
import me.golemcore.plugins.golemcore.s3.S3PluginConfigService;
import okhttp3.OkHttpClient;
import org.springframework.stereotype.Component;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Component
public class S3MinioClient {

    private static final String DIRECTORY_CONTENT_TYPE = "application/x-directory";

    private final S3PluginConfigService configService;

    public S3MinioClient(S3PluginConfigService configService) {
        this.configService = configService;
    }

    public void ensureBucketReady() {
        S3PluginConfig config = getConfig();
        requireText(config.getEndpoint(), "S3 endpoint is not configured.");
        requireText(config.getAccessKey(), "S3 access key is not configured.");
        requireText(config.getSecretKey(), "S3 secret key is not configured.");
        requireText(config.getBucket(), "S3 bucket is not configured.");

        if (bucketExistsInternal(config.getBucket())) {
            return;
        }
        if (!Boolean.TRUE.equals(config.getAutoCreateBucket())) {
            throw new IllegalArgumentException("S3 bucket does not exist: " + config.getBucket());
        }
        makeBucketInternal(config.getBucket(), config.getRegion());
    }

    public S3ObjectInfo findObject(String relativePath) {
        ensureBucketReady();
        String resolvedKey = resolveKey(relativePath);
        if (resolvedKey.isBlank()) {
            return null;
        }
        return findObjectByResolvedKey(resolvedKey, false);
    }

    public S3ObjectInfo findDirectoryMarker(String relativePath) {
        ensureBucketReady();
        String normalizedPath = normalizeRelativePath(relativePath);
        if (normalizedPath.isBlank()) {
            return null;
        }
        String markerKey = resolveDirectoryMarkerKey(normalizedPath);
        return findObjectByResolvedKey(markerKey, true);
    }

    public boolean directoryExists(String relativePath) {
        ensureBucketReady();
        String normalizedPath = normalizeRelativePath(relativePath);
        if (normalizedPath.isBlank()) {
            return true;
        }
        if (findDirectoryMarker(normalizedPath) != null) {
            return true;
        }
        return !listDirectory(normalizedPath).isEmpty();
    }

    public List<S3ObjectInfo> listDirectory(String relativePath) {
        ensureBucketReady();
        String normalizedPath = normalizeRelativePath(relativePath);
        String prefix = resolveListPrefix(normalizedPath);
        List<S3ObjectInfo> rawEntries = listObjectsInternal(getBucket(), prefix, false);
        List<S3ObjectInfo> entries = new ArrayList<>();
        for (S3ObjectInfo rawEntry : rawEntries) {
            S3ObjectInfo mapped = toRelativeInfo(rawEntry, rawEntry.directory());
            if (mapped == null) {
                continue;
            }
            if (!normalizedPath.isBlank() && normalizedPath.equals(mapped.key())) {
                continue;
            }
            if (entries.stream().noneMatch(existing -> existing.key().equals(mapped.key()))) {
                entries.add(mapped);
            }
        }
        return List.copyOf(entries);
    }

    public List<S3ObjectInfo> listTree(String relativePath) {
        ensureBucketReady();
        String normalizedPath = normalizeRelativePath(relativePath);
        String prefix = resolveListPrefix(normalizedPath);
        List<S3ObjectInfo> rawEntries = listObjectsInternal(getBucket(), prefix, true);
        List<S3ObjectInfo> entries = new ArrayList<>();
        for (S3ObjectInfo rawEntry : rawEntries) {
            S3ObjectInfo mapped = toRelativeInfo(rawEntry, rawEntry.directory());
            if (mapped != null) {
                entries.add(mapped);
            }
        }
        return List.copyOf(entries);
    }

    public S3ObjectContent readObject(String relativePath) {
        ensureBucketReady();
        String resolvedKey = resolveKey(relativePath);
        S3ObjectInfo metadata = requireObjectByResolvedKey(resolvedKey, false);
        return getObjectInternal(getBucket(), resolvedKey, metadata);
    }

    public void writeObject(String relativePath, byte[] bytes, String contentType) {
        ensureBucketReady();
        String resolvedKey = resolveKey(relativePath);
        putObjectInternal(getBucket(), resolvedKey, bytes, contentType);
    }

    public void createDirectoryMarker(String relativePath) {
        ensureBucketReady();
        String resolvedKey = resolveDirectoryMarkerKey(relativePath);
        putObjectInternal(getBucket(), resolvedKey, new byte[0], DIRECTORY_CONTENT_TYPE);
    }

    public void copyObject(String sourceRelativePath, String targetRelativePath) {
        ensureBucketReady();
        String sourceKey = resolveKey(sourceRelativePath);
        String targetKey = resolveKey(targetRelativePath);
        S3ObjectInfo sourceInfo = requireObjectByResolvedKey(sourceKey, false);
        copyObjectInternal(getBucket(), sourceKey, targetKey, sourceInfo);
    }

    public void deleteObject(String relativePath) {
        ensureBucketReady();
        deleteObjectByResolvedKey(resolveKey(relativePath));
    }

    public void deleteDirectoryMarker(String relativePath) {
        ensureBucketReady();
        deleteObjectByResolvedKey(resolveDirectoryMarkerKey(relativePath));
    }

    protected boolean bucketExistsInternal(String bucket) {
        try (MinioClient client = createClient()) {
            return client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
        } catch (Exception ex) {
            throw toStorageException("bucket existence check failed", ex);
        }
    }

    protected void makeBucketInternal(String bucket, String region) {
        try (MinioClient client = createClient()) {
            client.makeBucket(MakeBucketArgs.builder().bucket(bucket).region(region).build());
        } catch (Exception ex) {
            throw toStorageException("bucket creation failed", ex);
        }
    }

    protected List<S3ObjectInfo> listObjectsInternal(String bucket, String prefix, boolean recursive) {
        try (MinioClient client = createClient()) {
            Iterable<Result<Item>> results = client.listObjects(ListObjectsArgs.builder()
                    .bucket(bucket)
                    .prefix(prefix)
                    .recursive(recursive)
                    .build());
            List<S3ObjectInfo> objects = new ArrayList<>();
            for (Result<Item> result : results) {
                Item item = result.get();
                objects.add(new S3ObjectInfo(
                        bucket,
                        item.objectName(),
                        rawName(item.objectName()),
                        item.isDir(),
                        item.isDir() ? null : item.size(),
                        item.isDir() ? null : item.etag(),
                        null,
                        item.lastModified()));
            }
            return List.copyOf(objects);
        } catch (Exception ex) {
            throw toStorageException("object listing failed", ex);
        }
    }

    protected S3ObjectInfo statObjectInternal(String bucket, String key) {
        try (MinioClient client = createClient()) {
            StatObjectResponse response = client.statObject(StatObjectArgs.builder()
                    .bucket(bucket)
                    .object(key)
                    .build());
            return new S3ObjectInfo(
                    bucket,
                    key,
                    rawName(key),
                    false,
                    response.size(),
                    response.etag(),
                    response.contentType(),
                    response.lastModified());
        } catch (Exception ex) {
            throw toStorageException("object stat failed", ex);
        }
    }

    protected S3ObjectContent getObjectInternal(String bucket, String key, S3ObjectInfo metadata) {
        try (MinioClient client = createClient();
                GetObjectResponse response = client.getObject(GetObjectArgs.builder()
                        .bucket(bucket)
                        .object(key)
                        .build())) {
            return new S3ObjectContent(
                    bucket,
                    key,
                    response.readAllBytes(),
                    metadata.eTag(),
                    metadata.contentType(),
                    metadata.lastModified());
        } catch (Exception ex) {
            throw toStorageException("object read failed", ex);
        }
    }

    protected void putObjectInternal(String bucket, String key, byte[] bytes, String contentType) {
        try (MinioClient client = createClient()) {
            PutObjectArgs.Builder builder = PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(key)
                    .data(bytes, bytes.length);
            if (contentType != null && !contentType.isBlank()) {
                builder.contentType(contentType);
            }
            client.putObject(builder.build());
        } catch (Exception ex) {
            throw toStorageException("object write failed", ex);
        }
    }

    protected void copyObjectInternal(String bucket, String sourceKey, String targetKey, S3ObjectInfo sourceInfo) {
        try (MinioClient client = createClient()) {
            SourceObject source = new SourceObject(
                    SourceObject.builder().bucket(bucket).object(sourceKey).build(),
                    sourceInfo.size() != null ? sourceInfo.size() : 0L,
                    sourceInfo.eTag());
            client.copyObject(CopyObjectArgs.builder()
                    .bucket(bucket)
                    .object(targetKey)
                    .source(source)
                    .build());
        } catch (Exception ex) {
            throw toStorageException("object copy failed", ex);
        }
    }

    protected void removeObjectInternal(String bucket, String key) {
        try (MinioClient client = createClient()) {
            client.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(key).build());
        } catch (Exception ex) {
            throw toStorageException("object delete failed", ex);
        }
    }

    private MinioClient createClient() {
        S3PluginConfig config = getConfig();
        OkHttpClient httpClient = buildHttpClient(config);
        return MinioClient.builder()
                .endpoint(config.getEndpoint())
                .region(config.getRegion())
                .credentials(config.getAccessKey(), config.getSecretKey())
                .httpClient(httpClient, true)
                .build();
    }

    private OkHttpClient buildHttpClient(S3PluginConfig config) {
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .callTimeout(Duration.ofMillis(config.getTimeoutMs()))
                .connectTimeout(Duration.ofMillis(config.getTimeoutMs()))
                .readTimeout(Duration.ofMillis(config.getTimeoutMs()))
                .writeTimeout(Duration.ofMillis(config.getTimeoutMs()));
        if (Boolean.TRUE.equals(config.getAllowInsecureTls()) && config.getEndpoint().startsWith("https://")) {
            try {
                TrustManager[] trustManagers = new TrustManager[] { new X509TrustManager() {
                    @Override
                    public void checkClientTrusted(X509Certificate[] chain, String authType) {
                    }

                    @Override
                    public void checkServerTrusted(X509Certificate[] chain, String authType) {
                    }

                    @Override
                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[0];
                    }
                } };
                SSLContext sslContext = SSLContext.getInstance("TLS");
                sslContext.init(null, trustManagers, new SecureRandom());
                SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();
                X509TrustManager trustManager = (X509TrustManager) trustManagers[0];
                HostnameVerifier hostnameVerifier = (hostname, session) -> true;
                builder.sslSocketFactory(sslSocketFactory, trustManager);
                builder.hostnameVerifier(hostnameVerifier);
            } catch (GeneralSecurityException ex) {
                throw new IllegalStateException("Unable to configure insecure TLS for S3", ex);
            }
        }
        return builder.build();
    }

    private S3ObjectInfo findObjectByResolvedKey(String resolvedKey, boolean directory) {
        try {
            S3ObjectInfo rawInfo = statObjectInternal(getBucket(), resolvedKey);
            return toRelativeInfo(rawInfo, directory);
        } catch (S3StorageException ex) {
            if (isNotFound(ex)) {
                return null;
            }
            throw ex;
        }
    }

    private S3ObjectInfo requireObjectByResolvedKey(String resolvedKey, boolean directory) {
        S3ObjectInfo info = findObjectByResolvedKey(resolvedKey, directory);
        if (info == null) {
            throw new IllegalArgumentException("Object does not exist: " + stripRootPrefix(resolvedKey, directory));
        }
        return info;
    }

    private void deleteObjectByResolvedKey(String resolvedKey) {
        try {
            removeObjectInternal(getBucket(), resolvedKey);
        } catch (S3StorageException ex) {
            if (isNotFound(ex)) {
                throw new IllegalArgumentException(
                        "Object does not exist: " + stripRootPrefix(resolvedKey, resolvedKey.endsWith("/")));
            }
            throw ex;
        }
    }

    private S3ObjectInfo toRelativeInfo(S3ObjectInfo rawInfo, boolean forceDirectory) {
        if (rawInfo == null) {
            return null;
        }
        boolean directory = forceDirectory || rawInfo.directory();
        String relativeKey = stripRootPrefix(rawInfo.key(), directory);
        if (relativeKey == null) {
            return null;
        }
        if (relativeKey.isBlank() && !directory) {
            return null;
        }
        String name = relativeKey.isBlank() ? "/" : rawName(relativeKey);
        return new S3ObjectInfo(
                rawInfo.bucket(),
                relativeKey,
                name,
                directory,
                directory ? null : rawInfo.size(),
                rawInfo.eTag(),
                rawInfo.contentType(),
                rawInfo.lastModified());
    }

    private boolean isNotFound(S3StorageException ex) {
        Throwable cause = ex.getCause();
        if (cause instanceof ErrorResponseException errorResponseException) {
            String code = errorResponseException.errorResponse().code();
            return Objects.equals(code, "NoSuchKey")
                    || Objects.equals(code, "NoSuchBucket")
                    || Objects.equals(code, "NoSuchObject")
                    || Objects.equals(code, "NotFound");
        }
        String message = ex.getMessage();
        return message != null
                && (message.contains("NoSuchKey")
                        || message.contains("NoSuchBucket")
                        || message.contains("NoSuchObject")
                        || message.contains("NotFound"));
    }

    private S3StorageException toStorageException(String operation, Exception ex) {
        if (ex instanceof S3StorageException storageException) {
            return storageException;
        }
        if (ex instanceof ErrorResponseException errorResponseException) {
            String code = errorResponseException.errorResponse().code();
            String message = errorResponseException.errorResponse().message();
            return new S3StorageException("S3 " + operation + " failed: " + code
                    + (message != null && !message.isBlank() ? " - " + message : ""), ex);
        }
        if (ex instanceof MinioException) {
            return new S3StorageException("S3 " + operation + " failed: " + ex.getMessage(), ex);
        }
        if (ex instanceof IOException) {
            return new S3StorageException("S3 transport failed: " + ex.getMessage(), ex);
        }
        if (ex instanceof RuntimeException runtimeException) {
            return new S3StorageException("S3 " + operation + " failed: " + runtimeException.getMessage(), ex);
        }
        return new S3StorageException("S3 " + operation + " failed", ex);
    }

    private String resolveKey(String relativePath) {
        String normalized = normalizeRelativePath(relativePath);
        String rootPrefix = getConfig().getRootPrefix();
        if (rootPrefix.isBlank()) {
            return normalized;
        }
        if (normalized.isBlank()) {
            return rootPrefix;
        }
        return rootPrefix + "/" + normalized;
    }

    private String resolveDirectoryMarkerKey(String relativePath) {
        String normalized = normalizeRelativePath(relativePath);
        String baseKey = resolveKey(normalized);
        if (baseKey.isBlank()) {
            return "";
        }
        return baseKey.endsWith("/") ? baseKey : baseKey + "/";
    }

    private String resolveListPrefix(String relativePath) {
        String normalized = normalizeRelativePath(relativePath);
        if (normalized.isBlank()) {
            String rootPrefix = getConfig().getRootPrefix();
            return rootPrefix.isBlank() ? "" : rootPrefix + "/";
        }
        return resolveDirectoryMarkerKey(normalized);
    }

    private String stripRootPrefix(String rawKey, boolean directory) {
        String rootPrefix = getConfig().getRootPrefix();
        String normalizedKey = rawKey;
        if (directory && normalizedKey.endsWith("/")) {
            normalizedKey = normalizedKey.substring(0, normalizedKey.length() - 1);
        }
        if (rootPrefix.isBlank()) {
            return normalizedKey;
        }
        if (normalizedKey.equals(rootPrefix)) {
            return "";
        }
        String prefix = rootPrefix + "/";
        if (!normalizedKey.startsWith(prefix)) {
            return null;
        }
        return normalizedKey.substring(prefix.length());
    }

    private String normalizeRelativePath(String relativePath) {
        if (relativePath == null) {
            return "";
        }
        return relativePath;
    }

    private String rawName(String key) {
        String normalized = key;
        if (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        int separatorIndex = normalized.lastIndexOf('/');
        return separatorIndex >= 0 ? normalized.substring(separatorIndex + 1) : normalized;
    }

    private void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }

    private String getBucket() {
        return getConfig().getBucket();
    }

    private S3PluginConfig getConfig() {
        return configService.getConfig();
    }
}
