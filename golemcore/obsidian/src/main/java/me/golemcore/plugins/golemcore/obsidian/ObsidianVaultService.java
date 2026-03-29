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
            data.put("files", entries);
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
        return writeNote(path, content, "Created", false);
    }

    public ToolResult updateNote(String path, String content) {
        return writeNote(path, content, "Updated", true);
    }

    public ToolResult deleteNote(String path) {
        if (!Boolean.TRUE.equals(configService.getConfig().getAllowDelete())) {
            return ToolResult.failure(ToolFailureKind.POLICY_DENIED,
                    "Obsidian delete is disabled in plugin settings");
        }

        try {
            String normalizedPath = pathValidator.normalizeNotePath(path);
            requireExistingNote(normalizedPath);
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

    private ToolResult writeNote(String path, String content, String verb, boolean requireExisting) {
        if (!Boolean.TRUE.equals(configService.getConfig().getAllowWrite())) {
            return ToolResult.failure(ToolFailureKind.POLICY_DENIED,
                    "Obsidian write is disabled in plugin settings");
        }

        try {
            String normalizedPath = pathValidator.normalizeNotePath(path);
            requireContent(content);
            if (requireExisting) {
                requireExistingNote(normalizedPath);
            } else {
                ensureNoteAbsent(normalizedPath);
            }
            apiClient.writeNote(normalizedPath, content);
            return ToolResult.success(verb + " note " + normalizedPath, Map.of("path", normalizedPath));
        } catch (IllegalArgumentException | ObsidianApiException | ObsidianTransportException ex) {
            return executionFailure(ex.getMessage());
        }
    }

    private ToolResult relocateNote(String sourcePath, String targetPath, String verb) {
        try {
            if (sourcePath.equals(targetPath)) {
                throw new IllegalArgumentException("Source and target paths must differ");
            }
            String content = requireExistingNote(sourcePath);
            ensureNoteAbsent(targetPath);
            apiClient.writeNote(targetPath, content);
            try {
                apiClient.deleteNote(sourcePath);
            } catch (ObsidianApiException | ObsidianTransportException ex) {
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("path", sourcePath);
                data.put("target_path", targetPath);
                return ToolResult.builder()
                        .success(false)
                        .failureKind(ToolFailureKind.EXECUTION_FAILED)
                        .error("Obsidian " + verb.toLowerCase() + " partially failed after writing "
                                + targetPath + "; both source and target may now exist: " + ex.getMessage())
                        .data(data)
                        .build();
            }

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("path", sourcePath);
            data.put("target_path", targetPath);
            return ToolResult.success(verb + " note from " + sourcePath + " to " + targetPath, data);
        } catch (IllegalArgumentException | ObsidianApiException | ObsidianTransportException ex) {
            return executionFailure(ex.getMessage());
        }
    }

    private String requireExistingNote(String path) {
        try {
            return apiClient.readNote(path);
        } catch (ObsidianApiException ex) {
            if (ex.getStatusCode() == 404) {
                throw new IllegalArgumentException("Note does not exist: " + path);
            }
            throw ex;
        }
    }

    private void ensureNoteAbsent(String path) {
        if (noteExists(path)) {
            throw new IllegalArgumentException("Target note already exists: " + path);
        }
    }

    private boolean noteExists(String path) {
        try {
            apiClient.readNote(path);
            return true;
        } catch (ObsidianApiException ex) {
            if (ex.getStatusCode() == 404) {
                return false;
            }
            throw ex;
        }
    }

    private void requireContent(String content) {
        if (content == null) {
            throw new IllegalArgumentException("Content is required");
        }
    }

    private ToolResult executionFailure(String message) {
        return ToolResult.failure(ToolFailureKind.EXECUTION_FAILED, message);
    }

    private String displayPath(String normalizedPath) {
        return normalizedPath.isBlank() ? "/" : normalizedPath;
    }
}
