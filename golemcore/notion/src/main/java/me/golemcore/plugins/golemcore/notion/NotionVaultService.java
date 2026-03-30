package me.golemcore.plugins.golemcore.notion;

import me.golemcore.plugin.api.extension.model.ToolFailureKind;
import me.golemcore.plugin.api.extension.model.ToolResult;
import me.golemcore.plugins.golemcore.notion.support.NotionApiClient;
import me.golemcore.plugins.golemcore.notion.support.NotionApiException;
import me.golemcore.plugins.golemcore.notion.support.NotionLocalIndexService;
import me.golemcore.plugins.golemcore.notion.support.NotionPageSummary;
import me.golemcore.plugins.golemcore.notion.support.NotionPathValidator;
import me.golemcore.plugins.golemcore.notion.support.NotionSearchHit;
import me.golemcore.plugins.golemcore.notion.support.NotionTransportException;
import me.golemcore.plugins.golemcore.notion.support.NotionRagSyncService;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class NotionVaultService {

    private final NotionApiClient apiClient;
    private final NotionPluginConfigService configService;
    private final NotionLocalIndexService localIndexService;
    private final NotionRagSyncService ragSyncService;
    private final NotionPathValidator pathValidator = new NotionPathValidator();

    public NotionVaultService(
            NotionApiClient apiClient,
            NotionPluginConfigService configService,
            NotionLocalIndexService localIndexService,
            NotionRagSyncService ragSyncService) {
        this.apiClient = apiClient;
        this.configService = configService;
        this.localIndexService = localIndexService;
        this.ragSyncService = ragSyncService;
    }

    public ToolResult listDirectory(String path) {
        try {
            String normalizedPath = pathValidator.normalizeNotePath(path);
            ResolvedPage page = resolveExistingPage(normalizedPath);
            List<String> entries = apiClient.listChildPages(page.pageId()).stream()
                    .map(NotionPageSummary::title)
                    .toList();
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("path", normalizedPath);
            data.put("entries", entries);
            data.put("files", entries);
            return ToolResult.success(
                    "Listed " + entries.size() + " item(s) in " + displayPath(normalizedPath),
                    data);
        } catch (IllegalArgumentException | NotionApiException | NotionTransportException ex) {
            return executionFailure(ex.getMessage());
        }
    }

    public ToolResult readNote(String path) {
        try {
            String normalizedPath = pathValidator.normalizeNotePath(path);
            ResolvedPage page = resolveExistingPage(normalizedPath);
            String content = apiClient.retrievePageMarkdown(page.pageId());
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
        } catch (IllegalArgumentException | NotionApiException | NotionTransportException ex) {
            return executionFailure(ex.getMessage());
        }
    }

    public ToolResult searchNotes(String query, String path, Integer limit) {
        if (!Boolean.TRUE.equals(configService.getConfig().getLocalIndexEnabled())) {
            return ToolResult.failure(ToolFailureKind.POLICY_DENIED,
                    "Notion local index is disabled in plugin settings");
        }
        try {
            String normalizedPath = pathValidator.normalizeNotePath(path);
            List<NotionSearchHit> hits = localIndexService.search(query, normalizedPath, limit != null ? limit : 5);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("query", query != null ? query : "");
            data.put("path", normalizedPath);
            data.put("count", hits.size());
            data.put("results", hits.stream().map(this::toSearchResult).toList());
            return ToolResult.success("Found " + hits.size() + " matching note chunk(s)", data);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return executionFailure(ex.getMessage());
        }
    }

    public ToolResult createNote(String path, String content) {
        if (!Boolean.TRUE.equals(configService.getConfig().getAllowWrite())) {
            return ToolResult.failure(ToolFailureKind.POLICY_DENIED, "Notion write is disabled in plugin settings");
        }
        try {
            String normalizedPath = pathValidator.normalizeNotePath(path);
            requireNonRootPath(normalizedPath, "create");
            requireContent(content);
            String parentPath = pathValidator.parentPath(normalizedPath);
            String title = pathValidator.leafName(normalizedPath);
            ResolvedPage parentPage = resolveExistingPage(parentPath);
            ensureChildAbsent(parentPage.pageId(), title);
            NotionPageSummary created = apiClient.createChildPage(parentPage.pageId(), title, content);
            refreshIndexesAfterMutation(
                    () -> ragSyncService.upsertDocument(
                            created.id(),
                            normalizedPath,
                            title,
                            content,
                            created.url()));
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("path", normalizedPath);
            data.put("page_id", created.id());
            return ToolResult.success("Created note " + normalizedPath, data);
        } catch (IllegalArgumentException | NotionApiException | NotionTransportException ex) {
            return executionFailure(ex.getMessage());
        }
    }

    public ToolResult updateNote(String path, String content) {
        if (!Boolean.TRUE.equals(configService.getConfig().getAllowWrite())) {
            return ToolResult.failure(ToolFailureKind.POLICY_DENIED, "Notion write is disabled in plugin settings");
        }
        try {
            String normalizedPath = pathValidator.normalizeNotePath(path);
            requireContent(content);
            ResolvedPage page = resolveExistingPage(normalizedPath);
            apiClient.updatePageMarkdown(page.pageId(), content);
            refreshIndexesAfterMutation(
                    () -> ragSyncService.upsertDocument(
                            page.pageId(),
                            normalizedPath,
                            page.title(),
                            content,
                            page.url()));
            return ToolResult.success("Updated note " + normalizedPath, Map.of("path", normalizedPath));
        } catch (IllegalArgumentException | NotionApiException | NotionTransportException ex) {
            return executionFailure(ex.getMessage());
        }
    }

    public ToolResult deleteNote(String path) {
        if (!Boolean.TRUE.equals(configService.getConfig().getAllowDelete())) {
            return ToolResult.failure(ToolFailureKind.POLICY_DENIED, "Notion delete is disabled in plugin settings");
        }
        try {
            String normalizedPath = pathValidator.normalizeNotePath(path);
            requireNonRootPath(normalizedPath, "delete");
            ResolvedPage page = resolveExistingPage(normalizedPath);
            apiClient.archivePage(page.pageId());
            refreshIndexesAfterMutation(() -> ragSyncService.deleteDocument(page.pageId()));
            return ToolResult.success("Archived note " + normalizedPath, Map.of("path", normalizedPath));
        } catch (IllegalArgumentException | NotionApiException | NotionTransportException ex) {
            return executionFailure(ex.getMessage());
        }
    }

    public ToolResult moveNote(String path, String targetPath) {
        if (!Boolean.TRUE.equals(configService.getConfig().getAllowMove())) {
            return ToolResult.failure(ToolFailureKind.POLICY_DENIED, "Notion move is disabled in plugin settings");
        }
        try {
            String normalizedPath = pathValidator.normalizeNotePath(path);
            String normalizedTargetPath = pathValidator.normalizeNotePath(targetPath);
            requireNonRootPath(normalizedPath, "move");
            requireNonRootPath(normalizedTargetPath, "move");
            if (normalizedPath.equals(normalizedTargetPath)) {
                throw new IllegalArgumentException("Source and target paths must differ");
            }
            ResolvedPage source = resolveExistingPage(normalizedPath);
            String targetParentPath = pathValidator.parentPath(normalizedTargetPath);
            String targetTitle = pathValidator.leafName(normalizedTargetPath);
            ResolvedPage targetParent = resolveExistingPage(targetParentPath);
            ensureChildAbsent(targetParent.pageId(), targetTitle);
            if (!source.parentPageId().equals(targetParent.pageId())) {
                apiClient.movePage(source.pageId(), targetParent.pageId());
            }
            if (!source.title().equals(targetTitle)) {
                apiClient.renamePage(source.pageId(), targetTitle);
            }
            String content = apiClient.retrievePageMarkdown(source.pageId());
            refreshIndexesAfterMutation(
                    () -> ragSyncService.upsertDocument(
                            source.pageId(),
                            normalizedTargetPath,
                            targetTitle,
                            content,
                            source.url()));
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("path", normalizedPath);
            data.put("target_path", normalizedTargetPath);
            return ToolResult.success("Moved note from " + normalizedPath + " to " + normalizedTargetPath, data);
        } catch (IllegalArgumentException | NotionApiException | NotionTransportException ex) {
            return executionFailure(ex.getMessage());
        }
    }

    public ToolResult renameNote(String path, String newName) {
        if (!Boolean.TRUE.equals(configService.getConfig().getAllowRename())) {
            return ToolResult.failure(ToolFailureKind.POLICY_DENIED, "Notion rename is disabled in plugin settings");
        }
        try {
            String normalizedPath = pathValidator.normalizeNotePath(path);
            requireNonRootPath(normalizedPath, "rename");
            String targetPath = pathValidator.resolveSiblingPath(normalizedPath, newName);
            if (normalizedPath.equals(targetPath)) {
                throw new IllegalArgumentException("Source and target paths must differ");
            }
            ResolvedPage source = resolveExistingPage(normalizedPath);
            ensureChildAbsent(source.parentPageId(), pathValidator.leafName(targetPath));
            apiClient.renamePage(source.pageId(), pathValidator.leafName(targetPath));
            String content = apiClient.retrievePageMarkdown(source.pageId());
            refreshIndexesAfterMutation(
                    () -> ragSyncService.upsertDocument(
                            source.pageId(),
                            targetPath,
                            pathValidator.leafName(targetPath),
                            content,
                            source.url()));
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("path", normalizedPath);
            data.put("target_path", targetPath);
            return ToolResult.success("Renamed note from " + normalizedPath + " to " + targetPath, data);
        } catch (IllegalArgumentException ex) {
            return executionFailure(ex.getMessage());
        }
    }

    private ResolvedPage resolveExistingPage(String normalizedPath) {
        NotionPluginConfig config = configService.getConfig();
        if (config.getRootPageId() == null || config.getRootPageId().isBlank()) {
            throw new IllegalArgumentException("Root page ID is not configured.");
        }
        if (normalizedPath.isBlank()) {
            return new ResolvedPage(
                    config.getRootPageId(),
                    "",
                    "",
                    apiClient.retrievePageTitle(config.getRootPageId()),
                    "");
        }
        String currentPageId = config.getRootPageId();
        String parentPageId = "";
        String title = "";
        String url = "";
        for (String segment : normalizedPath.split("/")) {
            NotionPageSummary match = apiClient.listChildPages(currentPageId).stream()
                    .filter(page -> segment.equals(page.title()))
                    .findFirst()
                    .orElse(null);
            if (match == null) {
                throw new IllegalArgumentException("Page does not exist: " + normalizedPath);
            }
            parentPageId = currentPageId;
            currentPageId = match.id();
            title = match.title();
            url = match.url();
        }
        return new ResolvedPage(currentPageId, parentPageId, normalizedPath, title, url);
    }

    private void ensureChildAbsent(String parentPageId, String title) {
        boolean exists = apiClient.listChildPages(parentPageId).stream()
                .anyMatch(page -> title.equals(page.title()));
        if (exists) {
            throw new IllegalArgumentException("Target page already exists: " + title);
        }
    }

    private void requireContent(String content) {
        if (content == null) {
            throw new IllegalArgumentException("Content is required");
        }
    }

    private void requireNonRootPath(String normalizedPath, String operation) {
        if (normalizedPath.isBlank()) {
            throw new IllegalArgumentException("The configured root page cannot be used for " + operation + ".");
        }
    }

    private ToolResult executionFailure(String message) {
        return ToolResult.failure(ToolFailureKind.EXECUTION_FAILED, message);
    }

    private String displayPath(String normalizedPath) {
        return normalizedPath.isBlank() ? "/" : normalizedPath;
    }

    private Map<String, Object> toSearchResult(NotionSearchHit hit) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("chunk_id", hit.chunkId());
        data.put("page_id", hit.pageId());
        data.put("path", hit.path());
        data.put("title", hit.title());
        data.put("heading_path", hit.headingPath());
        data.put("snippet", hit.snippet());
        return data;
    }

    private void refreshIndexesAfterMutation(Runnable ragSyncMutation) {
        if (!Boolean.TRUE.equals(configService.getConfig().getLocalIndexEnabled())) {
            runBestEffort(ragSyncMutation);
            return;
        }
        runBestEffort(localIndexService::reindexAll);
        runBestEffort(ragSyncMutation);
    }

    private void runBestEffort(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException ignored) {
            // Mutation succeeded; downstream indexing is best effort.
        }
    }

    private record ResolvedPage(
            String pageId,
            String parentPageId,
            String path,
            String title,
            String url) {
    }
}
