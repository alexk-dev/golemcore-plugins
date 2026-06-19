package me.golemcore.plugins.golemcore.s3;

import me.golemcore.plugin.api.extension.model.ToolDefinition;
import me.golemcore.plugin.api.extension.model.ToolFailureKind;
import me.golemcore.plugin.api.extension.model.ToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class S3FilesToolProviderTest {

    private S3FilesService service;
    private S3FilesToolProvider provider;

    @BeforeEach
    void setUp() {
        service = mock(S3FilesService.class);
        provider = new S3FilesToolProvider(service);
    }

    @Test
    void shouldExposeAllSupportedOperations() {
        ToolDefinition definition = provider.getDefinition();

        Map<String, Object> schema = definition.getInputSchema();
        Map<?, ?> properties = (Map<?, ?>) schema.get("properties");
        Map<?, ?> operation = (Map<?, ?>) properties.get("operation");

        assertEquals("s3_files", definition.getName());
        assertEquals(List.of(
                "list_directory",
                "read_file",
                "write_file",
                "create_directory",
                "delete",
                "move",
                "copy",
                "file_info"), operation.get("enum"));
    }

    @Test
    void shouldBeEnabledWhenConfigHasCredentialsAndBucket() {
        when(service.getConfig()).thenReturn(S3PluginConfig.builder()
                .enabled(true)
                .endpoint("https://storage.example.com")
                .accessKey("minio")
                .secretKey("secret")
                .bucket("files")
                .build());

        boolean enabled = provider.isEnabled();

        assertTrue(enabled);
    }

    @Test
    void shouldRejectMissingOperation() {
        ToolResult result = provider.execute(Map.of()).join();

        assertFalse(result.isSuccess());
        assertEquals(ToolFailureKind.EXECUTION_FAILED, result.getFailureKind());
        assertTrue(result.getError().contains("operation is required"));
    }

    @Test
    void shouldDispatchWriteToService() {
        when(service.writeFile("docs/file.bin", null, "AQID", true))
                .thenReturn(ToolResult.success("written"));

        ToolResult result = provider.execute(Map.of(
                "operation", "write_file",
                "path", "docs/file.bin",
                "content_base64", "AQID",
                "append", true)).join();

        assertTrue(result.isSuccess());
        verify(service).writeFile("docs/file.bin", null, "AQID", true);
    }

    @Test
    void shouldDispatchCopyToService() {
        when(service.copy("docs/a.txt", "archive/a.txt"))
                .thenReturn(ToolResult.success("copied"));

        ToolResult result = provider.execute(Map.of(
                "operation", "copy",
                "path", "docs/a.txt",
                "target_path", "archive/a.txt")).join();

        assertTrue(result.isSuccess());
        verify(service).copy("docs/a.txt", "archive/a.txt");
    }

    @Test
    void shouldRejectUnsupportedOperation() {
        ToolResult result = provider.execute(Map.of("operation", "unknown")).join();

        assertFalse(result.isSuccess());
        assertEquals(ToolFailureKind.EXECUTION_FAILED, result.getFailureKind());
        assertTrue(result.getError().contains("Unsupported"));
    }
}
