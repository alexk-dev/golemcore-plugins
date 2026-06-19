package me.golemcore.plugins.golemcore.s3;

import lombok.RequiredArgsConstructor;
import me.golemcore.plugin.api.extension.spi.PluginActionResult;
import me.golemcore.plugin.api.extension.spi.PluginSettingsAction;
import me.golemcore.plugin.api.extension.spi.PluginSettingsCatalogItem;
import me.golemcore.plugin.api.extension.spi.PluginSettingsContributor;
import me.golemcore.plugin.api.extension.spi.PluginSettingsField;
import me.golemcore.plugin.api.extension.spi.PluginSettingsSection;
import me.golemcore.plugins.golemcore.s3.support.S3MinioClient;
import me.golemcore.plugins.golemcore.s3.support.S3StorageException;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class S3PluginSettingsContributor implements PluginSettingsContributor {

    private static final String SECTION_KEY = "main";
    private static final String ACTION_TEST_CONNECTION = "test-connection";

    private final S3PluginConfigService configService;
    private final S3MinioClient client;

    @Override
    public String getPluginId() {
        return S3PluginConfigService.PLUGIN_ID;
    }

    @Override
    public List<PluginSettingsCatalogItem> getCatalogItems() {
        return List.of(PluginSettingsCatalogItem.builder()
                .pluginId(S3PluginConfigService.PLUGIN_ID)
                .pluginName("s3")
                .provider("golemcore")
                .sectionKey(SECTION_KEY)
                .title("S3")
                .description("S3-compatible object storage connection, root sandbox, and file policy.")
                .blockKey("tools")
                .blockTitle("Tools")
                .blockDescription("Tool-specific runtime behavior and integrations")
                .order(39)
                .build());
    }

    @Override
    public PluginSettingsSection getSection(String sectionKey) {
        requireSection(sectionKey);
        S3PluginConfig config = configService.getConfig();
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("enabled", Boolean.TRUE.equals(config.getEnabled()));
        values.put("endpoint", config.getEndpoint());
        values.put("region", config.getRegion());
        values.put("accessKey", config.getAccessKey());
        values.put("secretKey", "");
        values.put("bucket", config.getBucket());
        values.put("rootPrefix", config.getRootPrefix().isBlank() ? "/" : "/" + config.getRootPrefix());
        values.put("timeoutMs", config.getTimeoutMs());
        values.put("allowInsecureTls", Boolean.TRUE.equals(config.getAllowInsecureTls()));
        values.put("maxDownloadBytes", config.getMaxDownloadBytes());
        values.put("maxInlineTextChars", config.getMaxInlineTextChars());
        values.put("autoCreateBucket", Boolean.TRUE.equals(config.getAutoCreateBucket()));
        values.put("allowWrite", Boolean.TRUE.equals(config.getAllowWrite()));
        values.put("allowDelete", Boolean.TRUE.equals(config.getAllowDelete()));
        values.put("allowMove", Boolean.TRUE.equals(config.getAllowMove()));
        values.put("allowCopy", Boolean.TRUE.equals(config.getAllowCopy()));

        return PluginSettingsSection.builder()
                .title("S3")
                .description(
                        "Configure the S3-compatible object storage connection, root prefix sandbox, and conservative file policy.")
                .fields(List.of(
                        booleanField("enabled", "Enable S3", "Allow tools to use the S3 integration."),
                        textField("endpoint", "Endpoint", "S3-compatible HTTPS or HTTP endpoint.",
                                "https://play.min.io"),
                        textField("region", "Region", "Default region passed to the MinIO client.", "us-east-1"),
                        textField("accessKey", "Access Key", "Access key ID used for S3 authentication.",
                                "minioadmin"),
                        PluginSettingsField.builder()
                                .key("secretKey")
                                .type("secret")
                                .label("Secret Key")
                                .description("Leave blank to keep the current secret.")
                                .placeholder("Enter secret key")
                                .build(),
                        textField("bucket", "Bucket", "Bucket used as the tool root namespace.", "my-bucket"),
                        textField("rootPrefix", "Root Prefix", "Sandbox all tool paths under this prefix.", "/AI"),
                        numberField("timeoutMs", "Request Timeout (ms)", "Timeout for MinIO S3 requests.",
                                1000.0, 300000.0, 1000.0),
                        booleanField("allowInsecureTls", "Allow Insecure TLS",
                                "Allow self-signed TLS certificates when connecting to S3."),
                        numberField("maxDownloadBytes", "Max Download Bytes",
                                "Maximum object size that read_file downloads into memory.", 1.0, null, 1024.0),
                        numberField("maxInlineTextChars", "Max Inline Text Chars",
                                "Maximum text characters returned inline before attachment fallback.", 1.0, null, 1.0),
                        booleanField("autoCreateBucket", "Auto Create Bucket",
                                "Create the configured bucket automatically if it does not exist."),
                        booleanField("allowWrite", "Allow Write",
                                "Permit tools to create directories or write objects."),
                        booleanField("allowDelete", "Allow Delete",
                                "Permit tools to delete objects or prefixes."),
                        booleanField("allowMove", "Allow Move",
                                "Permit tools to move objects or prefixes."),
                        booleanField("allowCopy", "Allow Copy",
                                "Permit tools to copy objects or prefixes.")))
                .values(values)
                .actions(List.of(PluginSettingsAction.builder()
                        .actionId(ACTION_TEST_CONNECTION)
                        .label("Test Connection")
                        .variant("secondary")
                        .build()))
                .build();
    }

    @Override
    public PluginSettingsSection saveSection(String sectionKey, Map<String, Object> values) {
        requireSection(sectionKey);
        S3PluginConfig config = configService.getConfig();
        config.setEnabled(readBoolean(values, "enabled", false));
        config.setEndpoint(readString(values, "endpoint", config.getEndpoint()));
        config.setRegion(readString(values, "region", config.getRegion()));
        config.setAccessKey(readString(values, "accessKey", config.getAccessKey()));
        String secretKey = readString(values, "secretKey", null);
        if (secretKey != null && !secretKey.isBlank()) {
            config.setSecretKey(secretKey);
        }
        config.setBucket(readString(values, "bucket", config.getBucket()));
        config.setRootPrefix(readString(values, "rootPrefix", config.getRootPrefix()));
        config.setTimeoutMs(readInteger(values, "timeoutMs", config.getTimeoutMs()));
        config.setAllowInsecureTls(readBoolean(values, "allowInsecureTls", false));
        config.setMaxDownloadBytes(readInteger(values, "maxDownloadBytes", config.getMaxDownloadBytes()));
        config.setMaxInlineTextChars(readInteger(values, "maxInlineTextChars", config.getMaxInlineTextChars()));
        config.setAutoCreateBucket(readBoolean(values, "autoCreateBucket", false));
        config.setAllowWrite(readBoolean(values, "allowWrite", false));
        config.setAllowDelete(readBoolean(values, "allowDelete", false));
        config.setAllowMove(readBoolean(values, "allowMove", false));
        config.setAllowCopy(readBoolean(values, "allowCopy", false));
        configService.save(config);
        return getSection(sectionKey);
    }

    @Override
    public PluginActionResult executeAction(String sectionKey, String actionId, Map<String, Object> payload) {
        requireSection(sectionKey);
        if (!ACTION_TEST_CONNECTION.equals(actionId)) {
            throw new IllegalArgumentException("Unknown S3 action: " + actionId);
        }
        return testConnection();
    }

    private PluginActionResult testConnection() {
        S3PluginConfig config = configService.getConfig();
        if (!hasText(config.getEndpoint()) || !hasText(config.getAccessKey()) || !hasText(config.getSecretKey())
                || !hasText(config.getBucket())) {
            return PluginActionResult.builder()
                    .status("error")
                    .message("S3 endpoint, access key, secret key, and bucket must be configured.")
                    .build();
        }
        try {
            client.ensureBucketReady();
            int count = client.listDirectory("").size();
            return PluginActionResult.builder()
                    .status("ok")
                    .message("Connected to S3 bucket " + config.getBucket() + ". Root returned " + count
                            + " item(s).")
                    .build();
        } catch (IllegalArgumentException | IllegalStateException | S3StorageException ex) {
            return PluginActionResult.builder()
                    .status("error")
                    .message("Connection failed: " + ex.getMessage())
                    .build();
        }
    }

    private void requireSection(String sectionKey) {
        if (!SECTION_KEY.equals(sectionKey)) {
            throw new IllegalArgumentException("Unknown S3 settings section: " + sectionKey);
        }
    }

    private PluginSettingsField booleanField(String key, String label, String description) {
        return PluginSettingsField.builder()
                .key(key)
                .type("boolean")
                .label(label)
                .description(description)
                .build();
    }

    private PluginSettingsField textField(String key, String label, String description, String placeholder) {
        return PluginSettingsField.builder()
                .key(key)
                .type("text")
                .label(label)
                .description(description)
                .placeholder(placeholder)
                .build();
    }

    private PluginSettingsField numberField(
            String key,
            String label,
            String description,
            Double min,
            Double max,
            Double step) {
        return PluginSettingsField.builder()
                .key(key)
                .type("number")
                .label(label)
                .description(description)
                .min(min)
                .max(max)
                .step(step)
                .build();
    }

    private boolean readBoolean(Map<String, Object> values, String key, boolean defaultValue) {
        Object value = values.get(key);
        return value instanceof Boolean bool ? bool : defaultValue;
    }

    private int readInteger(Map<String, Object> values, String key, int defaultValue) {
        Object value = values.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private String readString(Map<String, Object> values, String key, String defaultValue) {
        Object value = values.get(key);
        if (value instanceof String text) {
            return text;
        }
        return defaultValue;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
