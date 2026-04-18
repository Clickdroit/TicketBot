package fr.sakura.bot.utils;

/**
 * Entree de warning stockee en JSON.
 */
public class WarningEntry {

    private String moderatorId;
    private String reason;
    private String timestamp;

    public WarningEntry() {
        // Constructeur vide requis pour Gson.
    }

    public WarningEntry(String moderatorId, String reason, String timestamp) {
        this.moderatorId = moderatorId;
        this.reason = reason;
        this.timestamp = timestamp;
    }

    public String getModeratorId() {
        return moderatorId;
    }

    public String getReason() {
        return reason;
    }

    public String getTimestamp() {
        return timestamp;
    }
}

