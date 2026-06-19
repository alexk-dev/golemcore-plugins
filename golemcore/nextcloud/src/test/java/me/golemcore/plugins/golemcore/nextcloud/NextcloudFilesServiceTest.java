package me.golemcore.plugins.golemcore.nextcloud;

import me.golemcore.plugin.api.extension.model.Attachment;
import me.golemcore.plugin.api.extension.model.ToolFailureKind;
import me.golemcore.plugin.api.extension.model.ToolResult;
import me.golemcore.plugins.golemcore.nextcloud.support.NextcloudFileContent;
import me.golemcore.plugins.golemcore.nextcloud.support.NextcloudResource;
import me.golemcore.plugins.golemcore.nextcloud.support.NextcloudWebDavClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NextcloudFilesServiceTest {

    private NextcloudPluginConfigService configService;
    private NextcloudWebDavClient client;
    private NextcloudFilesService service;

    @BeforeEach
    void setUp() {
        configService = mock(NextcloudPluginConfigService.class);
        client = mock(NextcloudWebDavClient.class);
        when(configService.getConfig()).thenReturn(defaultConfig());
        service = new NextcloudFilesService(client, configService);
    }

    @Test
    void shouldListRootDirectory() {
        when(client.listDirectory("")).thenReturn(List.of(
                new NextcloudResource("docs", "docs", true, null, null, null, null),
                new NextcloudResource("notes.md", "notes.md", false, 123L, "text/markdown", null, null)));

        ToolResult result = service.listDirectory("");

        assertTrue(result.isSuccess());
        assertTrue(result.getOutput().contains("Directory: /"));
        assertTrue(result.getOutput().contains("[DIR]  docs/"));
        assertTrue(result.getOutput().contains("[FILE] notes.md"));
    }

    @Test
    void shouldReturnInlineTextWhenReadFileIsText() {
        when(client.readFile("docs/readme.md")).thenReturn(new NextcloudFileContent(
                "docs/readme.md",
                "hello".getBytes(),
                "text/markdown; charset=utf-8",
                5L,
                "etag",
                "Mon, 01 Jan 2026 00:00:00 GMT"));

        ToolResult result = service.readFile("docs/readme.md");

        assertTrue(result.isSuccess());
        assertEquals("hello", result.getOutput());
        Map<?, ?> data = assertInstanceOf(Map.class, result.getData());
        assertEquals("docs/readme.md", data.get("path"));
        assertEquals(true, data.get("is_text"));
        assertEquals(false, data.get("truncated"));
    }

    @Test
    void shouldAttachBinaryFilesOnRead() {
        when(client.readFile("docs/archive.zip")).thenReturn(new NextcloudFileContent(
                "docs/archive.zip",
                new byte[] { 1, 2, 3 },
                "application/zip",
                3L,
                null,
                null));

        ToolResult result = service.readFile("docs/archive.zip");

        assertTrue(result.isSuccess());
        assertTrue(result.getOutput().contains("Read binary file"));
        Map<?, ?> data = assertInstanceOf(Map.class, result.getData());
        Attachment attachment = assertInstanceOf(Attachment.class, data.get("attachment"));
        assertEquals("archive.zip", attachment.getFilename());
        assertEquals("application/zip", attachment.getMimeType());
        assertEquals(Attachment.Type.DOCUMENT, attachment.getType());
    }

    @Test
    void shouldAttachLongTextFilesWhenTruncated() {
        when(configService.getConfig()).thenReturn(NextcloudPluginConfig.builder()
                .enabled(true)
                .username("alex")
                .appPassword("secret")
                .maxInlineTextChars(4)
                .maxDownloadBytes(100)
                .build());
        when(client.readFile("docs/readme.md")).thenReturn(new NextcloudFileContent(
                "docs/readme.md",
                "hello".getBytes(),
                "text/plain",
                5L,
                null,
                null));

        ToolResult result = service.readFile("docs/readme.md");

        assertTrue(result.isSuccess());
        Map<?, ?> data = assertInstanceOf(Map.class, result.getData());
        assertEquals(true, data.get("truncated"));
        assertEquals(5, data.get("originalLength"));
        assertInstanceOf(Attachment.class, data.get("attachment"));
    }

    @Test
    void shouldDenyWriteWhenDisabled() {
        when(configService.getConfig()).thenReturn(NextcloudPluginConfig.builder()
                .enabled(true)
                .username("alex")
                .appPassword("secret")
                .allowWrite(false)
                .build());

        ToolResult result = service.writeFile("docs/file.txt", "hello", null, false);

        assertFalse(result.isSuccess());
        assertEquals(ToolFailureKind.POLICY_DENIED, result.getFailureKind());
        assertTrue(result.getError().contains("write is disabled"));
    }

    @Test
    void shouldWriteBase64BinaryContent() {
        ToolResult result = service.writeFile("docs/file.bin", null, "AQID", false);

        assertTrue(result.isSuccess());
        verify(client).createDirectoryRecursive("docs");
        verify(client).writeFile("docs/file.bin", new byte[] { 1, 2, 3 });
    }

    @Test
    void shouldAppendExistingFileContent() {
        when(client.fileInfo("docs/file.txt")).thenReturn(new NextcloudResource(
                "docs/file.txt",
                "file.txt",
                false,
                3L,
                "text/plain",
                null,
                null));
        when(client.readFile("docs/file.txt")).thenReturn(new NextcloudFileContent(
                "docs/file.txt",
                "abc".getBytes(),
                "text/plain",
                3L,
                null,
                null));

        ToolResult result = service.writeFile("docs/file.txt", "def", null, true);

        assertTrue(result.isSuccess());
        verify(client).writeFile("docs/file.txt", "abcdef".getBytes());
    }

    @Test
    void shouldCreateDirectories() {
        ToolResult result = service.createDirectory("docs/archive");

        assertTrue(result.isSuccess());
        verify(client).createDirectoryRecursive("docs/archive");
    }

    @Test
    void shouldDenyDeleteWhenDisabled() {
        when(configService.getConfig()).thenReturn(NextcloudPluginConfig.builder()
                .enabled(true)
                .username("alex")
                .appPassword("secret")
                .allowDelete(false)
                .build());

        ToolResult result = service.delete("docs/file.txt");

        assertFalse(result.isSuccess());
        assertEquals(ToolFailureKind.POLICY_DENIED, result.getFailureKind());
    }

    @Test
    void shouldMoveFiles() {
        ToolResult result = service.move("docs/file.txt", "archive/file.txt");

        assertTrue(result.isSuccess());
        verify(client).createDirectoryRecursive("archive");
        verify(client).move("docs/file.txt", "archive/file.txt");
    }

    @Test
    void shouldCopyFiles() {
        ToolResult result = service.copy("docs/file.txt", "archive/file.txt");

        assertTrue(result.isSuccess());
        verify(client).createDirectoryRecursive("archive");
        verify(client).copy("docs/file.txt", "archive/file.txt");
    }

    @Test
    void shouldReturnFileInfo() {
        when(client.fileInfo("docs/file.txt")).thenReturn(new NextcloudResource(
                "docs/file.txt",
                "file.txt",
                false,
                42L,
                "text/plain",
                "etag",
                "Mon, 01 Jan 2026 00:00:00 GMT"));

        ToolResult result = service.fileInfo("docs/file.txt");

        assertTrue(result.isSuccess());
        assertTrue(result.getOutput().contains("Path: docs/file.txt"));
        assertTrue(result.getOutput().contains("MIME Type: text/plain"));
    }

    private NextcloudPluginConfig defaultConfig() {
        return NextcloudPluginConfig.builder()
                .enabled(true)
                .username("alex")
                .appPassword("secret")
                .allowWrite(true)
                .allowDelete(true)
                .allowMove(true)
                .allowCopy(true)
                .maxDownloadBytes(1024)
                .maxInlineTextChars(100)
                .build();
    }
}
