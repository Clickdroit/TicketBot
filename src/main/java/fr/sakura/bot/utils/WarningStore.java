package fr.sakura.bot.utils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Stockage JSON local des warnings.
 */
public class WarningStore {

    private static final Logger logger = LoggerFactory.getLogger(WarningStore.class);

    private static final String DEFAULT_FILE = "data/warnings.json";

    private final Path filePath;
    private final Gson gson;

    public WarningStore(String warningsFilePath) {
        String resolvedPath = (warningsFilePath == null || warningsFilePath.isEmpty())
                ? DEFAULT_FILE
                : warningsFilePath;
        this.filePath = Path.of(resolvedPath);
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        logger.info("WarningStore initialise sur {}", this.filePath.toAbsolutePath());
    }

    public synchronized List<WarningEntry> getWarnings(String guildId, String userId) throws IOException {
        WarningPayload payload = loadPayload();
        Map<String, List<WarningEntry>> guildWarnings = payload.guildWarnings.get(guildId);
        if (guildWarnings == null) {
            logger.debug("Aucun warning trouve pour guildId={}", guildId);
            return List.of();
        }

        List<WarningEntry> warnings = guildWarnings.get(userId);
        if (warnings == null) {
            logger.debug("Aucun warning trouve pour guildId={}, userId={}", guildId, userId);
            return List.of();
        }

        logger.debug("Warnings charges: guildId={}, userId={}, total={}", guildId, userId, warnings.size());
        return new ArrayList<>(warnings);
    }

    public synchronized int addWarning(String guildId, String userId, WarningEntry warningEntry) throws IOException {
        WarningPayload payload = loadPayload();

        Map<String, List<WarningEntry>> guildWarnings = payload.guildWarnings.computeIfAbsent(guildId, id -> new HashMap<>());
        List<WarningEntry> userWarnings = guildWarnings.computeIfAbsent(userId, id -> new ArrayList<>());
        userWarnings.add(warningEntry);

        savePayload(payload);
        logger.info("Warning ajoute: guildId={}, userId={}, total={}", guildId, userId, userWarnings.size());
        return userWarnings.size();
    }

    public synchronized int clearWarnings(String guildId, String userId) throws IOException {
        WarningPayload payload = loadPayload();

        Map<String, List<WarningEntry>> guildWarnings = payload.guildWarnings.get(guildId);
        if (guildWarnings == null) {
            logger.debug("clearWarnings ignore: guildId={} absent", guildId);
            return 0;
        }

        List<WarningEntry> removed = guildWarnings.remove(userId);
        if (removed == null) {
            logger.debug("clearWarnings ignore: aucun warning pour guildId={}, userId={}", guildId, userId);
            return 0;
        }

        if (guildWarnings.isEmpty()) {
            payload.guildWarnings.remove(guildId);
        }

        savePayload(payload);
        logger.info("Warnings supprimes: guildId={}, userId={}, removed={}", guildId, userId, removed.size());
        return removed.size();
    }

    private WarningPayload loadPayload() throws IOException {
        if (!Files.exists(filePath)) {
            logger.debug("Fichier warnings absent, initialisation vide: {}", filePath.toAbsolutePath());
            return new WarningPayload();
        }

        String rawJson = Files.readString(filePath);
        if (rawJson == null || rawJson.isBlank()) {
            logger.debug("Fichier warnings vide: {}", filePath.toAbsolutePath());
            return new WarningPayload();
        }

        try {
            WarningPayload payload = gson.fromJson(rawJson, WarningPayload.class);
            if (payload == null || payload.guildWarnings == null) {
                logger.warn("Payload warnings invalide, reset en memoire: {}", filePath.toAbsolutePath());
                return new WarningPayload();
            }
            logger.debug("Payload warnings charge depuis {}", filePath.toAbsolutePath());
            return payload;
        } catch (JsonSyntaxException ex) {
            logger.error("JSON warnings corrompu, backup automatique en cours: {}", filePath.toAbsolutePath(), ex);
            backupCorruptedFile();
            return new WarningPayload();
        }
    }

    private void savePayload(WarningPayload payload) throws IOException {
        Path parent = filePath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        Path tempPath = filePath.resolveSibling(filePath.getFileName() + ".tmp");
        String json = gson.toJson(payload);
        Files.writeString(tempPath, json, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

        try {
            Files.move(tempPath, filePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(tempPath, filePath, StandardCopyOption.REPLACE_EXISTING);
        }

        logger.debug("Payload warnings ecrit sur {}", filePath.toAbsolutePath());
    }

    private void backupCorruptedFile() throws IOException {
        String fileName = filePath.getFileName().toString();
        Path backupPath = filePath.resolveSibling(fileName + ".corrupt-" + Instant.now().toEpochMilli());
        Files.move(filePath, backupPath, StandardCopyOption.REPLACE_EXISTING);
        logger.warn("Fichier warnings deplace vers backup: {}", backupPath.toAbsolutePath());
    }

    private static class WarningPayload {
        private Map<String, Map<String, List<WarningEntry>>> guildWarnings = new HashMap<>();
    }
}

