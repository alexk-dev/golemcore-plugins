package me.golemcore.plugins.golemcore.slack.support;

public final class SlackTextSupport {

    private SlackTextSupport() {
    }

    public static String escapeMrkdwn(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    public static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value == null ? "" : value;
        }
        return value.substring(0, maxLength - 3) + "...";
    }
}
