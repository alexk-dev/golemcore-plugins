package me.golemcore.plugins.golemcore.pinchtab;

import java.io.IOException;

public class PinchTabRequestException extends IOException {

    private static final long serialVersionUID = 1L;

    private final int statusCode;

    public PinchTabRequestException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
