package me.golemcore.plugins.golemcore.notion.support;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;

public class NotionPathValidator {

    public String normalizeNotePath(String path) {
        if (path == null || path.isBlank()) {
            return "";
        }
        Deque<String> normalized = new ArrayDeque<>();
        Arrays.stream(path.trim().split("/"))
                .map(String::trim)
                .forEach(segment -> consumeSegment(normalized, segment));
        return String.join("/", normalized);
    }

    public String parentPath(String normalizedPath) {
        List<String> segments = segments(normalizedPath);
        if (segments.isEmpty()) {
            return "";
        }
        if (segments.size() == 1) {
            return "";
        }
        return String.join("/", segments.subList(0, segments.size() - 1));
    }

    public String leafName(String normalizedPath) {
        List<String> segments = segments(normalizedPath);
        if (segments.isEmpty()) {
            throw new IllegalArgumentException("Path must point to a page");
        }
        return segments.getLast();
    }

    public String resolveSiblingPath(String normalizedPath, String newName) {
        if (newName == null || newName.isBlank()) {
            throw new IllegalArgumentException("newName is required");
        }
        String trimmed = newName.trim();
        if (trimmed.contains("/")) {
            throw new IllegalArgumentException("newName must not contain path separators");
        }
        String parentPath = parentPath(normalizedPath);
        return parentPath.isBlank() ? trimmed : parentPath + "/" + trimmed;
    }

    private void consumeSegment(Deque<String> normalized, String segment) {
        if (segment.isEmpty() || ".".equals(segment)) {
            return;
        }
        if ("..".equals(segment)) {
            if (normalized.isEmpty()) {
                throw new IllegalArgumentException("Path traversal is not allowed");
            }
            normalized.removeLast();
            return;
        }
        normalized.addLast(segment);
    }

    private List<String> segments(String normalizedPath) {
        if (normalizedPath == null || normalizedPath.isBlank()) {
            return List.of();
        }
        return List.of(normalizedPath.split("/"));
    }
}
