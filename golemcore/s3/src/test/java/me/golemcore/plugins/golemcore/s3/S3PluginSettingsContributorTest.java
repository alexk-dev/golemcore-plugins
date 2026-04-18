package me.golemcore.plugins.golemcore.s3;

import me.golemcore.plugin.api.extension.spi.PluginActionResult;
import me.golemcore.plugin.api.extension.spi.PluginSettingsSection;
import me.golemcore.plugins.golemcore.s3.support.S3MinioClient;
import me.golemcore.plugins.golemcore.s3.support.S3StorageException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class S3PluginSettingsContributorTest {

    private S3PluginConfigService configService;
    private S3MinioClient client;
    private S3PluginSettingsContributor contributor;
    private S3PluginConfig config;

    @BeforeEach
    void setUp() {
        configService = mock(S3PluginConfigService.class);
        client = mock(S3MinioClient.class);
        contributor = new S3PluginSettingsContributor(configService, client);
        config = S3PluginConfig.builder().build();
        config.normalize();
        when(configService.getConfig()).thenReturn(config);
    }

    @Test
    void shouldExposeSectionWithBlankSecretAndSafeDefaults() {
        PluginSettingsSection section = contributor.getSection("main");

        assertEquals(false, section.getValues().get("enabled"));
        assertEquals("https://play.min.io", section.getValues().get("endpoint"));
        assertEquals("us-east-1", section.getValues().get("region"));
        assertEquals("", section.getValues().get("secretKey"));
        assertEquals("/", section.getValues().get("rootPrefix"));
        assertEquals(30_000, section.getValues().get("timeoutMs"));
        assertEquals(false, section.getValues().get("allowInsecureTls"));
        assertEquals(50 * 1024 * 1024, section.getValues().get("maxDownloadBytes"));
        assertEquals(12_000, section.getValues().get("maxInlineTextChars"));
        assertFalse((Boolean) section.getValues().get("autoCreateBucket"));
        assertFalse((Boolean) section.getValues().get("allowWrite"));
        assertFalse((Boolean) section.getValues().get("allowDelete"));
        assertFalse((Boolean) section.getValues().get("allowMove"));
        assertFalse((Boolean) section.getValues().get("allowCopy"));
    }

    @Test
    void shouldRoundTripSavedPolicyFlagsThroughGetSection() {
        S3PluginConfig initialConfig = S3PluginConfig.builder()
                .secretKey("existing-secret")
                .build();
        initialConfig.normalize();
        S3PluginConfig persistedConfig = S3PluginConfig.builder()
                .enabled(true)
                .endpoint("https://storage.example.com")
                .region("eu-west-1")
                .accessKey("minio")
                .secretKey("existing-secret")
                .bucket("files")
                .rootPrefix("AI")
                .timeoutMs(45_000)
                .allowInsecureTls(true)
                .maxDownloadBytes(4096)
                .maxInlineTextChars(2048)
                .autoCreateBucket(true)
                .allowWrite(true)
                .allowDelete(false)
                .allowMove(true)
                .allowCopy(false)
                .build();
        persistedConfig.normalize();
        when(configService.getConfig()).thenReturn(initialConfig, persistedConfig);

        Map<String, Object> values = new LinkedHashMap<>();
        values.put("enabled", true);
        values.put("endpoint", "https://storage.example.com");
        values.put("region", "eu-west-1");
        values.put("accessKey", "minio");
        values.put("secretKey", "");
        values.put("bucket", "files");
        values.put("rootPrefix", "/AI");
        values.put("timeoutMs", 45_000);
        values.put("allowInsecureTls", true);
        values.put("maxDownloadBytes", 4096);
        values.put("maxInlineTextChars", 2048);
        values.put("autoCreateBucket", true);
        values.put("allowWrite", true);
        values.put("allowDelete", false);
        values.put("allowMove", true);
        values.put("allowCopy", false);

        PluginSettingsSection section = contributor.saveSection("main", values);

        ArgumentCaptor<S3PluginConfig> captor = ArgumentCaptor.forClass(S3PluginConfig.class);
        verify(configService).save(captor.capture());
        S3PluginConfig saved = captor.getValue();
        assertEquals("existing-secret", saved.getSecretKey());
        assertTrue(saved.getAutoCreateBucket());
        assertTrue(saved.getAllowWrite());
        assertFalse(saved.getAllowDelete());
        assertTrue(saved.getAllowMove());
        assertFalse(saved.getAllowCopy());

        assertEquals("", section.getValues().get("secretKey"));
        assertEquals("/AI", section.getValues().get("rootPrefix"));
        assertTrue((Boolean) section.getValues().get("autoCreateBucket"));
        assertTrue((Boolean) section.getValues().get("allowWrite"));
        assertFalse((Boolean) section.getValues().get("allowDelete"));
        assertTrue((Boolean) section.getValues().get("allowMove"));
        assertFalse((Boolean) section.getValues().get("allowCopy"));
    }

    @Test
    void shouldReturnOkWhenConnectionTestSucceeds() {
        config.setEndpoint("https://storage.example.com");
        config.setAccessKey("minio");
        config.setSecretKey("secret");
        config.setBucket("files");
        when(client.listDirectory("")).thenReturn(java.util.List.of());

        PluginActionResult result = contributor.executeAction("main", "test-connection", Map.of());

        assertEquals("ok", result.getStatus());
        assertEquals("Connected to S3 bucket files. Root returned 0 item(s).", result.getMessage());
    }

    @Test
    void shouldReturnErrorWhenRequiredSettingsAreMissing() {
        PluginActionResult result = contributor.executeAction("main", "test-connection", Map.of());

        assertEquals("error", result.getStatus());
        assertEquals("S3 endpoint, access key, secret key, and bucket must be configured.", result.getMessage());
    }

    @Test
    void shouldReturnErrorWhenConnectionTestFails() {
        config.setEndpoint("https://storage.example.com");
        config.setAccessKey("minio");
        config.setSecretKey("secret");
        config.setBucket("files");
        doThrow(new S3StorageException("S3 object listing failed: AccessDenied"))
                .when(client).ensureBucketReady();

        PluginActionResult result = contributor.executeAction("main", "test-connection", Map.of());

        assertEquals("error", result.getStatus());
        assertEquals("Connection failed: S3 object listing failed: AccessDenied", result.getMessage());
    }
}
