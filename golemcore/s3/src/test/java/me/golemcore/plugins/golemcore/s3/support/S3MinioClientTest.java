package me.golemcore.plugins.golemcore.s3.support;

import me.golemcore.plugins.golemcore.s3.S3PluginConfig;
import me.golemcore.plugins.golemcore.s3.S3PluginConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class S3MinioClientTest {

    private S3PluginConfigService configService;
    private StubS3MinioClient client;

    @BeforeEach
    void setUp() {
        configService = mock(S3PluginConfigService.class);
        when(configService.getConfig()).thenReturn(S3PluginConfig.builder()
                .enabled(true)
                .endpoint("https://storage.example.com")
                .region("us-east-1")
                .accessKey("minio")
                .secretKey("secret")
                .bucket("files")
                .rootPrefix("AI")
                .build());
        client = new StubS3MinioClient(configService);
    }

    @Test
    void shouldEnsureBucketExistsWithoutCreatingWhenPresent() {
        client.bucketExists = true;

        client.ensureBucketReady();

        assertFalse(client.makeBucketCalled);
    }

    @Test
    void shouldCreateBucketWhenMissingAndAutoCreateEnabled() {
        when(configService.getConfig()).thenReturn(S3PluginConfig.builder()
                .enabled(true)
                .endpoint("https://storage.example.com")
                .region("eu-west-1")
                .accessKey("minio")
                .secretKey("secret")
                .bucket("files")
                .rootPrefix("AI")
                .autoCreateBucket(true)
                .build());
        client.bucketExists = false;

        client.ensureBucketReady();

        assertTrue(client.makeBucketCalled);
        assertEquals("files", client.lastBucket);
        assertEquals("eu-west-1", client.lastRegion);
    }

    @Test
    void shouldListDirectoryAndStripRootPrefix() {
        client.bucketExists = true;
        client.listObjectsResult = List.of(
                new S3ObjectInfo("files", "AI/docs/readme.md", "readme.md", false, 5L, "etag", "text/plain", null),
                new S3ObjectInfo("files", "AI/docs/archive/", "archive", true, null, null, null, null));

        List<S3ObjectInfo> entries = client.listDirectory("docs");

        assertEquals(2, entries.size());
        assertEquals("docs/readme.md", entries.get(0).key());
        assertEquals("docs/archive", entries.get(1).key());
    }

    @Test
    void shouldReadObjectContent() {
        client.bucketExists = true;
        client.statObject = new S3ObjectInfo("files", "AI/docs/readme.md", "readme.md", false, 5L, "etag",
                "text/plain", ZonedDateTime.parse("2026-04-08T12:00:00Z"));
        client.objectContent = new S3ObjectContent("files", "AI/docs/readme.md", "hello".getBytes(), "etag",
                "text/plain", ZonedDateTime.parse("2026-04-08T12:00:00Z"));

        S3ObjectContent content = client.readObject("docs/readme.md");

        assertEquals("AI/docs/readme.md", client.lastKey);
        assertEquals("hello", new String(content.bytes()));
    }

    @Test
    void shouldWriteObjectsThroughResolvedRootPrefix() {
        client.bucketExists = true;

        client.writeObject("docs/readme.md", "hello".getBytes(), "text/plain");

        assertEquals("AI/docs/readme.md", client.lastKey);
        assertEquals("text/plain", client.lastContentType);
    }

    @Test
    void shouldCopyAndDeleteObjectsThroughResolvedRootPrefix() {
        client.bucketExists = true;
        client.statObject = new S3ObjectInfo("files", "AI/docs/readme.md", "readme.md", false, 5L, "etag",
                "text/plain", null);

        client.copyObject("docs/readme.md", "archive/readme.md");
        client.deleteObject("docs/readme.md");

        assertEquals("AI/archive/readme.md", client.lastTargetKey);
        assertEquals("AI/docs/readme.md", client.lastDeletedKey);
    }

    @Test
    void shouldExposeDirectoryMarkersSeparately() {
        client.bucketExists = true;
        client.directoryMarker = new S3ObjectInfo("files", "AI/docs/", "docs", true, null, null, null, null);

        S3ObjectInfo marker = client.findDirectoryMarker("docs");

        assertNotNull(marker);
        assertTrue(marker.directory());
        assertEquals("docs", marker.key());
    }

    @Test
    void shouldReturnNullWhenObjectIsMissing() {
        client.bucketExists = true;
        client.notFound = true;

        S3ObjectInfo result = client.findObject("docs/missing.txt");

        assertEquals(null, result);
    }

    private static final class StubS3MinioClient extends S3MinioClient {

        private boolean bucketExists;
        private boolean makeBucketCalled;
        private boolean notFound;
        private String lastBucket;
        private String lastRegion;
        private String lastKey;
        private String lastTargetKey;
        private String lastDeletedKey;
        private String lastContentType;
        private List<S3ObjectInfo> listObjectsResult = List.of();
        private S3ObjectInfo statObject;
        private S3ObjectInfo directoryMarker;
        private S3ObjectContent objectContent;

        private StubS3MinioClient(S3PluginConfigService configService) {
            super(configService);
        }

        @Override
        protected boolean bucketExistsInternal(String bucket) {
            lastBucket = bucket;
            return bucketExists;
        }

        @Override
        protected void makeBucketInternal(String bucket, String region) {
            makeBucketCalled = true;
            lastBucket = bucket;
            lastRegion = region;
        }

        @Override
        protected List<S3ObjectInfo> listObjectsInternal(String bucket, String prefix, boolean recursive) {
            return listObjectsResult;
        }

        @Override
        protected S3ObjectInfo statObjectInternal(String bucket, String key) {
            lastKey = key;
            if (notFound) {
                throw new S3StorageException("S3 object stat failed: NotFound");
            }
            if (directoryMarker != null && key.endsWith("/")) {
                return directoryMarker;
            }
            return statObject;
        }

        @Override
        protected S3ObjectContent getObjectInternal(String bucket, String key, S3ObjectInfo metadata) {
            lastKey = key;
            return objectContent;
        }

        @Override
        protected void putObjectInternal(String bucket, String key, byte[] bytes, String contentType) {
            lastBucket = bucket;
            lastKey = key;
            lastContentType = contentType;
        }

        @Override
        protected void copyObjectInternal(String bucket, String sourceKey, String targetKey, S3ObjectInfo sourceInfo) {
            lastBucket = bucket;
            lastKey = sourceKey;
            lastTargetKey = targetKey;
        }

        @Override
        protected void removeObjectInternal(String bucket, String key) {
            lastBucket = bucket;
            lastDeletedKey = key;
        }
    }
}
