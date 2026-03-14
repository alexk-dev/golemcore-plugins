package me.golemcore.plugins.golemcore.pinchtab;

import com.fasterxml.jackson.databind.ObjectMapper;
import me.golemcore.plugin.api.extension.model.ToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PinchTabNavigateToolProviderTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private PinchTabHttpClient client;
    private PinchTabPluginConfig config;
    private PinchTabNavigateToolProvider provider;

    @BeforeEach
    void setUp() {
        client = mock(PinchTabHttpClient.class);
        when(client.isEnabled()).thenReturn(true);
        config = PinchTabPluginConfig.builder()
                .enabled(true)
                .defaultWaitFor("dom")
                .defaultBlockImages(false)
                .build();
        config.normalize();
        when(client.getConfig()).thenReturn(config);
        provider = new PinchTabNavigateToolProvider(client);
    }

    @Test
    void shouldOpenNewTabInSpecificInstance() throws Exception {
        when(client.postJson(eq("/instances/inst_1/tabs/open"), eq(Map.of("url", "https://pinchtab.com"))))
                .thenReturn(objectMapper.readTree("""
                        {
                          "tabId": "tab_123",
                          "url": "https://pinchtab.com",
                          "title": "PinchTab"
                        }
                        """));

        ToolResult result = provider.execute(Map.of(
                "url", "https://pinchtab.com",
                "instance_id", "inst_1"))
                .join();

        assertTrue(result.isSuccess());
        assertTrue(result.getOutput().contains("tab_123"));
        verify(client).postJson("/instances/inst_1/tabs/open", Map.of("url", "https://pinchtab.com"));
    }

    @Test
    void shouldRejectAdvancedOptionsForInstanceScopedOpen() throws Exception {
        ToolResult result = provider.execute(Map.of(
                "url", "https://pinchtab.com",
                "instance_id", "inst_1",
                "wait_for", "networkidle"))
                .join();

        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("does not support"));
        verify(client, never()).postJson(eq("/instances/inst_1/tabs/open"), anyMap());
        verify(client, never()).postJson(eq("/navigate"), anyMap());
    }

    @Test
    void shouldRejectAdvancedOptionsWhenDefaultInstanceIdIsConfigured() throws Exception {
        config.setDefaultInstanceId("inst_default");
        config.normalize();

        ToolResult result = provider.execute(Map.of(
                "url", "https://pinchtab.com",
                "block_images", true))
                .join();

        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("does not support"));
        verify(client, never()).postJson(eq("/instances/inst_default/tabs/open"), anyMap());
        verify(client, never()).postJson(eq("/navigate"), anyMap());
    }

    @Test
    void shouldRequireWaitSelectorWhenSelectorModeIsRequested() throws Exception {
        ToolResult result = provider.execute(Map.of(
                "url", "https://pinchtab.com",
                "tab_id", "tab_123",
                "wait_for", "selector"))
                .join();

        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("wait_selector is required"));
        verify(client, never()).postJson(eq("/navigate"), anyMap());
    }

    @Test
    void shouldPassWaitSelectorToTopLevelNavigate() throws Exception {
        when(client.postJson(eq("/navigate"), eq(Map.of(
                "url", "https://pinchtab.com",
                "tabId", "tab_123",
                "blockImages", false,
                "waitFor", "selector",
                "waitSelector", "#ready"))))
                .thenReturn(objectMapper.readTree("""
                        {
                          "tabId": "tab_123",
                          "url": "https://pinchtab.com",
                          "title": "PinchTab"
                        }
                        """));

        ToolResult result = provider.execute(Map.of(
                "url", "https://pinchtab.com",
                "tab_id", "tab_123",
                "wait_for", "selector",
                "wait_selector", "#ready"))
                .join();

        assertTrue(result.isSuccess());
        verify(client).postJson("/navigate", Map.of(
                "url", "https://pinchtab.com",
                "tabId", "tab_123",
                "blockImages", false,
                "waitFor", "selector",
                "waitSelector", "#ready"));
    }
}
