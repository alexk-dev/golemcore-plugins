package me.golemcore.plugins.golemcore.obsidian;

import me.golemcore.plugin.api.extension.model.ToolFailureKind;
import me.golemcore.plugin.api.extension.model.ToolResult;
import me.golemcore.plugins.golemcore.obsidian.model.ObsidianSearchResult;
import me.golemcore.plugins.golemcore.obsidian.support.ObsidianApiClient;
import me.golemcore.plugins.golemcore.obsidian.support.ObsidianApiException;
import me.golemcore.plugins.golemcore.obsidian.support.ObsidianPathValidator;
import me.golemcore.plugins.golemcore.obsidian.support.ObsidianTransportException;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ObsidianVaultService {

    private final ObsidianApiClient apiClient;
    private final ObsidianPluginConfigService configService;
    private final ObsidianPathValidator pathValidator = new ObsidianPathValidator();

    public ObsidianVaultService(ObsidianApiClient apiClient, ObsidianPluginConfigService configService) {
        this.apiClient = apiClient;
        this.configService = configService;
    }

    public ToolResult listDirectory(String path) {
        try {
            String normalizedPath = pathValidator.normalizeDirectoryPath(path);
            List<String> entries = apiClient.listDirectory(normalizedPath);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("path", normalizedPath);
            data.put("entries", entries);
            return ToolResult.success(
                    "Listed " + entries.size() + " item(s) in " + displayPath(normalizedPath),
                    data);
        } catch (IllegalArgumentException | ObsidianApiException | ObsidianTransportException ex) {
            return executionFailure(ex.getMessage());
        }
    }

    public ToolResult readNote(String path) {
        try {
            String normalizedPath = pathValidator.normalizeNotePath(path);
            String content = apiClient.readNote(normalizedPath);
            int originalLength = content.length();
            int maxReadChars = configService.getConfig().getMaxReadChars();
            boolean truncated = originalLength > maxReadChars;
            String visibleContent = truncated ? content.substring(0, maxReadChars) : content;

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("path", normalizedPath);
            data.put("content", visibleContent);
            data.put("truncated", truncated);
            if (truncated) {
                data.put("originalLength", originalLength);
            }
            return ToolResult.success(visibleContent, data);
        } catch (IllegalArgumentException | ObsidianApiException | ObsidianTransportException ex) {
            return executionFailure(ex.getMessage());
        }
    }

    public ToolResult searchNotes(String query, Integer contextLength) {
        try {
            if (query == null || query.isBlank()) {
                throw new IllegalArgumentException("Search query is required");
            }
            int requestedContextLength = contextLength != null ? contextLength : 0;
            List<ObsidianSearchResult> results = apiClient.simpleSearch(query.trim(), requestedContextLength);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("query", query.trim());
            data.put("count", results.size());
            data.put("results", results);
            return ToolResult.success("Found " + results.size() + " note(s) for query: " + query.trim(), data);
        } catch (IllegalArgumentException | ObsidianApiException | ObsidianTransportException ex) {
            return executionFailure(ex.getMessage());
        }
    }

    public ToolResult createNote(String path, String content) {
        return writeNote(path, content, "Created");
    }

    public ToolResult updateNote(String path, String content) {
        return writeNote(path, content, "Updated");
    }

    public ToolResult deleteNote(String path) {
        if (!Boolean.TRUE.equals(configService.getConfig().getAllowDelete())) {
            return ToolResult.failure(ToolFailureKind.POLICY_DENIED,
                    "Obsidian delete is disabled in plugin settings");
        }

        try {
            String normalizedPath = pathValidator.normalizeNotePath(path);
            apiClient.deleteNote(normalizedPath);
            return ToolResult.success("Deleted note " + normalizedPath, Map.of("path", normalizedPath));
        } catch (IllegalArgumentException | ObsidianApiException | ObsidianTransportException ex) {
            return executionFailure(ex.getMessage());
        }
    }

    public ToolResult moveNote(String path, String targetPath) {
        if (!Boolean.TRUE.equals(configService.getConfig().getAllowMove())) {
            return ToolResult.failure(ToolFailureKind.POLICY_DENIED,
                    "Obsidian move is disabled in plugin settings");
        }

        try {
            String normalizedPath = pathValidator.normalizeNotePath(path);
            String normalizedTargetPath = pathValidator.normalizeNotePath(targetPath);
            return relocateNote(normalizedPath, normalizedTargetPath, "Moved");
        } catch (IllegalArgumentException ex) {
            return executionFailure(ex.getMessage());
        }
    }

    public ToolResult renameNote(String path, String newName) {
        if (!Boolean.TRUE.equals(configService.getConfig().getAllowRename())) {
            return ToolResult.failure(ToolFailureKind.POLICY_DENIED,
                    "Obsidian rename is disabled in plugin settings");
        }

        try {
            String normalizedPath = pathValidator.normalizeNotePath(path);
            String targetPath = pathValidator.resolveSiblingNotePath(normalizedPath, newName);
            return relocateNote(normalizedPath, targetPath, "Renamed");
        } catch (IllegalArgumentException ex) {
            return executionFailure(ex.getMessage());
        }
    }

    private ToolResult writeNote(String path, String content, String verb) {
        if (!Boolean.TRUE.equals(configService.getConfig().getAllowWrite())) {
            return ToolResult.failure(ToolFailureKind.POLICY_DENIED,
                    "Obsidian write is disabled in plugin settings");
        }

        try {
            String normalizedPath = pathValidator.normalizeNotePath(path);
            apiClient.writeNote(normalizedPath, content != null ? content : "");
            return ToolResult.success(verb + " note " + normalizedPath, Map.of("path", normalizedPath));
        } catch (IllegalArgumentException | ObsidianApiException | ObsidianTransportException ex) {
            return executionFailure(ex.getMessage());
        }
    }

    private ToolResult relocateNote(String sourcePath, String targetPath, String verb) {
        try {
            String content = apiClient.readNote(sourcePath);
            apiClient.writeNote(targetPath, content);
            try {
                apiClient.deleteNote(sourcePath);
            } catch (ObsidianApiException | ObsidianTransportException ex) {
                return ToolResult.failure(ToolFailureKind.EXECUTION_FAILED,
                        "Obsidian " + verb.toLowerCase() + " partially failed after writing "
                                + targetPath + "; both source and target may now exist: " + ex.getMessage());
            }

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("sourcePath", sourcePath);
            data.put("targetPath", targetPath);
            return ToolResult.success(verb + " note from " + sourcePath + " to " + targetPath, data);
        } catch (IllegalArgumentException | ObsidianApiException | ObsidianTransportException ex) {
            return executionFailure(ex.getMessage());
        }
    }

    private ToolResult executionFailure(String message) {
        return ToolResult.failure(ToolFailureKind.EXECUTION_FAILED, message);
    }

    private String displayPath(String normalizedPath) {
        return normalizedPath.isBlank() ? "/" : normalizedPath;
    }
}
