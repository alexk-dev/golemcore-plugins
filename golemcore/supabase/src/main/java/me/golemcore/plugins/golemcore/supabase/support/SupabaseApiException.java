package me.golemcore.plugins.golemcore.supabase.support;

public class SupabaseApiException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final int statusCode;

    public SupabaseApiException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
