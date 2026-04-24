package fr.sakura.bot.core.model;

/**
 * Entree de warning stockee en JSON ou BDD.
 */
public record WarningEntry(String moderatorId, String reason, String timestamp) {
}

