package fr.sakura.bot.core.service;

import fr.sakura.bot.core.model.WarningEntry;
import fr.sakura.bot.core.store.WarningStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Service métier pour manipuler les warnings.
 */
public class WarningService {

    private static final Logger logger = LoggerFactory.getLogger(WarningService.class);

    private final WarningStore warningStore;

    public WarningService(WarningStore warningStore) {
        this.warningStore = warningStore;
        logger.info("WarningService prêt");
    }

    public int addWarning(String guildId, String userId, String moderatorId, String reason) {
        WarningEntry warningEntry = new WarningEntry(moderatorId, reason, OffsetDateTime.now().toString());
        int total = warningStore.addWarning(guildId, userId, warningEntry);
        logger.info("addWarning: guildId={}, userId={}, moderatorId={}, total={}", guildId, userId, moderatorId, total);
        return total;
    }

    public List<WarningEntry> getWarnings(String guildId, String userId) {
        List<WarningEntry> warnings = warningStore.getWarnings(guildId, userId);
        logger.debug("getWarnings: guildId={}, userId={}, total={}", guildId, userId, warnings.size());
        return warnings;
    }

    public int clearWarnings(String guildId, String userId) {
        int removed = warningStore.clearWarnings(guildId, userId);
        logger.info("clearWarnings: guildId={}, userId={}, removed={}", guildId, userId, removed);
        return removed;
    }
}
