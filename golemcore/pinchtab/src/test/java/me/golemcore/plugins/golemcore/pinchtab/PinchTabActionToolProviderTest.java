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

class PinchTabActionToolProviderTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private PinchTabHttpClient client;
    private PinchTabActionToolProvider provider;

    @BeforeEach
    void setUp() {
        client = mock(PinchTabHttpClient.class);
        when(client.isEnabled()).thenReturn(true);
        PinchTabPluginConfig config = PinchTabPluginConfig.builder().enabled(true).build();
        config.normalize();
        when(client.getConfig()).thenReturn(config);
        provider = new PinchTabActionToolProvider(client);
    }

    @Test
    void shouldRejectClickWithoutRefOrSelector() throws Exception {
        ToolResult result = provider.execute(Map.of("kind", "click")).join();

        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("requires ref or selector"));
        verify(client, never()).postJson(eq("/action"), anyMap());
    }

    @Test
    void shouldExecutePressWithoutRef() throws Exception {
        when(client.postJson(eq("/action"), eq(Map.of(
                "kind", "press",
                "key", "Enter"))))
                .thenReturn(objectMapper.readTree("{\"status\":\"ok\"}"));
        when(client.prettyPrint(org.mockito.ArgumentMatchers.any())).thenReturn("{\n  \"status\" : \"ok\"\n}");

        ToolResult result = provider.execute(Map.of(
                "kind", "press",
                "key", "Enter"))
                .join();

        assertTrue(result.isSuccess());
        verify(client).postJson("/action", Map.of(
                "kind", "press",
                "key", "Enter"));
    }

    @Test
    void shouldExecuteFillWithText() throws Exception {
        when(client.postJson(eq("/action"), eq(Map.of(
                "kind", "fill",
                "selector", "#email",
                "text", "user@test.com"))))
                .thenReturn(objectMapper.readTree("{\"status\":\"ok\"}"));
        when(client.prettyPrint(org.mockito.ArgumentMatchers.any())).thenReturn("{\n  \"status\" : \"ok\"\n}");

        ToolResult result = provider.execute(Map.of(
                "kind", "fill",
                "selector", "#email",
                "text", "user@test.com"))
                .join();

        assertTrue(result.isSuccess());
        verify(client).postJson("/action", Map.of(
                "kind", "fill",
                "selector", "#email",
                "text", "user@test.com"));
    }

    @Test
    void shouldAcceptLegacyValueAliasForFill() throws Exception {
        when(client.postJson(eq("/action"), eq(Map.of(
                "kind", "fill",
                "selector", "#email",
                "text", "user@test.com"))))
                .thenReturn(objectMapper.readTree("{\"status\":\"ok\"}"));
        when(client.prettyPrint(org.mockito.ArgumentMatchers.any())).thenReturn("{\n  \"status\" : \"ok\"\n}");

        ToolResult result = provider.execute(Map.of(
                "kind", "fill",
                "selector", "#email",
                "value", "user@test.com"))
                .join();

        assertTrue(result.isSuccess());
        verify(client).postJson("/action", Map.of(
                "kind", "fill",
                "selector", "#email",
                "text", "user@test.com"));
    }
}
