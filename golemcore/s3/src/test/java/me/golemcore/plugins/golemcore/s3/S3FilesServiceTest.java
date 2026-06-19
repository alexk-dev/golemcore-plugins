package me.golemcore.plugins.golemcore.s3;

import me.golemcore.plugin.api.extension.model.Attachment;
import me.golemcore.plugin.api.extension.model.ToolFailureKind;
import me.golemcore.plugin.api.extension.model.ToolResult;
import me.golemcore.plugins.golemcore.s3.support.S3MinioClient;
import me.golemcore.plugins.golemcore.s3.support.S3ObjectContent;
import me.golemcore.plugins.golemcore.s3.support.S3ObjectInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class S3FilesServiceTest {

    private S3PluginConfigService configService;
    private S3MinioClient client;
    private S3FilesService service;

    @BeforeEach
    void setUp() {
        configService = mock(S3PluginConfigService.class);
        client = mock(S3MinioClient.class);
        when(configService.getConfig()).thenReturn(defaultConfig());
        service = new S3FilesService(client, configService);
    }

    @Test
    void shouldListRootDirectory() {
        when(client.listDirectory("")).thenReturn(List.of(
                new S3ObjectInfo("files", "docs", "docs", true, null, null, null, null),
                new S3ObjectInfo("files", "notes.md", "notes.md", false, 123L, "etag", "text/markdown", null)));

        ToolResult result = service.listDirectory("");

        assertTrue(result.isSuccess());
        assertTrue(result.getOutput().contains("Directory: /"));
        assertTrue(result.getOutput().contains("[DIR]  docs/"));
        assertTrue(result.getOutput().contains("[FILE] notes.md"));
    }

    @Test
    void shouldReturnInlineTextWhenReadFileIsText() {
        when(client.findObject("docs/readme.md")).thenReturn(file("docs/readme.md", 5L, "text/plain"));
        when(client.readObject("docs/readme.md")).thenReturn(new S3ObjectContent(
                "files",
                "docs/readme.md",
                "hello".getBytes(),
                "etag",
                "text/plain; charset=utf-8",
                ZonedDateTime.parse("2026-04-08T12:00:00Z")));

        ToolResult result = service.readFile("docs/readme.md");

        assertTrue(result.isSuccess());
        assertEquals("hello", result.getOutput());
        Map<?, ?> data = assertInstanceOf(Map.class, result.getData());
        assertEquals("docs/readme.md", data.get("path"));
        assertEquals(true, data.get("is_text"));
        assertEquals(false, data.get("truncated"));
    }

    @Test
    void shouldAttachBinaryObjectsOnRead() {
        when(client.findObject("docs/archive.zip")).thenReturn(file("docs/archive.zip", 3L, "application/zip"));
        when(client.readObject("docs/archive.zip")).thenReturn(new S3ObjectContent(
                "files",
                "docs/archive.zip",
                new byte[] { 1, 2, 3 },
                "etag",
                "application/zip",
                null));

        ToolResult result = service.readFile("docs/archive.zip");

        assertTrue(result.isSuccess());
        Map<?, ?> data = assertInstanceOf(Map.class, result.getData());
        Attachment attachment = assertInstanceOf(Attachment.class, data.get("attachment"));
        assertEquals("archive.zip", attachment.getFilename());
        assertEquals("application/zip", attachment.getMimeType());
    }

    @Test
    void shouldWriteBase64BinaryContent() {
        ToolResult result = service.writeFile("docs/file.bin", null, "AQID", false);

        assertTrue(result.isSuccess());
        verify(client).writeObject("docs/file.bin", new byte[] { 1, 2, 3 }, "application/octet-stream");
    }

    @Test
    void shouldAppendExistingObjectContent() {
        when(client.findObject("docs/file.txt")).thenReturn(file("docs/file.txt", 3L, "text/plain"));
        when(client.readObject("docs/file.txt")).thenReturn(new S3ObjectContent(
                "files",
                "docs/file.txt",
                "abc".getBytes(),
                "etag",
                "text/plain",
                null));

        ToolResult result = service.writeFile("docs/file.txt", "def", null, true);

        assertTrue(result.isSuccess());
        verify(client).writeObject("docs/file.txt", "abcdef".getBytes(), "text/plain");
    }

    @Test
    void shouldCreateDirectoryMarkers() {
        when(client.findObject("docs/archive")).thenReturn(null);
        when(client.directoryExists("docs/archive")).thenReturn(false);

        ToolResult result = service.createDirectory("docs/archive");

        assertTrue(result.isSuccess());
        verify(client).createDirectoryMarker("docs/archive");
    }

    @Test
    void shouldDeleteObjects() {
        when(client.findObject("docs/file.txt")).thenReturn(file("docs/file.txt", 1L, "text/plain"));

        ToolResult result = service.delete("docs/file.txt");

        assertTrue(result.isSuccess());
        verify(client).deleteObject("docs/file.txt");
    }

    @Test
    void shouldDeleteDirectoriesRecursively() {
        when(client.findObject("docs/archive")).thenReturn(null);
        when(client.directoryExists("docs/archive")).thenReturn(true);
        when(client.findDirectoryMarker("docs/archive")).thenReturn(new S3ObjectInfo(
                "files",
                "docs/archive",
                "archive",
                true,
                null,
                null,
                null,
                null));
        when(client.listTree("docs/archive")).thenReturn(List.of(
                new S3ObjectInfo("files", "docs/archive/file.txt", "file.txt", false, 1L, "etag", "text/plain", null)));

        ToolResult result = service.delete("docs/archive");

        assertTrue(result.isSuccess());
        verify(client).deleteObject("docs/archive/file.txt");
        verify(client).deleteDirectoryMarker("docs/archive");
    }

    @Test
    void shouldMoveObjects() {
        when(client.findObject("docs/file.txt")).thenReturn(file("docs/file.txt", 1L, "text/plain"));
        when(client.findObject("archive/file.txt")).thenReturn(null);
        when(client.directoryExists("archive/file.txt")).thenReturn(false);

        ToolResult result = service.move("docs/file.txt", "archive/file.txt");

        assertTrue(result.isSuccess());
        verify(client).copyObject("docs/file.txt", "archive/file.txt");
        verify(client).deleteObject("docs/file.txt");
    }

    @Test
    void shouldCopyObjects() {
        when(client.findObject("docs/file.txt")).thenReturn(file("docs/file.txt", 1L, "text/plain"));
        when(client.findObject("archive/file.txt")).thenReturn(null);
        when(client.directoryExists("archive/file.txt")).thenReturn(false);

        ToolResult result = service.copy("docs/file.txt", "archive/file.txt");

        assertTrue(result.isSuccess());
        verify(client).copyObject("docs/file.txt", "archive/file.txt");
    }

    @Test
    void shouldDenyWriteWhenDisabled() {
        when(configService.getConfig()).thenReturn(S3PluginConfig.builder()
                .enabled(true)
                .endpoint("https://storage.example.com")
                .accessKey("minio")
                .secretKey("secret")
                .bucket("files")
                .allowWrite(false)
                .build());

        ToolResult result = service.writeFile("docs/file.txt", "hello", null, false);

        assertFalse(result.isSuccess());
        assertEquals(ToolFailureKind.POLICY_DENIED, result.getFailureKind());
    }

    private S3ObjectInfo file(String key, Long size, String contentType) {
        return new S3ObjectInfo("files", key, key.substring(key.lastIndexOf('/') + 1), false, size, "etag", contentType,
                ZonedDateTime.parse("2026-04-08T12:00:00Z"));
    }

    private S3PluginConfig defaultConfig() {
        return S3PluginConfig.builder()
                .enabled(true)
                .endpoint("https://storage.example.com")
                .accessKey("minio")
                .secretKey("secret")
                .bucket("files")
                .allowWrite(true)
                .allowDelete(true)
                .allowMove(true)
                .allowCopy(true)
                .maxDownloadBytes(1024)
                .maxInlineTextChars(100)
                .build();
    }
}
