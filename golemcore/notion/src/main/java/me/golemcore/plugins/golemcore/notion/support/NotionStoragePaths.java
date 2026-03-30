package me.golemcore.plugins.golemcore.notion.support;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Component
public class NotionStoragePaths {

    static final String STORAGE_DIR_PROPERTY = "golemcore.notion.storageDir";

    private final Path storageRoot;

    public NotionStoragePaths() {
        this(defaultStorageRoot());
    }

    public NotionStoragePaths(Path storageRoot) {
        this.storageRoot = storageRoot;
    }

    public Path indexDatabasePath() {
        return storageRoot.resolve("notion-index.sqlite");
    }

    public void ensureStorageDirectories() {
        try {
            Files.createDirectories(storageRoot);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to prepare Notion plugin storage: " + ex.getMessage(), ex);
        }
    }

    private static Path defaultStorageRoot() {
        String override = System.getProperty(STORAGE_DIR_PROPERTY);
        if (override != null && !override.isBlank()) {
            return Paths.get(override);
        }
        return Paths.get(System.getProperty("user.home"), ".golemcore", "plugins", "golemcore-notion");
    }
}
