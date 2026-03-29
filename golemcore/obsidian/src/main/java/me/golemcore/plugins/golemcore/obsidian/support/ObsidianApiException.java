package me.golemcore.plugins.golemcore.obsidian.support;

public class ObsidianApiException extends RuntimeException {

    private final int statusCode;

    public ObsidianApiException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
