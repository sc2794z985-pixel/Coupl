package com.auditlab.mod.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Loads and saves {@link AuditLabConfig} as pretty-printed JSON. */
public final class ConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private final Path file;
    private volatile AuditLabConfig config = new AuditLabConfig().normalize();
    private volatile String lastError;

    public ConfigManager(Path file) {
        this.file = file;
    }

    public AuditLabConfig get() {
        return config;
    }

    public Path file() {
        return file;
    }

    /** Message of the last load failure, or {@code null}. */
    public String lastError() {
        return lastError;
    }

    /**
     * Loads the file, creating it with defaults when missing. A malformed file is left untouched
     * and defaults are used, so a typo never destroys the user's settings.
     */
    public AuditLabConfig load() {
        lastError = null;
        if (!Files.exists(file)) {
            config = new AuditLabConfig().normalize();
            save();
            return config;
        }
        try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            AuditLabConfig loaded = GSON.fromJson(r, AuditLabConfig.class);
            config = (loaded == null ? new AuditLabConfig() : loaded).normalize();
        } catch (IOException | JsonParseException e) {
            lastError = e.getMessage();
            config = new AuditLabConfig().normalize();
        }
        return config;
    }

    public void save() {
        try {
            Files.createDirectories(file.toAbsolutePath().getParent());
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            try (Writer w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                GSON.toJson(config, w);
            }
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            lastError = e.getMessage();
        }
    }

    public void set(AuditLabConfig newConfig) {
        config = newConfig.normalize();
    }
}
