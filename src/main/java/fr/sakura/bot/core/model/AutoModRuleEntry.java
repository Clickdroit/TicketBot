package fr.sakura.bot.core.model;

public record AutoModRuleEntry(long id, String guildId, String type, String pattern, String action, String createdAt) {
    public AutoModRuleEntry(String guildId, String type, String pattern, String action) {
        this(0, guildId, type, pattern, action, java.time.Instant.now().toString());
    }
}
