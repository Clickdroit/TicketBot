package fr.sakura.bot.core.model;

/**
 * Représente un bannissement temporaire en base de données.
 * 
 * @param guildId Identifiant de la guilde
 * @param userId Identifiant de l'utilisateur banni
 * @param unbanTime Timestamp (ms) du débannissement
 * @param reason Motif du bannissement
 */
public record TempBanEntry(
    String guildId,
    String userId,
    long unbanTime,
    String reason
) {}
