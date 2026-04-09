package me.golemcore.plugins.golemcore.s3.support;

import java.util.ArrayDeque;
import java.util.Deque;

public final class S3PathValidator {

    public String normalizeOptionalPath(String path) {
        if (path == null || path.isBlank()) {
            return "";
        }
        String candidate = path.trim().replace('\\', '/');
        while (candidate.startsWith("/")) {
            candidate = candidate.substring(1);
        }
        while (candidate.endsWith("/")) {
            candidate = candidate.substring(0, candidate.length() - 1);
        }

        Deque<String> segments = new ArrayDeque<>();
        for (String rawSegment : candidate.split("/")) {
            String segment = rawSegment.trim();
            if (segment.isEmpty() || ".".equals(segment)) {
                continue;
            }
            if ("..".equals(segment)) {
                if (segments.isEmpty()) {
                    throw new IllegalArgumentException("Path must stay within the configured S3 root prefix");
                }
                segments.removeLast();
                continue;
            }
            segments.addLast(segment);
        }
        return String.join("/", segments);
    }

    public String normalizeRequiredPath(String path) {
        String normalized = normalizeOptionalPath(path);
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("Path is required");
        }
        return normalized;
    }

    public String parentPath(String normalizedPath) {
        String normalized = normalizeOptionalPath(normalizedPath);
        int separatorIndex = normalized.lastIndexOf('/');
        return separatorIndex >= 0 ? normalized.substring(0, separatorIndex) : "";
    }
}
