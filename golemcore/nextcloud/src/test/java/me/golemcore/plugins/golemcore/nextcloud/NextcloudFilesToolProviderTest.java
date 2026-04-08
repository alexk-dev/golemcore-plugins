package me.golemcore.plugins.golemcore.nextcloud;

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

class NextcloudFilesToolProviderTest {

    private NextcloudFilesService service;
    private NextcloudFilesToolProvider provider;

    @BeforeEach
    void setUp() {
        service = mock(NextcloudFilesService.class);
        provider = new NextcloudFilesToolProvider(service);
    }

    @Test
    void shouldExposeAllSupportedOperations() {
        ToolDefinition definition = provider.getDefinition();

        Map<String, Object> schema = definition.getInputSchema();
        Map<?, ?> properties = (Map<?, ?>) schema.get("properties");
        Map<?, ?> operation = (Map<?, ?>) properties.get("operation");

        assertEquals("nextcloud_files", definition.getName());
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
    void shouldRequirePathForReadFileInSchema() {
        ToolDefinition definition = provider.getDefinition();

        Map<String, Object> schema = definition.getInputSchema();
        List<?> allOf = (List<?>) schema.get("allOf");
        Map<?, ?> readRule = (Map<?, ?>) allOf.stream()
                .map(Map.class::cast)
                .filter(rule -> {
                    Map<?, ?> condition = (Map<?, ?>) rule.get("if");
                    Map<?, ?> conditionProperties = (Map<?, ?>) condition.get("properties");
                    Map<?, ?> operationProperty = (Map<?, ?>) conditionProperties.get("operation");
                    return "read_file".equals(operationProperty.get("const"));
                })
                .findFirst()
                .orElseThrow();
        Map<?, ?> thenClause = (Map<?, ?>) readRule.get("then");

        assertEquals(List.of("path"), thenClause.get("required"));
    }

    @Test
    void shouldBeEnabledWhenConfigHasCredentialsAndPluginIsEnabled() {
        when(service.getConfig()).thenReturn(NextcloudPluginConfig.builder()
                .enabled(true)
                .username("alex")
                .appPassword("secret")
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
    void shouldDispatchReadToService() {
        when(service.readFile("docs/file.bin"))
                .thenReturn(ToolResult.success("read"));

        ToolResult result = provider.execute(Map.of(
                "operation", "read_file",
                "path", "docs/file.bin")).join();

        assertTrue(result.isSuccess());
        verify(service).readFile("docs/file.bin");
    }

    @Test
    void shouldDispatchListDirectoryWithoutPath() {
        when(service.listDirectory(null)).thenReturn(ToolResult.success("listed"));

        ToolResult result = provider.execute(Map.of("operation", "list_directory")).join();

        assertTrue(result.isSuccess());
        verify(service).listDirectory(null);
    }

    @Test
    void shouldDispatchCopyToService() {
        when(service.copy("docs/source.bin", "docs/target.bin"))
                .thenReturn(ToolResult.success("copied"));

        ToolResult result = provider.execute(Map.of(
                "operation", "copy",
                "path", "docs/source.bin",
                "target_path", "docs/target.bin")).join();

        assertTrue(result.isSuccess());
        verify(service).copy("docs/source.bin", "docs/target.bin");
    }

    @Test
    void shouldRejectUnsupportedOperation() {
        ToolResult result = provider.execute(Map.of("operation", "unknown")).join();

        assertFalse(result.isSuccess());
        assertEquals(ToolFailureKind.EXECUTION_FAILED, result.getFailureKind());
        assertTrue(result.getError().contains("Unsupported"));
    }
}
