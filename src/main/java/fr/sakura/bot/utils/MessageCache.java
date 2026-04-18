package fr.sakura.bot.utils;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Cache LRU en mémoire des derniers messages reçus par guild.
 *
 * <p>Discord ne fournit pas le contenu dans {@code MessageDeleteEvent} ni
 * l'ancien contenu dans {@code MessageUpdateEvent} — seul l'ID est garanti.
 * Ce cache stocke chaque message à la réception et permet de retrouver :</p>
 * <ul>
 *   <li>le contenu au moment de la suppression ;</li>
 *   <li>le contenu <em>avant</em> modification (via {@link #getContent})
 *       puis mise à jour via {@link #updateContent}.</li>
 * </ul>
 */
public class MessageCache {

    private static final int MAX_PER_GUILD = 500;
    private static final int MAX_CONTENT   = 1000;

    /**
     * Entrée de cache représentant un message.
     *
     * @param authorId   ID Discord de l'auteur
     * @param authorTag  Nom d'utilisateur (ex : {@code clickdroit})
     * @param channelId  ID du salon
     * @param content    Contenu du message (tronqué à {@value MAX_CONTENT} caractères)
     */
    public record CachedMessage(String authorId, String authorTag, String channelId, String content) {}

    /** guildId → (messageId → CachedMessage) */
    private final Map<String, LinkedHashMap<String, CachedMessage>> cache = new LinkedHashMap<>();

    // ── Écriture ─────────────────────────────────────────────────────────────────

    /** Stocke un nouveau message dans le cache. */
    public synchronized void put(String guildId, String messageId,
                                 String authorId, String authorTag,
                                 String channelId, String content) {
        LinkedHashMap<String, CachedMessage> guildCache = getOrCreateGuildCache(guildId);
        guildCache.put(messageId, new CachedMessage(authorId, authorTag, channelId, truncate(content)));
    }

    /**
     * Met à jour le contenu d'un message déjà en cache (après édition).
     * Si le message n'est pas en cache, cette méthode ne fait rien.
     *
     * @param guildId    ID de la guild
     * @param messageId  ID du message à mettre à jour
     * @param newContent Nouveau contenu à stocker
     */
    public synchronized void updateContent(String guildId, String messageId, String newContent) {
        LinkedHashMap<String, CachedMessage> guildCache = cache.get(guildId);
        if (guildCache == null) return;

        CachedMessage existing = guildCache.get(messageId);
        if (existing == null) return;

        guildCache.put(messageId, new CachedMessage(
                existing.authorId(),
                existing.authorTag(),
                existing.channelId(),
                truncate(newContent)
        ));
    }

    // ── Lecture ──────────────────────────────────────────────────────────────────

    /**
     * Récupère un message sans le retirer du cache.
     * Utile pour lire le contenu "avant" lors d'une édition.
     *
     * @return le message en cache, ou {@code null} s'il est introuvable
     */
    public synchronized CachedMessage getContent(String guildId, String messageId) {
        LinkedHashMap<String, CachedMessage> guildCache = cache.get(guildId);
        if (guildCache == null) return null;
        return guildCache.get(messageId);
    }

    /**
     * Récupère et <em>supprime</em> un message du cache (consommation unique).
     * À utiliser lors d'une suppression de message.
     *
     * @return le message en cache, ou {@code null} s'il est introuvable
     */
    public synchronized CachedMessage remove(String guildId, String messageId) {
        LinkedHashMap<String, CachedMessage> guildCache = cache.get(guildId);
        if (guildCache == null) return null;
        return guildCache.remove(messageId);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────

    private LinkedHashMap<String, CachedMessage> getOrCreateGuildCache(String guildId) {
        return cache.computeIfAbsent(guildId, k -> new LinkedHashMap<>(MAX_PER_GUILD, 0.75f, false) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, CachedMessage> eldest) {
                return size() > MAX_PER_GUILD;
            }
        });
    }

    private static String truncate(String content) {
        if (content == null) return "";
        return content.length() > MAX_CONTENT ? content.substring(0, MAX_CONTENT) + "…" : content;
    }
}