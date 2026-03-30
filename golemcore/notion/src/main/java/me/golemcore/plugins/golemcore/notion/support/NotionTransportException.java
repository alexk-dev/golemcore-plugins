package me.golemcore.plugins.golemcore.notion.support;

public class NotionTransportException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public NotionTransportException(String message, Throwable cause) {
        super(message, cause);
    }
}
