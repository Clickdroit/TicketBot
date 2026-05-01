package fr.sakura.bot.core.model;

public record TempRoleEntry(long id, String guildId, String userId, String roleId, long expiryTime) {
    public TempRoleEntry(String guildId, String userId, String roleId, long expiryTime) {
        this(0, guildId, userId, roleId, expiryTime);
    }
}
