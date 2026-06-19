package me.golemcore.plugins.golemcore.s3;

import me.golemcore.plugin.api.extension.model.Attachment;
import me.golemcore.plugin.api.extension.model.ToolFailureKind;
import me.golemcore.plugin.api.extension.model.ToolResult;
import me.golemcore.plugins.golemcore.s3.support.S3MimeTypes;
import me.golemcore.plugins.golemcore.s3.support.S3MinioClient;
import me.golemcore.plugins.golemcore.s3.support.S3ObjectContent;
import me.golemcore.plugins.golemcore.s3.support.S3ObjectInfo;
import me.golemcore.plugins.golemcore.s3.support.S3PathValidator;
import me.golemcore.plugins.golemcore.s3.support.S3StorageException;
import org.springframework.stereotype.Service;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class S3FilesService {

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

    private final S3MinioClient client;
    private final S3PluginConfigService configService;
    private final S3PathValidator pathValidator = new S3PathValidator();

    public S3FilesService(S3MinioClient client, S3PluginConfigService configService) {
        this.client = client;
        this.configService = configService;
    }

    public S3PluginConfig getConfig() {
        return configService.getConfig();
    }

    public ToolResult listDirectory(String path) {
        try {
            String normalizedPath = pathValidator.normalizeOptionalPath(path);
            S3ObjectInfo object = client.findObject(normalizedPath);
            if (object != null) {
                return executionFailure("Not a directory: " + displayPath(normalizedPath));
            }
            if (!normalizedPath.isBlank() && !client.directoryExists(normalizedPath)) {
                return executionFailure("Directory not found: " + normalizedPath);
            }
            List<S3ObjectInfo> entries = client.listDirectory(normalizedPath);
            StringBuilder output = new StringBuilder();
            output.append("Directory: ").append(displayPath(normalizedPath)).append('\n');
            output.append("Entries: ").append(entries.size()).append("\n\n");
            for (S3ObjectInfo entry : entries) {
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
            data.put("entries", entries.stream().map(this::toObjectInfo).toList());
            return ToolResult.success(output.toString(), data);
        } catch (IllegalArgumentException | S3StorageException ex) {
            return executionFailure(ex.getMessage());
        }
    }

    public ToolResult fileInfo(String path) {
        try {
            String normalizedPath = pathValidator.normalizeRequiredPath(path);
            S3ObjectInfo object = client.findObject(normalizedPath);
            if (object != null) {
                return buildObjectInfoResult(object);
            }
            if (!client.directoryExists(normalizedPath)) {
                return executionFailure("Path not found: " + normalizedPath);
            }
            S3ObjectInfo marker = client.findDirectoryMarker(normalizedPath);
            S3ObjectInfo directory = marker != null
                    ? marker
                    : new S3ObjectInfo(
                            getConfig().getBucket(),
                            normalizedPath,
                            leafName(normalizedPath),
                            true,
                            null,
                            null,
                            null,
                            null);
            return buildObjectInfoResult(directory);
        } catch (IllegalArgumentException | S3StorageException ex) {
            return executionFailure(ex.getMessage());
        }
    }

    public ToolResult readFile(String path) {
        try {
            String normalizedPath = pathValidator.normalizeRequiredPath(path);
            S3ObjectInfo object = requireObject(normalizedPath);
            long size = object.size() != null ? object.size() : 0L;
            if (size > getConfig().getMaxDownloadBytes()) {
                return executionFailure("File too large to read directly: " + normalizedPath + " (" + formatSize(size)
                        + ")");
            }
            S3ObjectContent content = client.readObject(normalizedPath);
            boolean textFile = isTextFile(normalizedPath, content.contentType(), content.bytes());
            if (textFile) {
                return buildTextReadResult(content);
            }
            return buildBinaryReadResult(content, size);
        } catch (IllegalArgumentException | S3StorageException ex) {
            return executionFailure(ex.getMessage());
        }
    }

    public ToolResult writeFile(String path, String content, String contentBase64, boolean append) {
        if (!Boolean.TRUE.equals(getConfig().getAllowWrite())) {
            return ToolResult.failure(ToolFailureKind.POLICY_DENIED, "S3 write is disabled in plugin settings");
        }
        try {
            String normalizedPath = pathValidator.normalizeRequiredPath(path);
            byte[] payload = resolveWritePayload(content, contentBase64);
            byte[] finalPayload = append ? appendToExistingContent(normalizedPath, payload) : payload;
            client.writeObject(normalizedPath, finalPayload,
                    resolveContentType(normalizedPath, content, contentBase64));
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("path", normalizedPath);
            data.put("size", finalPayload.length);
            data.put("operation", append ? "append" : "write");
            return ToolResult.success(
                    "Successfully " + (append ? "appended to" : "written") + " object: " + normalizedPath,
                    data);
        } catch (IllegalArgumentException | S3StorageException ex) {
            return executionFailure(ex.getMessage());
        }
    }

    public ToolResult createDirectory(String path) {
        if (!Boolean.TRUE.equals(getConfig().getAllowWrite())) {
            return ToolResult.failure(ToolFailureKind.POLICY_DENIED, "S3 write is disabled in plugin settings");
        }
        try {
            String normalizedPath = pathValidator.normalizeRequiredPath(path);
            S3ObjectInfo object = client.findObject(normalizedPath);
            if (object != null) {
                return executionFailure("Path exists but is not a directory: " + normalizedPath);
            }
            if (client.directoryExists(normalizedPath)) {
                return ToolResult.success("Directory already exists: " + normalizedPath,
                        Map.of("path", normalizedPath));
            }
            client.createDirectoryMarker(normalizedPath);
            return ToolResult.success("Created directory: " + normalizedPath, Map.of("path", normalizedPath));
        } catch (IllegalArgumentException | S3StorageException ex) {
            return executionFailure(ex.getMessage());
        }
    }

    public ToolResult delete(String path) {
        if (!Boolean.TRUE.equals(getConfig().getAllowDelete())) {
            return ToolResult.failure(ToolFailureKind.POLICY_DENIED, "S3 delete is disabled in plugin settings");
        }
        try {
            String normalizedPath = pathValidator.normalizeRequiredPath(path);
            S3ObjectInfo object = client.findObject(normalizedPath);
            if (object != null) {
                client.deleteObject(normalizedPath);
                return ToolResult.success("Deleted: " + normalizedPath, Map.of("path", normalizedPath));
            }
            if (!client.directoryExists(normalizedPath)) {
                return executionFailure("Path not found: " + normalizedPath);
            }
            deleteDirectory(normalizedPath);
            return ToolResult.success("Deleted: " + normalizedPath, Map.of("path", normalizedPath));
        } catch (IllegalArgumentException | S3StorageException ex) {
            return executionFailure(ex.getMessage());
        }
    }

    public ToolResult move(String path, String targetPath) {
        if (!Boolean.TRUE.equals(getConfig().getAllowMove())) {
            return ToolResult.failure(ToolFailureKind.POLICY_DENIED, "S3 move is disabled in plugin settings");
        }
        try {
            String normalizedPath = pathValidator.normalizeRequiredPath(path);
            String normalizedTargetPath = pathValidator.normalizeRequiredPath(targetPath);
            if (normalizedPath.equals(normalizedTargetPath)) {
                return executionFailure("Source and target paths must differ");
            }
            ensureTargetAbsent(normalizedTargetPath);
            S3ObjectInfo object = client.findObject(normalizedPath);
            if (object != null) {
                client.copyObject(normalizedPath, normalizedTargetPath);
                client.deleteObject(normalizedPath);
            } else {
                copyDirectory(normalizedPath, normalizedTargetPath);
                deleteDirectory(normalizedPath);
            }
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("path", normalizedPath);
            data.put("target_path", normalizedTargetPath);
            return ToolResult.success("Moved: " + normalizedPath + " -> " + normalizedTargetPath, data);
        } catch (IllegalArgumentException | S3StorageException ex) {
            return executionFailure(ex.getMessage());
        }
    }

    public ToolResult copy(String path, String targetPath) {
        if (!Boolean.TRUE.equals(getConfig().getAllowCopy())) {
            return ToolResult.failure(ToolFailureKind.POLICY_DENIED, "S3 copy is disabled in plugin settings");
        }
        try {
            String normalizedPath = pathValidator.normalizeRequiredPath(path);
            String normalizedTargetPath = pathValidator.normalizeRequiredPath(targetPath);
            if (normalizedPath.equals(normalizedTargetPath)) {
                return executionFailure("Source and target paths must differ");
            }
            ensureTargetAbsent(normalizedTargetPath);
            S3ObjectInfo object = client.findObject(normalizedPath);
            if (object != null) {
                client.copyObject(normalizedPath, normalizedTargetPath);
            } else {
                copyDirectory(normalizedPath, normalizedTargetPath);
            }
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("path", normalizedPath);
            data.put("target_path", normalizedTargetPath);
            return ToolResult.success("Copied: " + normalizedPath + " -> " + normalizedTargetPath, data);
        } catch (IllegalArgumentException | S3StorageException ex) {
            return executionFailure(ex.getMessage());
        }
    }

    private ToolResult buildObjectInfoResult(S3ObjectInfo info) {
        Map<String, Object> data = toObjectInfo(info);
        StringBuilder output = new StringBuilder();
        output.append("Path: ").append(displayPath(info.key())).append('\n');
        output.append("Type: ").append(info.directory() ? "Directory" : "File").append('\n');
        if (info.size() != null) {
            output.append("Size: ").append(formatSize(info.size())).append('\n');
        }
        if (info.contentType() != null && !info.contentType().isBlank()) {
            output.append("Content-Type: ").append(info.contentType()).append('\n');
        }
        if (info.lastModified() != null) {
            output.append("Modified: ").append(info.lastModified()).append('\n');
        }
        if (info.eTag() != null && !info.eTag().isBlank()) {
            output.append("ETag: ").append(info.eTag()).append('\n');
        }
        return ToolResult.success(output.toString().trim(), data);
    }

    private ToolResult buildTextReadResult(S3ObjectContent content) {
        String text = decodeText(content.bytes(), content.contentType());
        boolean truncated = text.length() > getConfig().getMaxInlineTextChars();
        String visibleText = truncated ? text.substring(0, getConfig().getMaxInlineTextChars()) : text;
        Map<String, Object> data = baseReadData(content, true);
        data.put("content", visibleText);
        data.put("truncated", truncated);
        if (truncated) {
            data.put("originalLength", text.length());
            data.put("attachment", buildAttachment(content, content.bytes()));
        }
        String output = truncated
                ? visibleText + "\n\n[TRUNCATED: " + text.length() + " chars total. Full object attached.]"
                : visibleText;
        return ToolResult.success(output, data);
    }

    private ToolResult buildBinaryReadResult(S3ObjectContent content, long size) {
        Map<String, Object> data = baseReadData(content, false);
        Attachment attachment = buildAttachment(content, content.bytes());
        data.put("attachment", attachment);
        data.put("filename", attachment.getFilename());
        StringBuilder output = new StringBuilder();
        output.append("Read binary object: ").append(content.key()).append('\n');
        output.append("Size: ").append(formatSize(size)).append('\n');
        if (content.contentType() != null && !content.contentType().isBlank()) {
            output.append("Content-Type: ").append(content.contentType());
        }
        return ToolResult.success(output.toString().trim(), data);
    }

    private Map<String, Object> baseReadData(S3ObjectContent content, boolean textFile) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("bucket", content.bucket());
        data.put("path", content.key());
        data.put("size", content.bytes().length);
        data.put("etag", content.eTag());
        data.put("content_type", content.contentType());
        data.put("modified", content.lastModified() != null ? content.lastModified().toString() : null);
        data.put("is_text", textFile);
        return data;
    }

    private Attachment buildAttachment(S3ObjectContent content, byte[] bytes) {
        String mimeType = content.contentType() != null && !content.contentType().isBlank()
                ? stripMimeParameters(content.contentType())
                : S3MimeTypes.detect(content.key());
        Attachment.Type attachmentType = mimeType.startsWith("image/")
                ? Attachment.Type.IMAGE
                : Attachment.Type.DOCUMENT;
        return Attachment.builder()
                .type(attachmentType)
                .data(bytes)
                .filename(leafName(content.key()))
                .mimeType(mimeType)
                .caption("S3 object: " + content.key())
                .build();
    }

    private void ensureTargetAbsent(String normalizedTargetPath) {
        if (client.findObject(normalizedTargetPath) != null || client.directoryExists(normalizedTargetPath)) {
            throw new IllegalArgumentException("Target already exists: " + normalizedTargetPath);
        }
    }

    private S3ObjectInfo requireObject(String normalizedPath) {
        S3ObjectInfo object = client.findObject(normalizedPath);
        if (object == null) {
            if (client.directoryExists(normalizedPath)) {
                throw new IllegalArgumentException("Not a file: " + normalizedPath);
            }
            throw new IllegalArgumentException("File not found: " + normalizedPath);
        }
        return object;
    }

    private void copyDirectory(String sourcePath, String targetPath) {
        if (!client.directoryExists(sourcePath)) {
            throw new IllegalArgumentException("Directory not found: " + sourcePath);
        }
        List<S3ObjectInfo> entries = collectDirectoryEntries(sourcePath);
        for (S3ObjectInfo entry : entries) {
            String suffix = relativeSuffix(sourcePath, entry.key());
            String destinationPath = suffix.isBlank() ? targetPath : targetPath + "/" + suffix;
            if (entry.directory()) {
                client.createDirectoryMarker(destinationPath);
            } else {
                client.copyObject(entry.key(), destinationPath);
            }
        }
    }

    private void deleteDirectory(String path) {
        List<S3ObjectInfo> entries = collectDirectoryEntries(path);
        if (entries.isEmpty()) {
            throw new IllegalArgumentException("Directory not found: " + path);
        }
        for (S3ObjectInfo entry : entries) {
            if (entry.directory()) {
                client.deleteDirectoryMarker(entry.key());
            } else {
                client.deleteObject(entry.key());
            }
        }
    }

    private List<S3ObjectInfo> collectDirectoryEntries(String path) {
        Set<String> seen = new LinkedHashSet<>();
        List<S3ObjectInfo> collected = new ArrayList<>();
        S3ObjectInfo marker = client.findDirectoryMarker(path);
        if (marker != null && seen.add(marker.key() + "/dir")) {
            collected.add(marker);
        }
        for (S3ObjectInfo entry : client.listTree(path)) {
            String identity = entry.key() + (entry.directory() ? "/dir" : "/file");
            if (seen.add(identity)) {
                collected.add(entry);
            }
        }
        collected.sort((left, right) -> Integer.compare(right.key().length(), left.key().length()));
        return List.copyOf(collected);
    }

    private String relativeSuffix(String sourcePath, String entryPath) {
        if (entryPath.equals(sourcePath)) {
            return "";
        }
        if (entryPath.startsWith(sourcePath + "/")) {
            return entryPath.substring(sourcePath.length() + 1);
        }
        throw new IllegalArgumentException("Entry path " + entryPath + " is not under " + sourcePath);
    }

    private byte[] appendToExistingContent(String normalizedPath, byte[] payload) {
        try {
            S3ObjectInfo existing = requireObject(normalizedPath);
            long size = existing.size() != null ? existing.size() : 0L;
            if (size > getConfig().getMaxDownloadBytes()) {
                throw new IllegalArgumentException("Existing object is too large to append safely: " + normalizedPath);
            }
            S3ObjectContent existingContent = client.readObject(normalizedPath);
            byte[] combined = new byte[existingContent.bytes().length + payload.length];
            System.arraycopy(existingContent.bytes(), 0, combined, 0, existingContent.bytes().length);
            System.arraycopy(payload, 0, combined, existingContent.bytes().length, payload.length);
            return combined;
        } catch (IllegalArgumentException ex) {
            if (ex.getMessage() != null && ex.getMessage().startsWith("File not found:")) {
                return payload;
            }
            throw ex;
        }
    }

    private byte[] resolveWritePayload(String content, String contentBase64) {
        if (content != null && !content.isBlank() && contentBase64 != null && !contentBase64.isBlank()) {
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

    private String resolveContentType(String normalizedPath, String content, String contentBase64) {
        if (contentBase64 != null && !contentBase64.isBlank()) {
            return S3MimeTypes.detect(normalizedPath);
        }
        if (content != null) {
            return S3MimeTypes.detect(normalizedPath);
        }
        return "application/octet-stream";
    }

    private ToolResult executionFailure(String message) {
        return ToolResult.failure(ToolFailureKind.EXECUTION_FAILED, message);
    }

    private boolean isTextFile(String path, String contentType, byte[] bytes) {
        String normalizedMimeType = stripMimeParameters(contentType).toLowerCase(Locale.ROOT);
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

    private String decodeText(byte[] bytes, String contentType) {
        Charset charset = StandardCharsets.UTF_8;
        String lowerMimeType = contentType != null ? contentType.toLowerCase(Locale.ROOT) : "";
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

    private Map<String, Object> toObjectInfo(S3ObjectInfo info) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("bucket", info.bucket());
        data.put("path", info.key());
        data.put("name", info.name());
        data.put("type", info.directory() ? "directory" : "file");
        data.put("size", info.size());
        data.put("etag", info.eTag());
        data.put("content_type", info.contentType());
        data.put("modified", info.lastModified() != null ? info.lastModified().toString() : null);
        return data;
    }

    private String displayPath(String normalizedPath) {
        return normalizedPath == null || normalizedPath.isBlank() ? "/" : normalizedPath;
    }

    private String leafName(String path) {
        String normalized = path;
        if (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        int separatorIndex = normalized.lastIndexOf('/');
        return separatorIndex >= 0 ? normalized.substring(separatorIndex + 1) : normalized;
    }

    private String fileExtension(String path) {
        String currentName = leafName(path).toLowerCase(Locale.ROOT);
        int dotIndex = currentName.lastIndexOf('.');
        return dotIndex >= 0 ? currentName.substring(dotIndex + 1) : "";
    }

    private String stripMimeParameters(String contentType) {
        if (contentType == null) {
            return "";
        }
        int separatorIndex = contentType.indexOf(';');
        return separatorIndex >= 0 ? contentType.substring(0, separatorIndex).trim() : contentType.trim();
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
}
