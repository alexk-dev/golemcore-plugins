package me.golemcore.plugins.golemcore.s3.support;

public class S3StorageException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public S3StorageException(String message, Throwable cause) {
        super(message, cause);
    }

    public S3StorageException(String message) {
        super(message);
    }
}
