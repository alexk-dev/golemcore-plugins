package me.golemcore.plugins.golemcore.nextcloud;

import me.golemcore.plugin.api.extension.model.Attachment;
import me.golemcore.plugin.api.extension.model.ToolFailureKind;
import me.golemcore.plugin.api.extension.model.ToolResult;
import me.golemcore.plugins.golemcore.nextcloud.support.NextcloudApiException;
import me.golemcore.plugins.golemcore.nextcloud.support.NextcloudFileContent;
import me.golemcore.plugins.golemcore.nextcloud.support.NextcloudPathValidator;
import me.golemcore.plugins.golemcore.nextcloud.support.NextcloudResource;
import me.golemcore.plugins.golemcore.nextcloud.support.NextcloudTransportException;
import me.golemcore.plugins.golemcore.nextcloud.support.NextcloudWebDavClient;
import org.springframework.stereotype.Service;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class NextcloudFilesService {

    private static final long BYTES_PER_KB = 1024;
    private static final Set<String> TEXT_MIME_TYPES = Set.of(
            "application/json",
            "application/ld+json",
            "application/xml",
            "application/x-yaml",
            "application/yaml",
            "application/javascript",
            "application/x-javascript",
            "image/svg+xml");
    private static final Set<String> TEXT_FILE_EXTENSIONS = Set.of(
            "txt",
            "md",
            "markdown",
            "json",
            "xml",
            "yaml",
            "yml",
            "csv",
            "tsv",
            "log",
            "properties",
            "java",
            "kt",
            "groovy",
            "js",
            "ts",
            "jsx",
            "tsx",
            "py",
            "rb",
            "go",
            "rs",
            "c",
            "cc",
            "cpp",
            "h",
            "hpp",
            "html",
            "css",
            "scss",
            "sql",
            "sh",
            "bash",
            "zsh",
            "ini",
            "toml");

    private final NextcloudWebDavClient client;
    private final NextcloudPluginConfigService configService;
    private final NextcloudPathValidator pathValidator = new NextcloudPathValidator();

    public NextcloudFilesService(NextcloudWebDavClient client, NextcloudPluginConfigService configService) {
        this.client = client;
        this.configService = configService;
    }

    public NextcloudPluginConfig getConfig() {
        return configService.getConfig();
    }

    public ToolResult listDirectory(String path) {
        try {
            String normalizedPath = pathValidator.normalizeOptionalPath(path);
            List<NextcloudResource> entries = client.listDirectory(normalizedPath);
            StringBuilder output = new StringBuilder();
            output.append("Directory: ").append(displayPath(normalizedPath)).append('\n');
            output.append("Entries: ").append(entries.size()).append("\n\n");
            for (NextcloudResource entry : entries) {
                if (entry.directory()) {
                    output.append("[DIR]  ").append(entry.name()).append("/\n");
                } else {
                    output.append("[FILE] ").append(entry.name()).append(" (")
                            .append(formatSize(entry.size() != null ? entry.size() : 0L))
                            .append(")\n");
                }
            }

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("path", normalizedPath);
            data.put("entries", entries.stream().map(this::resourceToMap).toList());
            return ToolResult.success(output.toString(), data);
        } catch (IllegalArgumentException | NextcloudApiException | NextcloudTransportException ex) {
            return executionFailure(ex.getMessage());
        }
    }

    public ToolResult fileInfo(String path) {
        try {
            String normalizedPath = pathValidator.normalizeOptionalPath(path);
            NextcloudResource resource = client.fileInfo(normalizedPath);
            Map<String, Object> data = resourceToMap(resource);
            StringBuilder output = new StringBuilder();
            output.append("Path: ").append(displayPath(resource.path())).append('\n');
            output.append("Type: ").append(resource.directory() ? "Directory" : "File").append('\n');
            if (resource.size() != null) {
                output.append("Size: ").append(formatSize(resource.size())).append('\n');
            }
            if (hasText(resource.mimeType())) {
                output.append("MIME Type: ").append(resource.mimeType()).append('\n');
            }
            if (hasText(resource.lastModified())) {
                output.append("Modified: ").append(resource.lastModified()).append('\n');
            }
            if (hasText(resource.etag())) {
                output.append("ETag: ").append(resource.etag()).append('\n');
            }
            return ToolResult.success(output.toString().trim(), data);
        } catch (IllegalArgumentException | NextcloudApiException | NextcloudTransportException ex) {
            return executionFailure(ex.getMessage());
        }
    }

    public ToolResult readFile(String path) {
        try {
            String normalizedPath = pathValidator.normalizeRequiredPath(path);
            NextcloudFileContent content = client.readFile(normalizedPath);
            NextcloudPluginConfig config = configService.getConfig();
            long size = content.size() != null ? content.size() : content.bytes().length;
            if (size > config.getMaxDownloadBytes()) {
                return executionFailure("File too large to read directly: " + displayPath(normalizedPath)
                        + " (" + formatSize(size) + ")");
            }

            boolean textFile = isTextFile(normalizedPath, content.mimeType(), content.bytes());
            if (textFile) {
                return buildTextReadResult(content, config);
            }
            return buildBinaryReadResult(content, size);
        } catch (IllegalArgumentException | NextcloudApiException | NextcloudTransportException ex) {
            return executionFailure(ex.getMessage());
        }
    }

    public ToolResult writeFile(String path, String content, String contentBase64, boolean append) {
        if (!Boolean.TRUE.equals(configService.getConfig().getAllowWrite())) {
            return ToolResult.failure(ToolFailureKind.POLICY_DENIED,
                    "Nextcloud write is disabled in plugin settings");
        }

        try {
            String normalizedPath = pathValidator.normalizeRequiredPath(path);
            byte[] payload = resolveWritePayload(content, contentBase64);
            byte[] finalPayload = payload;
            if (append) {
                finalPayload = appendToExistingContent(normalizedPath, payload);
            }
            client.createDirectoryRecursive(pathValidator.parentPath(normalizedPath));
            client.writeFile(normalizedPath, finalPayload);

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("path", normalizedPath);
            data.put("size", finalPayload.length);
            data.put("operation", append ? "append" : "write");
            return ToolResult.success(
                    "Successfully " + (append ? "appended to" : "written") + " file: " + normalizedPath,
                    data);
        } catch (IllegalArgumentException | NextcloudApiException | NextcloudTransportException ex) {
            return executionFailure(ex.getMessage());
        }
    }

    public ToolResult createDirectory(String path) {
        if (!Boolean.TRUE.equals(configService.getConfig().getAllowWrite())) {
            return ToolResult.failure(ToolFailureKind.POLICY_DENIED,
                    "Nextcloud write is disabled in plugin settings");
        }

        try {
            String normalizedPath = pathValidator.normalizeRequiredPath(path);
            client.createDirectoryRecursive(normalizedPath);
            return ToolResult.success("Created directory: " + normalizedPath, Map.of("path", normalizedPath));
        } catch (IllegalArgumentException | NextcloudApiException | NextcloudTransportException ex) {
            return executionFailure(ex.getMessage());
        }
    }

    public ToolResult delete(String path) {
        if (!Boolean.TRUE.equals(configService.getConfig().getAllowDelete())) {
            return ToolResult.failure(ToolFailureKind.POLICY_DENIED,
                    "Nextcloud delete is disabled in plugin settings");
        }

        try {
            String normalizedPath = pathValidator.normalizeRequiredPath(path);
            client.delete(normalizedPath);
            return ToolResult.success("Deleted: " + normalizedPath, Map.of("path", normalizedPath));
        } catch (IllegalArgumentException | NextcloudApiException | NextcloudTransportException ex) {
            return executionFailure(ex.getMessage());
        }
    }

    public ToolResult move(String path, String targetPath) {
        if (!Boolean.TRUE.equals(configService.getConfig().getAllowMove())) {
            return ToolResult.failure(ToolFailureKind.POLICY_DENIED,
                    "Nextcloud move is disabled in plugin settings");
        }

        try {
            String normalizedPath = pathValidator.normalizeRequiredPath(path);
            String normalizedTargetPath = pathValidator.normalizeRequiredPath(targetPath);
            if (normalizedPath.equals(normalizedTargetPath)) {
                return executionFailure("Source and target paths must differ");
            }
            client.createDirectoryRecursive(pathValidator.parentPath(normalizedTargetPath));
            client.move(normalizedPath, normalizedTargetPath);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("path", normalizedPath);
            data.put("target_path", normalizedTargetPath);
            return ToolResult.success("Moved: " + normalizedPath + " -> " + normalizedTargetPath, data);
        } catch (IllegalArgumentException | NextcloudApiException | NextcloudTransportException ex) {
            return executionFailure(ex.getMessage());
        }
    }

    public ToolResult copy(String path, String targetPath) {
        if (!Boolean.TRUE.equals(configService.getConfig().getAllowCopy())) {
            return ToolResult.failure(ToolFailureKind.POLICY_DENIED,
                    "Nextcloud copy is disabled in plugin settings");
        }

        try {
            String normalizedPath = pathValidator.normalizeRequiredPath(path);
            String normalizedTargetPath = pathValidator.normalizeRequiredPath(targetPath);
            if (normalizedPath.equals(normalizedTargetPath)) {
                return executionFailure("Source and target paths must differ");
            }
            client.createDirectoryRecursive(pathValidator.parentPath(normalizedTargetPath));
            client.copy(normalizedPath, normalizedTargetPath);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("path", normalizedPath);
            data.put("target_path", normalizedTargetPath);
            return ToolResult.success("Copied: " + normalizedPath + " -> " + normalizedTargetPath, data);
        } catch (IllegalArgumentException | NextcloudApiException | NextcloudTransportException ex) {
            return executionFailure(ex.getMessage());
        }
    }

    private ToolResult buildTextReadResult(NextcloudFileContent content, NextcloudPluginConfig config) {
        String text = decodeText(content.bytes(), content.mimeType());
        boolean truncated = text.length() > config.getMaxInlineTextChars();
        String visibleText = truncated ? text.substring(0, config.getMaxInlineTextChars()) : text;

        Map<String, Object> data = baseReadData(content, true);
        data.put("content", visibleText);
        data.put("truncated", truncated);
        if (truncated) {
            data.put("originalLength", text.length());
            data.put("attachment", buildAttachment(content, content.bytes()));
        }

        String output = truncated
                ? visibleText + "\n\n[TRUNCATED: " + text.length()
                        + " chars total. Full file attached.]"
                : visibleText;
        return ToolResult.success(output, data);
    }

    private ToolResult buildBinaryReadResult(NextcloudFileContent content, long size) {
        Map<String, Object> data = baseReadData(content, false);
        Attachment attachment = buildAttachment(content, content.bytes());
        data.put("attachment", attachment);
        data.put("filename", attachment.getFilename());
        StringBuilder output = new StringBuilder();
        output.append("Read binary file: ").append(content.path()).append('\n');
        output.append("Size: ").append(formatSize(size)).append('\n');
        if (hasText(content.mimeType())) {
            output.append("MIME Type: ").append(content.mimeType());
        }
        return ToolResult.success(output.toString().trim(), data);
    }

    private Map<String, Object> baseReadData(NextcloudFileContent content, boolean textFile) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("path", content.path());
        data.put("size", content.size() != null ? content.size() : content.bytes().length);
        data.put("mime_type", content.mimeType());
        data.put("etag", content.etag());
        data.put("modified", content.lastModified());
        data.put("is_text", textFile);
        return data;
    }

    private Attachment buildAttachment(NextcloudFileContent content, byte[] bytes) {
        String mimeType = hasText(content.mimeType()) ? stripMimeParameters(content.mimeType())
                : detectMimeType(content.path());
        Attachment.Type attachmentType = mimeType.startsWith("image/")
                ? Attachment.Type.IMAGE
                : Attachment.Type.DOCUMENT;
        return Attachment.builder()
                .type(attachmentType)
                .data(bytes)
                .filename(fileName(content.path()))
                .mimeType(mimeType)
                .caption("Nextcloud file: " + content.path())
                .build();
    }

    private byte[] appendToExistingContent(String normalizedPath, byte[] payload) {
        try {
            NextcloudResource existing = client.fileInfo(normalizedPath);
            if (existing.directory()) {
                throw new IllegalArgumentException("Cannot append to a directory: " + normalizedPath);
            }
            long size = existing.size() != null ? existing.size() : 0L;
            if (size > configService.getConfig().getMaxDownloadBytes()) {
                throw new IllegalArgumentException("Existing file is too large to append safely: " + normalizedPath);
            }
            NextcloudFileContent existingContent = client.readFile(normalizedPath);
            byte[] combined = new byte[existingContent.bytes().length + payload.length];
            System.arraycopy(existingContent.bytes(), 0, combined, 0, existingContent.bytes().length);
            System.arraycopy(payload, 0, combined, existingContent.bytes().length, payload.length);
            return combined;
        } catch (NextcloudApiException ex) {
            if (ex.getStatusCode() == 404) {
                return payload;
            }
            throw ex;
        }
    }

    private byte[] resolveWritePayload(String content, String contentBase64) {
        if (hasText(content) && hasText(contentBase64)) {
            throw new IllegalArgumentException("Provide either content or content_base64, not both");
        }
        if (contentBase64 != null && !contentBase64.isBlank()) {
            try {
                return Base64.getDecoder().decode(contentBase64);
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException("content_base64 must be valid base64");
            }
        }
        if (content != null) {
            return content.getBytes(StandardCharsets.UTF_8);
        }
        throw new IllegalArgumentException("content or content_base64 is required");
    }

    private ToolResult executionFailure(String message) {
        return ToolResult.failure(ToolFailureKind.EXECUTION_FAILED, message);
    }

    private boolean isTextFile(String path, String mimeType, byte[] bytes) {
        String normalizedMimeType = stripMimeParameters(mimeType).toLowerCase(Locale.ROOT);
        if (normalizedMimeType.startsWith("text/") || TEXT_MIME_TYPES.contains(normalizedMimeType)) {
            return true;
        }
        if (normalizedMimeType.startsWith("image/") || normalizedMimeType.startsWith("audio/")
                || normalizedMimeType.startsWith("video/") || "application/pdf".equals(normalizedMimeType)
                || "application/zip".equals(normalizedMimeType)
                || "application/octet-stream".equals(normalizedMimeType)) {
            return false;
        }
        String extension = fileExtension(path);
        if (TEXT_FILE_EXTENSIONS.contains(extension)) {
            return true;
        }
        int limit = Math.min(bytes.length, 512);
        for (int index = 0; index < limit; index++) {
            if (bytes[index] == 0) {
                return false;
            }
        }
        return true;
    }

    private String decodeText(byte[] bytes, String mimeType) {
        Charset charset = StandardCharsets.UTF_8;
        String lowerMimeType = mimeType != null ? mimeType.toLowerCase(Locale.ROOT) : "";
        int charsetIndex = lowerMimeType.indexOf("charset=");
        if (charsetIndex >= 0) {
            String charsetName = lowerMimeType.substring(charsetIndex + 8).trim();
            int separatorIndex = charsetName.indexOf(';');
            if (separatorIndex >= 0) {
                charsetName = charsetName.substring(0, separatorIndex).trim();
            }
            try {
                charset = Charset.forName(charsetName.replace('"', ' ').trim());
            } catch (RuntimeException ignored) {
                charset = StandardCharsets.UTF_8;
            }
        }
        try {
            return charset.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPLACE)
                    .onUnmappableCharacter(CodingErrorAction.REPLACE)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException ignored) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }

    private Map<String, Object> resourceToMap(NextcloudResource resource) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("path", resource.path());
        data.put("name", resource.name());
        data.put("type", resource.directory() ? "directory" : "file");
        data.put("size", resource.size());
        data.put("mime_type", resource.mimeType());
        data.put("modified", resource.lastModified());
        data.put("etag", resource.etag());
        return data;
    }

    private String displayPath(String normalizedPath) {
        return normalizedPath == null || normalizedPath.isBlank() ? "/" : normalizedPath;
    }

    private String fileName(String path) {
        int separatorIndex = path.lastIndexOf('/');
        return separatorIndex >= 0 ? path.substring(separatorIndex + 1) : path;
    }

    private String fileExtension(String path) {
        String currentFileName = fileName(path).toLowerCase(Locale.ROOT);
        int dotIndex = currentFileName.lastIndexOf('.');
        return dotIndex >= 0 ? currentFileName.substring(dotIndex + 1) : "";
    }

    private String stripMimeParameters(String mimeType) {
        if (mimeType == null) {
            return "";
        }
        int separatorIndex = mimeType.indexOf(';');
        return separatorIndex >= 0 ? mimeType.substring(0, separatorIndex).trim() : mimeType.trim();
    }

    private String detectMimeType(String path) {
        String extension = fileExtension(path);
        return switch (extension) {
        case "txt" -> "text/plain";
        case "md", "markdown" -> "text/markdown";
        case "json" -> "application/json";
        case "xml" -> "application/xml";
        case "yaml", "yml" -> "application/yaml";
        case "csv" -> "text/csv";
        case "html" -> "text/html";
        case "css" -> "text/css";
        case "js" -> "application/javascript";
        case "ts" -> "text/typescript";
        case "svg" -> "image/svg+xml";
        case "png" -> "image/png";
        case "jpg", "jpeg" -> "image/jpeg";
        case "gif" -> "image/gif";
        case "webp" -> "image/webp";
        case "pdf" -> "application/pdf";
        case "zip" -> "application/zip";
        default -> "application/octet-stream";
        };
    }

    private String formatSize(long bytes) {
        if (bytes < BYTES_PER_KB) {
            return bytes + " B";
        }
        if (bytes < BYTES_PER_KB * BYTES_PER_KB) {
            return String.format(Locale.ROOT, "%.1f KB", bytes / (double) BYTES_PER_KB);
        }
        if (bytes < BYTES_PER_KB * BYTES_PER_KB * BYTES_PER_KB) {
            return String.format(Locale.ROOT, "%.1f MB", bytes / (double) (BYTES_PER_KB * BYTES_PER_KB));
        }
        return String.format(Locale.ROOT, "%.1f GB", bytes / (double) (BYTES_PER_KB * BYTES_PER_KB * BYTES_PER_KB));
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
