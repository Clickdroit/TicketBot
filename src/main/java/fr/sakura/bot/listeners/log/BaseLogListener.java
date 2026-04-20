package fr.sakura.bot.listeners.log;

import fr.sakura.bot.utils.EmbedStyle;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.audit.ActionType;
import net.dv8tion.jda.api.audit.AuditLogEntry;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Classe de base pour tous les listeners de logs (Style Sakura).
 * Fournit les méthodes utilitaires partagées: cache, envoi de logs, audit logs.
 */
public abstract class BaseLogListener extends ListenerAdapter {

    protected static final Logger logger = LoggerFactory.getLogger(BaseLogListener.class);

    private final String logChannelId;

    public BaseLogListener(String logChannelId) {
        this.logChannelId = logChannelId;
    }

    // Cache des messages récents pour détecter les suppressions et ghost pings
    protected static final Map<String, CachedMessage> messageCache = new ConcurrentHashMap<>();
    protected static final int MAX_CACHE_SIZE = 10000;
    protected static final long CACHE_DURATION_MS = 3600000; // 1 heure

    // Cache des audit logs pour éviter les requêtes répétées
    protected static final Map<String, List<CachedAuditEntry>> auditLogCache = new ConcurrentHashMap<>();
    protected static final long AUDIT_CACHE_DURATION_MS = 5000; // 5 secondes
    protected static final int MAX_AUDIT_CACHE_ENTRIES = 10;

    // Fenêtres temporelles pour l'audit log (en millisecondes)
    protected static final long AUDIT_TIMING_STANDARD = 5000;
    protected static final long RETRY_DELAY_MS = 1000;

    /**
     * Cache d'entrées d'audit log
     */
    protected static class CachedAuditEntry {
        public AuditLogEntry entry;
        public long timestamp;

        public CachedAuditEntry(AuditLogEntry entry) {
            this.entry = entry;
            this.timestamp = System.currentTimeMillis();
        }

        public boolean isExpired() {
            return System.currentTimeMillis() - timestamp > AUDIT_CACHE_DURATION_MS;
        }
    }

    /**
     * Classe pour mettre en cache les messages récents
     */
    protected static class CachedMessage {
        public String content;
        public String authorId;
        public String authorName;
        public String authorAvatar;
        public String channelId;
        public String channelName;
        public Set<String> mentionedUserIds;
        public List<CachedAttachment> attachments;
        public long timestamp;

        public CachedMessage(Message message) {
            this.content = message.getContentDisplay();
            this.authorId = message.getAuthor().getId();
            this.authorName = message.getAuthor().getName();
            this.authorAvatar = message.getAuthor().getEffectiveAvatarUrl();
            this.channelId = message.getChannel().getId();
            this.channelName = message.getChannel().getName();
            this.mentionedUserIds = new HashSet<>();
            message.getMentions().getUsers().forEach(user -> mentionedUserIds.add(user.getId()));
            this.attachments = new ArrayList<>();
            message.getAttachments().forEach(att -> attachments.add(new CachedAttachment(att)));
            this.timestamp = System.currentTimeMillis();
        }
    }

    /**
     * Classe pour mettre en cache les pièces jointes
     */
    protected static class CachedAttachment {
        public String url;
        public String proxyUrl;
        public String fileName;
        public String contentType;
        public long size;
        public boolean isImage;

        public CachedAttachment(Message.Attachment attachment) {
            this.url = attachment.getUrl();
            this.proxyUrl = attachment.getProxyUrl();
            this.fileName = attachment.getFileName();
            this.contentType = attachment.getContentType();
            this.size = attachment.getSize();
            this.isImage = attachment.isImage();
        }

        public String getEmoji() {
            if (isImage) return "🖼️";
            if (contentType != null && contentType.startsWith("video")) return "🎬";
            if (contentType != null && contentType.startsWith("audio")) return "🎵";
            return "📎";
        }

        public String getFormattedSize() {
            if (size < 1024) return size + " B";
            if (size < 1024 * 1024) return String.format("%.1f KB", size / 1024.0);
            return String.format("%.1f MB", size / (1024.0 * 1024.0));
        }
    }

    /**
     * Cache un message pour la détection ultérieure
     */
    public static void cacheMessageStatic(Message message) {
        if (message.getAuthor().isBot()) return;

        if (messageCache.size() > MAX_CACHE_SIZE) {
            long now = System.currentTimeMillis();
            messageCache.entrySet().removeIf(entry -> now - entry.getValue().timestamp > CACHE_DURATION_MS);
        }

        messageCache.put(message.getId(), new CachedMessage(message));
    }

    /**
     * Envoie un embed de log dans le salon configuré
     */
    protected void sendLogToChannel(Guild guild, java.util.function.Consumer<EmbedBuilder> embedConsumer) {
        if (logChannelId == null || logChannelId.isEmpty()) return;

        TextChannel logChannel = guild.getTextChannelById(logChannelId);
        if (logChannel != null) {
            EmbedBuilder embed = new EmbedBuilder();
            embedConsumer.accept(embed);
            logChannel.sendMessageEmbeds(embed.build()).queue(
                    success -> {},
                    error -> logger.error("Erreur lors de l'envoi du log: {}", error.getMessage())
            );
        }
    }

    /**
     * Trouve une action récente dans l'audit log
     */
    protected CompletableFuture<AuditLogEntry> findRecentAuditAction(
            Guild guild, ActionType actionType, String targetId, long maxDelay, int maxRetries) {
        CompletableFuture<AuditLogEntry> future = new CompletableFuture<>();
        String cacheKey = guild.getId() + "_" + actionType.name();

        // Vérifier le cache d'abord
        List<CachedAuditEntry> cached = auditLogCache.get(cacheKey);
        if (cached != null) {
            cached.removeIf(CachedAuditEntry::isExpired);
            for (CachedAuditEntry cachedEntry : cached) {
                AuditLogEntry entry = cachedEntry.entry;
                long timeDiff = System.currentTimeMillis() - entry.getTimeCreated().toInstant().toEpochMilli();
                String entryTargetId = entry.getTargetId();

                if (timeDiff < maxDelay && entryTargetId != null && entryTargetId.equals(targetId)
                        && entry.getUser() != null) {
                    future.complete(entry);
                    return future;
                }
            }
        }

        // Requête API
        guild.retrieveAuditLogs()
                .type(actionType)
                .limit(5)
                .queue(logs -> {
                    List<CachedAuditEntry> cacheList = new ArrayList<>();
                    for (AuditLogEntry entry : logs) {
                        cacheList.add(new CachedAuditEntry(entry));
                        if (cacheList.size() >= MAX_AUDIT_CACHE_ENTRIES)
                            break;
                    }
                    auditLogCache.put(cacheKey, cacheList);

                    AuditLogEntry matchingEntry = null;
                    for (AuditLogEntry entry : logs) {
                        long timeDiff = System.currentTimeMillis() - entry.getTimeCreated().toInstant().toEpochMilli();
                        String entryTargetId = entry.getTargetId();

                        if (timeDiff < maxDelay && entryTargetId != null && entryTargetId.equals(targetId)
                                && entry.getUser() != null) {
                            matchingEntry = entry;
                            break;
                        }
                    }

                    if (matchingEntry != null) {
                        future.complete(matchingEntry);
                    } else if (maxRetries > 0) {
                        guild.getJDA().getGatewayPool().schedule(
                                () -> retryFindAuditAction(guild, actionType, targetId, maxDelay, maxRetries - 1, future),
                                RETRY_DELAY_MS, TimeUnit.MILLISECONDS);
                    } else {
                        future.complete(null);
                    }
                }, error -> {
                    if (maxRetries > 0) {
                        guild.getJDA().getGatewayPool().schedule(
                                () -> retryFindAuditAction(guild, actionType, targetId, maxDelay, maxRetries - 1, future),
                                RETRY_DELAY_MS, TimeUnit.MILLISECONDS);
                    } else {
                        future.complete(null);
                    }
                });

        return future;
    }

    private void retryFindAuditAction(Guild guild, ActionType actionType, String targetId, long maxDelay,
            int maxRetries, CompletableFuture<AuditLogEntry> future) {
        guild.retrieveAuditLogs()
                .type(actionType)
                .limit(5)
                .queue(logs -> {
                    AuditLogEntry matchingEntry = null;
                    for (AuditLogEntry entry : logs) {
                        long timeDiff = System.currentTimeMillis() - entry.getTimeCreated().toInstant().toEpochMilli();
                        String entryTargetId = entry.getTargetId();

                        if (timeDiff < maxDelay + (RETRY_DELAY_MS * (5 - maxRetries)) && entryTargetId != null &&
                                entryTargetId.equals(targetId) && entry.getUser() != null) {
                            matchingEntry = entry;
                            break;
                        }
                    }

                    if (matchingEntry != null) {
                        future.complete(matchingEntry);
                    } else if (maxRetries > 0) {
                        guild.getJDA().getGatewayPool().schedule(
                                () -> retryFindAuditAction(guild, actionType, targetId, maxDelay, maxRetries - 1, future),
                                RETRY_DELAY_MS, TimeUnit.MILLISECONDS);
                    } else {
                        future.complete(null);
                    }
                }, error -> future.complete(null));
    }
}
