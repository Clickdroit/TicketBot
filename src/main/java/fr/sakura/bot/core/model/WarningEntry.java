package fr.sakura.bot.core.model;

/**
 * Entree de warning stockee en JSON ou BDD.
 */
public record WarningEntry(long id, String moderatorId, String reason, String timestamp) {
    public WarningEntry(String moderatorId, String reason, String timestamp) {
        this(0, moderatorId, reason, timestamp);
    }
}

