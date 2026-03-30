package me.golemcore.plugins.golemcore.notion.support;

public class NotionApiException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final int statusCode;

    public NotionApiException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
