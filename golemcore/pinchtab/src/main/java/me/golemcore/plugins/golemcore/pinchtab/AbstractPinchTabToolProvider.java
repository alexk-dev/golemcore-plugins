package me.golemcore.plugins.golemcore.pinchtab;

import me.golemcore.plugin.api.extension.model.ToolFailureKind;
import me.golemcore.plugin.api.extension.model.ToolResult;
import me.golemcore.plugin.api.extension.spi.ToolProvider;

import java.util.Map;

public abstract class AbstractPinchTabToolProvider implements ToolProvider {

    protected static final String TYPE = "type";
    protected static final String TYPE_OBJECT = "object";
    protected static final String TYPE_STRING = "string";
    protected static final String TYPE_INTEGER = "integer";
    protected static final String TYPE_NUMBER = "number";
    protected static final String TYPE_BOOLEAN = "boolean";
    protected static final String TYPE_ARRAY = "array";
    protected static final String PROPERTIES = "properties";
    protected static final String REQUIRED = "required";
    protected static final String ITEMS = "items";
    protected static final int MAX_OUTPUT_CHARS = 12_000;

    private final PinchTabHttpClient client;

    protected AbstractPinchTabToolProvider(PinchTabHttpClient client) {
        this.client = client;
    }

    @Override
    public boolean isEnabled() {
        return client.isEnabled();
    }

    protected PinchTabHttpClient getClient() {
        return client;
    }

    protected PinchTabPluginConfig getConfig() {
        return client.getConfig();
    }

    protected ToolResult disabledResult() {
        return ToolResult.failure(ToolFailureKind.EXECUTION_FAILED,
                "PinchTab is disabled. Enable the plugin in settings first.");
    }

    protected ToolResult failureResult(String operation, Exception ex) {
        return ToolResult.failure(ToolFailureKind.EXECUTION_FAILED,
                "PinchTab " + operation + " failed: " + ex.getMessage());
    }

    protected String resolveInstanceId(Map<String, Object> parameters) {
        String instanceId = readString(parameters.get("instance_id"));
        if (hasText(instanceId)) {
            return instanceId.trim();
        }
        return getConfig().getDefaultInstanceId();
    }

    protected String readString(Object value) {
        return value instanceof String text ? text : null;
    }

    protected Integer readInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    protected Integer readInteger(Object value, int defaultValue, int minValue, int maxValue) {
        Integer parsed = readInteger(value);
        int resolved = parsed != null ? parsed : defaultValue;
        if (resolved < minValue) {
            return minValue;
        }
        if (resolved > maxValue) {
            return maxValue;
        }
        return resolved;
    }

    protected Double readDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Double.parseDouble(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    protected boolean readBoolean(Object value, boolean defaultValue) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String text && !text.isBlank()) {
            return Boolean.parseBoolean(text);
        }
        return defaultValue;
    }

    protected boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    protected String truncate(String value) {
        if (!hasText(value) || value.length() <= MAX_OUTPUT_CHARS) {
            return value;
        }
        return value.substring(0, MAX_OUTPUT_CHARS) + "\n\n[OUTPUT TRUNCATED]";
    }
}
