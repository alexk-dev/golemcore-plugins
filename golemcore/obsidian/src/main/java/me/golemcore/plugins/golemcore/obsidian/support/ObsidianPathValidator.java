package me.golemcore.plugins.golemcore.obsidian.support;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;

public final class ObsidianPathValidator {

    public String normalizeDirectoryPath(String path) {
        return normalizeRelativePath(path, true);
    }

    public String normalizeNotePath(String path) {
        String normalized = normalizeRelativePath(path, false);
        if (!normalized.toLowerCase(Locale.ROOT).endsWith(".md")) {
            throw new IllegalArgumentException("Obsidian note paths must end with .md");
        }
        return normalized;
    }

    public String normalizeNewName(String newName) {
        if (newName == null || newName.isBlank()) {
            throw new IllegalArgumentException("newName is required");
        }
        String normalized = newName.trim();
        if (normalized.contains("/") || normalized.contains("\\")) {
            throw new IllegalArgumentException("newName must not contain path separators");
        }
        if (!normalized.toLowerCase(Locale.ROOT).endsWith(".md")) {
            throw new IllegalArgumentException("newName must end with .md");
        }
        return normalized;
    }

    public String resolveSiblingNotePath(String sourcePath, String newName) {
        String normalizedSourcePath = normalizeNotePath(sourcePath);
        String normalizedNewName = normalizeNewName(newName);
        int separatorIndex = normalizedSourcePath.lastIndexOf('/');
        if (separatorIndex < 0) {
            return normalizedNewName;
        }
        return normalizedSourcePath.substring(0, separatorIndex + 1) + normalizedNewName;
    }

    private String normalizeRelativePath(String path, boolean allowBlank) {
        if (path == null || path.isBlank()) {
            if (allowBlank) {
                return "";
            }
            throw new IllegalArgumentException("Path is required");
        }

        String candidate = path.trim().replace('\\', '/');
        Deque<String> segments = new ArrayDeque<>();
        for (String rawSegment : candidate.split("/")) {
            String segment = rawSegment.trim();
            if (segment.isEmpty() || ".".equals(segment)) {
                continue;
            }
            if ("..".equals(segment)) {
                if (segments.isEmpty()) {
                    throw new IllegalArgumentException("Path must stay within the vault root");
                }
                segments.removeLast();
                continue;
            }
            segments.addLast(segment);
        }

        String normalized = String.join("/", segments);
        if (!allowBlank && normalized.isBlank()) {
            throw new IllegalArgumentException("Path is required");
        }
        return normalized;
    }
}
