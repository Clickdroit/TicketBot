package fr.sakura.bot.core.model;

/**
 * Représente une note interne de staff sur un membre.
 * 
 * @param id Identifiant de la note
 * @param guildId Identifiant de la guilde
 * @param userId Identifiant de l'utilisateur concerné
 * @param authorId Identifiant du staff ayant écrit la note
 * @param content Contenu de la note
 * @param createdAt Date de création (format texte ISO)
 */
public record StaffNoteEntry(
    long id,
    String guildId,
    String userId,
    String authorId,
    String content,
    String createdAt
) {}
