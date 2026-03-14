package me.golemcore.plugins.golemcore.pinchtab;

import me.golemcore.plugin.api.extension.model.Attachment;
import me.golemcore.plugin.api.extension.model.ToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PinchTabScreenshotToolProviderTest {

    private PinchTabHttpClient client;
    private PinchTabScreenshotToolProvider provider;

    @BeforeEach
    void setUp() {
        client = mock(PinchTabHttpClient.class);
        when(client.isEnabled()).thenReturn(true);
        PinchTabPluginConfig config = PinchTabPluginConfig.builder()
                .enabled(true)
                .defaultScreenshotQuality(77)
                .build();
        config.normalize();
        when(client.getConfig()).thenReturn(config);
        provider = new PinchTabScreenshotToolProvider(client);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldAttachScreenshotBytes() throws Exception {
        byte[] image = new byte[] { 1, 2, 3, 4 };
        when(client.getBytes(eq("/screenshot"), eq(Map.of(
                "tabId", "tab_7",
                "quality", 77,
                "raw", true))))
                .thenReturn(image);

        ToolResult result = provider.execute(Map.of("tab_id", "tab_7")).join();

        assertTrue(result.isSuccess());
        Map<String, Object> data = assertInstanceOf(Map.class, result.getData());
        Attachment attachment = assertInstanceOf(Attachment.class, data.get("attachment"));
        assertEquals(Attachment.Type.IMAGE, attachment.getType());
        assertEquals("pinchtab-screenshot.jpg", attachment.getFilename());
        assertEquals("image/jpeg", attachment.getMimeType());
        verify(client).getBytes("/screenshot", Map.of(
                "tabId", "tab_7",
                "quality", 77,
                "raw", true));
    }
}
