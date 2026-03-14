package me.golemcore.plugins.golemcore.pinchtab;

import java.io.IOException;

public class PinchTabRequestException extends IOException {

    private final int statusCode;

    public PinchTabRequestException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
