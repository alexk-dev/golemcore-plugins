package me.golemcore.plugins.golemcore.supabase.support;

public class SupabaseTransportException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public SupabaseTransportException(String message, Throwable cause) {
        super(message, cause);
    }
}
