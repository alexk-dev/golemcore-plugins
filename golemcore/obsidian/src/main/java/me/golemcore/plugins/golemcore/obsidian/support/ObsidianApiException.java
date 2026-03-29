package me.golemcore.plugins.golemcore.obsidian.support;

public class ObsidianApiException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final int statusCode;

    public ObsidianApiException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
