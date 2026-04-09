package me.golemcore.plugins.golemcore.s3.support;

import java.util.Locale;

public final class S3MimeTypes {

    private S3MimeTypes() {
    }

    public static String detect(String path) {
        String lowerPath = path == null ? "" : path.toLowerCase(Locale.ROOT);
        if (lowerPath.endsWith(".txt") || lowerPath.endsWith(".log") || lowerPath.endsWith(".ini")
                || lowerPath.endsWith(".toml") || lowerPath.endsWith(".properties")) {
            return "text/plain";
        }
        if (lowerPath.endsWith(".md") || lowerPath.endsWith(".markdown")) {
            return "text/markdown";
        }
        if (lowerPath.endsWith(".json")) {
            return "application/json";
        }
        if (lowerPath.endsWith(".xml")) {
            return "application/xml";
        }
        if (lowerPath.endsWith(".yaml") || lowerPath.endsWith(".yml")) {
            return "application/yaml";
        }
        if (lowerPath.endsWith(".csv")) {
            return "text/csv";
        }
        if (lowerPath.endsWith(".html") || lowerPath.endsWith(".htm")) {
            return "text/html";
        }
        if (lowerPath.endsWith(".css")) {
            return "text/css";
        }
        if (lowerPath.endsWith(".js")) {
            return "application/javascript";
        }
        if (lowerPath.endsWith(".ts")) {
            return "text/typescript";
        }
        if (lowerPath.endsWith(".svg")) {
            return "image/svg+xml";
        }
        if (lowerPath.endsWith(".png")) {
            return "image/png";
        }
        if (lowerPath.endsWith(".jpg") || lowerPath.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (lowerPath.endsWith(".gif")) {
            return "image/gif";
        }
        if (lowerPath.endsWith(".webp")) {
            return "image/webp";
        }
        if (lowerPath.endsWith(".pdf")) {
            return "application/pdf";
        }
        if (lowerPath.endsWith(".zip")) {
            return "application/zip";
        }
        return "application/octet-stream";
    }
}
