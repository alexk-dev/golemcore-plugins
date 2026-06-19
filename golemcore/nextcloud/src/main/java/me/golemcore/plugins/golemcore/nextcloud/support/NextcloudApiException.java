package me.golemcore.plugins.golemcore.nextcloud.support;

public class NextcloudApiException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final int statusCode;

    public NextcloudApiException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
