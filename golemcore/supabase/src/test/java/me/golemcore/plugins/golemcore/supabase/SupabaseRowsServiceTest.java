package me.golemcore.plugins.golemcore.supabase;

import me.golemcore.plugin.api.extension.model.ToolFailureKind;
import me.golemcore.plugin.api.extension.model.ToolResult;
import me.golemcore.plugins.golemcore.supabase.support.SupabaseApiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SupabaseRowsServiceTest {

    private SupabaseApiClient apiClient;
    private SupabasePluginConfigService configService;
    private SupabaseRowsService service;
    private SupabasePluginConfig config;

    @BeforeEach
    void setUp() {
        apiClient = mock(SupabaseApiClient.class);
        configService = mock(SupabasePluginConfigService.class);
        service = new SupabaseRowsService(apiClient, configService);
        config = SupabasePluginConfig.builder()
                .enabled(true)
                .projectUrl("https://project.supabase.co")
                .apiKey("token")
                .defaultSchema("public")
                .defaultTable("tasks")
                .defaultSelect("*")
                .defaultLimit(20)
                .allowWrite(true)
                .allowDelete(true)
                .build();
        config.normalize();
        when(configService.getConfig()).thenReturn(config);
    }

    @Test
    void shouldSelectRowsUsingConfiguredDefaults() {
        when(apiClient.selectRows("tasks", "public", "*", 20, null, null, Optional.empty(), Map.of()))
                .thenReturn(List.of(Map.of("id", 1, "name", "Alice")));

        ToolResult result = service.selectRows(null, null, null, null, null, null, Optional.empty(), Map.of());

        assertTrue(result.isSuccess());
        assertTrue(result.getOutput().contains("Alice"));
        verify(apiClient).selectRows("tasks", "public", "*", 20, null, null, Optional.empty(), Map.of());
    }

    @Test
    void shouldInsertRowWhenWriteIsAllowed() {
        when(apiClient.insertRow("tasks", "public", Map.of("name", "Alice")))
                .thenReturn(List.of(Map.of("id", 7, "name", "Alice")));

        ToolResult result = service.insertRow(null, null, Map.of("name", "Alice"));

        assertTrue(result.isSuccess());
        assertTrue(result.getOutput().contains("Inserted Supabase rows into public.tasks"));
        verify(apiClient).insertRow("tasks", "public", Map.of("name", "Alice"));
    }

    @Test
    void shouldRequireFiltersForDelete() {
        ToolResult result = service.deleteRows(null, null, Map.of());

        assertFalse(result.isSuccess());
        assertEquals(
                "filters must contain at least one PostgREST filter expression for update_rows or delete_rows",
                result.getError());
    }

    @Test
    void shouldRejectDeleteWhenDisabled() {
        config.setAllowDelete(false);

        ToolResult result = service.deleteRows(null, null, Map.of("id", "eq.1"));

        assertFalse(result.isSuccess());
        assertEquals(ToolFailureKind.POLICY_DENIED, result.getFailureKind());
        assertEquals("Supabase delete is disabled in plugin settings", result.getError());
    }
}
