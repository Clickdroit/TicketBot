package fr.sakura.bot.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Service metier pour manipuler les warnings.
 */
public class WarningService {

    private static final Logger logger = LoggerFactory.getLogger(WarningService.class);

    private final WarningStore warningStore;

    public WarningService(String warningsFilePath) {
        this.warningStore = new WarningStore(warningsFilePath);
        logger.info("WarningService pret (stockage JSON)");
    }

    public int addWarning(String guildId, String userId, String moderatorId, String reason) throws IOException {
        WarningEntry warningEntry = new WarningEntry(moderatorId, reason, OffsetDateTime.now().toString());
        int total = warningStore.addWarning(guildId, userId, warningEntry);
        logger.info("addWarning: guildId={}, userId={}, moderatorId={}, total={}", guildId, userId, moderatorId, total);
        return total;
    }

    public List<WarningEntry> getWarnings(String guildId, String userId) throws IOException {
        List<WarningEntry> warnings = warningStore.getWarnings(guildId, userId);
        logger.debug("getWarnings: guildId={}, userId={}, total={}", guildId, userId, warnings.size());
        return warnings;
    }

    public int clearWarnings(String guildId, String userId) throws IOException {
        int removed = warningStore.clearWarnings(guildId, userId);
        logger.info("clearWarnings: guildId={}, userId={}, removed={}", guildId, userId, removed);
        return removed;
    }
}

