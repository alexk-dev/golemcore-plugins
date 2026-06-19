package me.golemcore.plugins.golemcore.nextcloud.support;

public class NextcloudTransportException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public NextcloudTransportException(String message, Throwable cause) {
        super(message, cause);
    }
}
