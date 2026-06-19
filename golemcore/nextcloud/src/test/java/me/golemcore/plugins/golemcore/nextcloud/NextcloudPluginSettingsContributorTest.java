package me.golemcore.plugins.golemcore.nextcloud;

import me.golemcore.plugin.api.extension.spi.PluginActionResult;
import me.golemcore.plugin.api.extension.spi.PluginSettingsSection;
import me.golemcore.plugins.golemcore.nextcloud.support.NextcloudTransportException;
import me.golemcore.plugins.golemcore.nextcloud.support.NextcloudWebDavClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NextcloudPluginSettingsContributorTest {

    private NextcloudPluginConfigService configService;
    private NextcloudWebDavClient client;
    private NextcloudPluginSettingsContributor contributor;
    private NextcloudPluginConfig config;

    @BeforeEach
    void setUp() {
        configService = mock(NextcloudPluginConfigService.class);
        client = mock(NextcloudWebDavClient.class);
        contributor = new NextcloudPluginSettingsContributor(configService, client);
        config = NextcloudPluginConfig.builder().build();
        config.normalize();
        when(configService.getConfig()).thenReturn(config);
    }

    @Test
    void shouldExposeSectionWithBlankSecretAndSafeDefaults() {
        PluginSettingsSection section = contributor.getSection("main");

        assertEquals(false, section.getValues().get("enabled"));
        assertEquals("https://nextcloud.example.com", section.getValues().get("baseUrl"));
        assertEquals("", section.getValues().get("appPassword"));
        assertEquals("/", section.getValues().get("rootPath"));
        assertEquals(30_000, section.getValues().get("timeoutMs"));
        assertEquals(false, section.getValues().get("allowInsecureTls"));
        assertEquals(50 * 1024 * 1024, section.getValues().get("maxDownloadBytes"));
        assertEquals(12_000, section.getValues().get("maxInlineTextChars"));
        assertFalse((Boolean) section.getValues().get("allowWrite"));
        assertFalse((Boolean) section.getValues().get("allowDelete"));
        assertFalse((Boolean) section.getValues().get("allowMove"));
        assertFalse((Boolean) section.getValues().get("allowCopy"));
    }

    @Test
    void shouldRoundTripSavedPolicyFlagsThroughGetSection() {
        NextcloudPluginConfig initialConfig = NextcloudPluginConfig.builder()
                .appPassword("existing-secret")
                .build();
        initialConfig.normalize();
        NextcloudPluginConfig persistedConfig = NextcloudPluginConfig.builder()
                .enabled(true)
                .baseUrl("https://cloud.example.com")
                .username("alex")
                .appPassword("existing-secret")
                .rootPath("AI")
                .timeoutMs(45_000)
                .allowInsecureTls(true)
                .maxDownloadBytes(4096)
                .maxInlineTextChars(2048)
                .allowWrite(true)
                .allowDelete(false)
                .allowMove(true)
                .allowCopy(false)
                .build();
        persistedConfig.normalize();
        when(configService.getConfig()).thenReturn(initialConfig, persistedConfig);

        Map<String, Object> values = new LinkedHashMap<>();
        values.put("enabled", true);
        values.put("baseUrl", "https://cloud.example.com");
        values.put("username", "alex");
        values.put("appPassword", "");
        values.put("rootPath", "/AI");
        values.put("timeoutMs", 45_000);
        values.put("allowInsecureTls", true);
        values.put("maxDownloadBytes", 4096);
        values.put("maxInlineTextChars", 2048);
        values.put("allowWrite", true);
        values.put("allowDelete", false);
        values.put("allowMove", true);
        values.put("allowCopy", false);

        PluginSettingsSection section = contributor.saveSection("main", values);

        ArgumentCaptor<NextcloudPluginConfig> captor = ArgumentCaptor.forClass(NextcloudPluginConfig.class);
        verify(configService).save(captor.capture());
        NextcloudPluginConfig saved = captor.getValue();
        assertEquals("existing-secret", saved.getAppPassword());
        assertTrue(saved.getAllowWrite());
        assertFalse(saved.getAllowDelete());
        assertTrue(saved.getAllowMove());
        assertFalse(saved.getAllowCopy());

        assertEquals("", section.getValues().get("appPassword"));
        assertEquals("/AI", section.getValues().get("rootPath"));
        assertTrue((Boolean) section.getValues().get("allowWrite"));
        assertFalse((Boolean) section.getValues().get("allowDelete"));
        assertTrue((Boolean) section.getValues().get("allowMove"));
        assertFalse((Boolean) section.getValues().get("allowCopy"));
    }

    @Test
    void shouldReturnOkWhenConnectionTestSucceeds() {
        config.setUsername("alex");
        config.setAppPassword("secret");
        when(client.listDirectory("")).thenReturn(List.of());

        PluginActionResult result = contributor.executeAction("main", "test-connection", Map.of());

        assertEquals("ok", result.getStatus());
        assertEquals("Connected to Nextcloud. Root returned 0 item(s).", result.getMessage());
    }

    @Test
    void shouldReturnErrorWhenCredentialsAreMissing() {
        PluginActionResult result = contributor.executeAction("main", "test-connection", Map.of());

        assertEquals("error", result.getStatus());
        assertEquals("Nextcloud username and app password must be configured.", result.getMessage());
    }

    @Test
    void shouldReturnErrorWhenConnectionTestFails() {
        config.setUsername("alex");
        config.setAppPassword("secret");
        when(client.listDirectory("")).thenThrow(new NextcloudTransportException(
                "Nextcloud transport failed: timeout",
                new IOException("timeout")));

        PluginActionResult result = contributor.executeAction("main", "test-connection", Map.of());

        assertEquals("error", result.getStatus());
        assertEquals("Connection failed: Nextcloud transport failed: timeout", result.getMessage());
    }
}
