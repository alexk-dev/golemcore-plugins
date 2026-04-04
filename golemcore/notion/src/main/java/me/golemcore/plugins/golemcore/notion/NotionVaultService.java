package me.golemcore.plugins.golemcore.notion;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import me.golemcore.plugin.api.extension.model.ToolFailureKind;
import me.golemcore.plugin.api.extension.model.ToolResult;
import me.golemcore.plugins.golemcore.notion.support.NotionApiClient;
import me.golemcore.plugins.golemcore.notion.support.NotionApiException;
import me.golemcore.plugins.golemcore.notion.support.NotionChildSummary;
import me.golemcore.plugins.golemcore.notion.support.NotionDataSourceQueryResult;
import me.golemcore.plugins.golemcore.notion.support.NotionDataSourceSummary;
import me.golemcore.plugins.golemcore.notion.support.NotionDatabaseSummary;
import me.golemcore.plugins.golemcore.notion.support.NotionFileAttachmentSummary;
import me.golemcore.plugins.golemcore.notion.support.NotionFileUploadSummary;
import me.golemcore.plugins.golemcore.notion.support.NotionLocalIndexService;
import me.golemcore.plugins.golemcore.notion.support.NotionPageDetails;
import me.golemcore.plugins.golemcore.notion.support.NotionPageSummary;
import me.golemcore.plugins.golemcore.notion.support.NotionPathValidator;
import me.golemcore.plugins.golemcore.notion.support.NotionRagSyncService;
import me.golemcore.plugins.golemcore.notion.support.NotionSearchHit;
import me.golemcore.plugins.golemcore.notion.support.NotionTransportException;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
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
    private final ObjectMapper objectMapper = new ObjectMapper();

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
            List<NotionChildSummary> items = apiClient.listChildItems(page.pageId());
            List<String> entries = items.stream().map(NotionChildSummary::title).toList();
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("path", normalizedPath);
            data.put("entries", entries);
            data.put("files", entries);
            data.put("items", items.stream().map(this::toChildItem).toList());
            return ToolResult.success(
                    "Listed " + items.size() + " item(s) in " + displayPath(normalizedPath),
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
            NotionPageDetails details = apiClient.retrievePageDetails(page.pageId());

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("path", normalizedPath);
            data.put("page_id", page.pageId());
            data.put("content", visibleContent);
            data.put("truncated", truncated);
            data.put("files", details.files().stream().map(this::toFileAttachment).toList());
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
            ensureChildTitleAbsent(parentPage.pageId(), title);
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
            ensureChildTitleAbsent(targetParent.pageId(), targetTitle);
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
            ensureChildTitleAbsent(source.parentPageId(), pathValidator.leafName(targetPath));
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
        } catch (IllegalArgumentException | NotionApiException | NotionTransportException ex) {
            return executionFailure(ex.getMessage());
        }
    }

    public ToolResult createDatabase(
            String parentPath,
            String title,
            String description,
            String propertiesJson,
            Boolean inline,
            String iconEmoji,
            String coverUrl) {
        if (!Boolean.TRUE.equals(configService.getConfig().getAllowWrite())) {
            return ToolResult.failure(ToolFailureKind.POLICY_DENIED, "Notion write is disabled in plugin settings");
        }
        try {
            String normalizedParentPath = pathValidator.normalizeNotePath(parentPath);
            ResolvedPage parentPage = resolveExistingPage(normalizedParentPath);
            ensureChildTitleAbsent(parentPage.pageId(), requiredText(title, "title"));
            NotionDatabaseSummary database = apiClient.createDatabase(
                    parentPage.pageId(),
                    requiredText(title, "title"),
                    description,
                    parseJsonObject(propertiesJson),
                    Boolean.TRUE.equals(inline),
                    iconEmoji,
                    coverUrl);
            return ToolResult.success(
                    "Created database " + database.title(),
                    Map.of(
                            "parent_path", normalizedParentPath,
                            "database", toDatabaseSummary(database)));
        } catch (IllegalArgumentException | NotionApiException | NotionTransportException ex) {
            return executionFailure(ex.getMessage());
        }
    }

    public ToolResult readDatabase(String databaseId) {
        try {
            NotionDatabaseSummary database = apiClient.retrieveDatabase(requiredText(databaseId, "database_id"));
            return ToolResult.success(
                    "Retrieved database " + database.title(),
                    Map.of("database", toDatabaseSummary(database)));
        } catch (IllegalArgumentException | NotionApiException | NotionTransportException ex) {
            return executionFailure(ex.getMessage());
        }
    }

    public ToolResult updateDatabase(
            String databaseId,
            String title,
            String description,
            String iconEmoji,
            String coverUrl) {
        if (!Boolean.TRUE.equals(configService.getConfig().getAllowWrite())) {
            return ToolResult.failure(ToolFailureKind.POLICY_DENIED, "Notion write is disabled in plugin settings");
        }
        try {
            NotionDatabaseSummary database = apiClient.updateDatabase(
                    requiredText(databaseId, "database_id"),
                    title,
                    description,
                    iconEmoji,
                    coverUrl);
            return ToolResult.success(
                    "Updated database " + database.title(),
                    Map.of("database", toDatabaseSummary(database)));
        } catch (IllegalArgumentException | NotionApiException | NotionTransportException ex) {
            return executionFailure(ex.getMessage());
        }
    }

    public ToolResult createDataSource(String databaseId, String title, String propertiesJson, String iconEmoji) {
        if (!Boolean.TRUE.equals(configService.getConfig().getAllowWrite())) {
            return ToolResult.failure(ToolFailureKind.POLICY_DENIED, "Notion write is disabled in plugin settings");
        }
        try {
            NotionDataSourceSummary dataSource = apiClient.createDataSource(
                    requiredText(databaseId, "database_id"),
                    title,
                    parseRequiredJsonObject(propertiesJson, "properties_json"),
                    iconEmoji);
            return ToolResult.success(
                    "Created data source " + dataSource.title(),
                    Map.of("data_source", toDataSourceSummary(dataSource)));
        } catch (IllegalArgumentException | NotionApiException | NotionTransportException ex) {
            return executionFailure(ex.getMessage());
        }
    }

    public ToolResult readDataSource(String dataSourceId) {
        try {
            NotionDataSourceSummary dataSource = apiClient
                    .retrieveDataSource(requiredText(dataSourceId, "data_source_id"));
            return ToolResult.success(
                    "Retrieved data source " + dataSource.title(),
                    Map.of("data_source", toDataSourceSummary(dataSource)));
        } catch (IllegalArgumentException | NotionApiException | NotionTransportException ex) {
            return executionFailure(ex.getMessage());
        }
    }

    public ToolResult updateDataSource(String dataSourceId, String title, String propertiesJson, String iconEmoji) {
        if (!Boolean.TRUE.equals(configService.getConfig().getAllowWrite())) {
            return ToolResult.failure(ToolFailureKind.POLICY_DENIED, "Notion write is disabled in plugin settings");
        }
        try {
            NotionDataSourceSummary dataSource = apiClient.updateDataSource(
                    requiredText(dataSourceId, "data_source_id"),
                    title,
                    parseJsonObject(propertiesJson),
                    iconEmoji);
            return ToolResult.success(
                    "Updated data source " + dataSource.title(),
                    Map.of("data_source", toDataSourceSummary(dataSource)));
        } catch (IllegalArgumentException | NotionApiException | NotionTransportException ex) {
            return executionFailure(ex.getMessage());
        }
    }

    public ToolResult queryDatabase(
            String databaseId,
            String dataSourceId,
            String filterJson,
            String sortsJson,
            Integer limit,
            String cursor) {
        try {
            String resolvedDataSourceId = resolveDataSourceId(databaseId, dataSourceId);
            NotionDataSourceQueryResult queryResult = apiClient.queryDataSource(
                    resolvedDataSourceId,
                    filterJson,
                    sortsJson,
                    limit,
                    cursor);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("data_source_id", resolvedDataSourceId);
            data.put("count", queryResult.count());
            data.put("has_more", queryResult.hasMore());
            data.put("next_cursor", queryResult.nextCursor());
            data.put("results", queryResult.results());
            return ToolResult.success("Retrieved " + queryResult.count() + " database item(s)", data);
        } catch (IllegalArgumentException | NotionApiException | NotionTransportException ex) {
            return executionFailure(ex.getMessage());
        }
    }

    public ToolResult createDatabaseEntry(
            String databaseId,
            String dataSourceId,
            String propertiesJson,
            String markdown,
            String contentJson,
            String iconEmoji,
            String coverUrl) {
        if (!Boolean.TRUE.equals(configService.getConfig().getAllowWrite())) {
            return ToolResult.failure(ToolFailureKind.POLICY_DENIED, "Notion write is disabled in plugin settings");
        }
        try {
            String resolvedDataSourceId = resolveDataSourceId(databaseId, dataSourceId);
            NotionPageSummary entry = apiClient.createDataSourceEntry(
                    resolvedDataSourceId,
                    requiredText(propertiesJson, "properties_json"),
                    markdown,
                    contentJson,
                    iconEmoji,
                    coverUrl);
            return ToolResult.success(
                    "Created database entry " + entry.title(),
                    Map.of(
                            "data_source_id", resolvedDataSourceId,
                            "page_id", entry.id(),
                            "title", entry.title(),
                            "url", entry.url()));
        } catch (IllegalArgumentException | NotionApiException | NotionTransportException ex) {
            return executionFailure(ex.getMessage());
        }
    }

    public ToolResult readDatabaseEntry(String pageId) {
        try {
            NotionPageDetails page = apiClient.retrievePageDetails(requiredText(pageId, "page_id"));
            return ToolResult.success(
                    "Retrieved database entry " + page.title(),
                    Map.of("page", toPageDetails(page)));
        } catch (IllegalArgumentException | NotionApiException | NotionTransportException ex) {
            return executionFailure(ex.getMessage());
        }
    }

    public ToolResult updateDatabaseEntry(String pageId, String propertiesJson, String iconEmoji, String coverUrl) {
        if (!Boolean.TRUE.equals(configService.getConfig().getAllowWrite())) {
            return ToolResult.failure(ToolFailureKind.POLICY_DENIED, "Notion write is disabled in plugin settings");
        }
        try {
            NotionPageSummary page = apiClient.updateDataSourceEntry(
                    requiredText(pageId, "page_id"),
                    propertiesJson,
                    iconEmoji,
                    coverUrl);
            return ToolResult.success(
                    "Updated database entry " + page.title(),
                    Map.of(
                            "page_id", page.id(),
                            "title", page.title(),
                            "url", page.url()));
        } catch (IllegalArgumentException | NotionApiException | NotionTransportException ex) {
            return executionFailure(ex.getMessage());
        }
    }

    public ToolResult listPageFiles(String pageId) {
        try {
            NotionPageDetails page = apiClient.retrievePageDetails(requiredText(pageId, "page_id"));
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("page_id", page.id());
            data.put("title", page.title());
            data.put("count", page.files().size());
            data.put("files", page.files().stream().map(this::toFileAttachment).toList());
            return ToolResult.success("Found " + page.files().size() + " file(s) on page " + page.title(), data);
        } catch (IllegalArgumentException | NotionApiException | NotionTransportException ex) {
            return executionFailure(ex.getMessage());
        }
    }

    public ToolResult createFileUpload(
            String mode,
            String filename,
            String contentType,
            Integer numberOfParts,
            String externalUrl) {
        if (!Boolean.TRUE.equals(configService.getConfig().getAllowWrite())) {
            return ToolResult.failure(ToolFailureKind.POLICY_DENIED, "Notion write is disabled in plugin settings");
        }
        try {
            NotionFileUploadSummary upload = apiClient.createFileUpload(
                    mode,
                    filename,
                    contentType,
                    numberOfParts,
                    externalUrl);
            return ToolResult.success(
                    "Created file upload " + upload.id(),
                    Map.of("file_upload", toFileUploadSummary(upload)));
        } catch (IllegalArgumentException | NotionApiException | NotionTransportException ex) {
            return executionFailure(ex.getMessage());
        }
    }

    public ToolResult uploadFileContent(String fileUploadId, String localPath, String contentType) {
        if (!Boolean.TRUE.equals(configService.getConfig().getAllowWrite())) {
            return ToolResult.failure(ToolFailureKind.POLICY_DENIED, "Notion write is disabled in plugin settings");
        }
        try {
            NotionFileUploadSummary upload = apiClient.uploadFileContent(
                    requiredText(fileUploadId, "file_upload_id"),
                    Path.of(requiredText(localPath, "local_path")),
                    contentType);
            return ToolResult.success(
                    "Uploaded file content for " + upload.id(),
                    Map.of("file_upload", toFileUploadSummary(upload)));
        } catch (IllegalArgumentException | NotionApiException | NotionTransportException ex) {
            return executionFailure(ex.getMessage());
        }
    }

    public ToolResult completeFileUpload(String fileUploadId) {
        if (!Boolean.TRUE.equals(configService.getConfig().getAllowWrite())) {
            return ToolResult.failure(ToolFailureKind.POLICY_DENIED, "Notion write is disabled in plugin settings");
        }
        try {
            NotionFileUploadSummary upload = apiClient.completeFileUpload(requiredText(fileUploadId, "file_upload_id"));
            return ToolResult.success(
                    "Completed file upload " + upload.id(),
                    Map.of("file_upload", toFileUploadSummary(upload)));
        } catch (IllegalArgumentException | NotionApiException | NotionTransportException ex) {
            return executionFailure(ex.getMessage());
        }
    }

    public ToolResult readFileUpload(String fileUploadId) {
        try {
            NotionFileUploadSummary upload = apiClient.retrieveFileUpload(requiredText(fileUploadId, "file_upload_id"));
            return ToolResult.success(
                    "Retrieved file upload " + upload.id(),
                    Map.of("file_upload", toFileUploadSummary(upload)));
        } catch (IllegalArgumentException | NotionApiException | NotionTransportException ex) {
            return executionFailure(ex.getMessage());
        }
    }

    public ToolResult listFileUploads(String status, Integer limit, String cursor) {
        try {
            List<NotionFileUploadSummary> uploads = apiClient.listFileUploads(status, limit, cursor);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("count", uploads.size());
            data.put("uploads", uploads.stream().map(this::toFileUploadSummary).toList());
            return ToolResult.success("Listed " + uploads.size() + " file upload(s)", data);
        } catch (IllegalArgumentException | NotionApiException | NotionTransportException ex) {
            return executionFailure(ex.getMessage());
        }
    }

    public ToolResult attachFileToPage(
            String pageId,
            String fileUploadId,
            String externalUrl,
            String fileName,
            String caption,
            String blockType) {
        if (!Boolean.TRUE.equals(configService.getConfig().getAllowWrite())) {
            return ToolResult.failure(ToolFailureKind.POLICY_DENIED, "Notion write is disabled in plugin settings");
        }
        try {
            NotionPageSummary block = apiClient.appendFileBlock(
                    requiredText(pageId, "page_id"),
                    fileUploadId,
                    externalUrl,
                    fileName,
                    caption,
                    blockType);
            return ToolResult.success(
                    "Attached file to page " + pageId,
                    Map.of(
                            "page_id", pageId,
                            "block_id", block.id(),
                            "url", block.url(),
                            "file_name", fileName != null ? fileName : ""));
        } catch (IllegalArgumentException | NotionApiException | NotionTransportException ex) {
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

    private void ensureChildTitleAbsent(String parentPageId, String title) {
        boolean exists = apiClient.listChildItems(parentPageId).stream()
                .anyMatch(item -> title.equals(item.title()));
        if (exists) {
            throw new IllegalArgumentException("Target item already exists: " + title);
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

    private String resolveDataSourceId(String databaseId, String dataSourceId) {
        if (dataSourceId != null && !dataSourceId.isBlank()) {
            return dataSourceId;
        }
        if (databaseId == null || databaseId.isBlank()) {
            throw new IllegalArgumentException("database_id or data_source_id is required");
        }
        NotionDatabaseSummary database = apiClient.retrieveDatabase(databaseId);
        if (database.dataSources().isEmpty()) {
            throw new IllegalArgumentException("Database has no data sources: " + databaseId);
        }
        if (database.dataSources().size() > 1) {
            throw new IllegalArgumentException(
                    "Database has multiple data sources; provide data_source_id explicitly: " + databaseId);
        }
        Object id = database.dataSources().getFirst().get("id");
        if (!(id instanceof String text) || text.isBlank()) {
            throw new IllegalArgumentException("Database data source ID is missing: " + databaseId);
        }
        return text;
    }

    private String requiredText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value;
    }

    private Map<String, Object> parseJsonObject(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(rawJson, new TypeReference<LinkedHashMap<String, Object>>() {
            });
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Expected JSON object: " + ex.getMessage(), ex);
        }
    }

    private Map<String, Object> parseRequiredJsonObject(String rawJson, String fieldName) {
        if (rawJson == null || rawJson.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        Map<String, Object> parsed = parseJsonObject(rawJson);
        if (parsed.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be empty");
        }
        return parsed;
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

    private Map<String, Object> toChildItem(NotionChildSummary child) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", child.id());
        data.put("title", child.title());
        data.put("url", child.url());
        data.put("type", child.kind());
        return data;
    }

    private Map<String, Object> toDatabaseSummary(NotionDatabaseSummary database) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", database.id());
        data.put("title", database.title());
        data.put("url", database.url());
        data.put("data_sources", database.dataSources());
        return data;
    }

    private Map<String, Object> toDataSourceSummary(NotionDataSourceSummary dataSource) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", dataSource.id());
        data.put("title", dataSource.title());
        data.put("url", dataSource.url());
        data.put("properties", dataSource.properties());
        return data;
    }

    private Map<String, Object> toPageDetails(NotionPageDetails page) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", page.id());
        data.put("title", page.title());
        data.put("url", page.url());
        data.put("files", page.files().stream().map(this::toFileAttachment).toList());
        data.put("properties", page.rawProperties());
        return data;
    }

    private Map<String, Object> toFileUploadSummary(NotionFileUploadSummary upload) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", upload.id());
        data.put("status", upload.status());
        data.put("filename", upload.filename());
        data.put("content_type", upload.contentType());
        data.put("content_length", upload.contentLength());
        data.put("upload_url", upload.uploadUrl());
        data.put("expiry_time", upload.expiryTime());
        return data;
    }

    private Map<String, Object> toFileAttachment(NotionFileAttachmentSummary file) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("name", file.name());
        data.put("type", file.type());
        data.put("url", file.url());
        data.put("expiry_time", file.expiryTime());
        data.put("source_kind", file.sourceKind());
        data.put("source_name", file.sourceName());
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
