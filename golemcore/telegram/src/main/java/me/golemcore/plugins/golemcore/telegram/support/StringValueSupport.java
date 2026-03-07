package me.golemcore.plugins.golemcore.telegram.support;

public final class StringValueSupport {

    private StringValueSupport() {
    }

    public static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
