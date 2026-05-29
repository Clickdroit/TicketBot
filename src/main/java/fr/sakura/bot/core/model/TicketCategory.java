package fr.sakura.bot.core.model;

/**
 * Représente une catégorie de ticket personnalisable.
 */
public record TicketCategory(
        String categoryId,
        String label,
        String description,
        String emoji
) {
}
