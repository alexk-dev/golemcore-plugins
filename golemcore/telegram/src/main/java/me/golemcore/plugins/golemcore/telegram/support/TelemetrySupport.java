package me.golemcore.plugins.golemcore.telegram.support;

public final class TelemetrySupport {

    private TelemetrySupport() {
    }

    public static String shortHash(String value) {
        if (StringValueSupport.isBlank(value)) {
            return "";
        }
        return Integer.toHexString(value.trim().hashCode());
    }
}
