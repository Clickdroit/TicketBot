package fr.sakura.bot.core.model;

/**
 * Représente un panel de ticket enregistré en base de données pour l'actualisation dynamique.
 */
public record PanelEntry(
        String channelId,
        String messageId
) {
}
