package fr.sakura.bot.listeners.log;

import fr.sakura.bot.core.service.MessageCacheService;
import fr.sakura.bot.database.SettingsManager;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.audit.ActionType;
import net.dv8tion.jda.api.audit.AuditLogEntry;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Classe de base pour tous les listeners de logs.
 * Fournit les méthodes utilitaires partagées : envoi de logs, audit logs.
 */
public abstract class BaseLogListener extends ListenerAdapter {

    protected static final Logger logger = LoggerFactory.getLogger(BaseLogListener.class);

    protected final SettingsManager settingsManager;
    protected final MessageCacheService messageCacheService;

    // Cache des audit logs pour éviter les requêtes répétées
    protected final Map<String, List<CachedAuditEntry>> auditLogCache = new ConcurrentHashMap<>();
    protected static final long AUDIT_CACHE_DURATION_MS = 5000; // 5 secondes
    protected static final int MAX_AUDIT_CACHE_ENTRIES = 10;

    // Fenêtres temporelles pour l'audit log (en millisecondes)
    protected static final long AUDIT_TIMING_STANDARD = 5000;
    protected static final long RETRY_DELAY_MS = 1000;

    public BaseLogListener(SettingsManager settingsManager, MessageCacheService messageCacheService) {
        this.settingsManager = settingsManager;
        this.messageCacheService = messageCacheService;
    }

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
     * Envoie un embed de log dans le salon configuré
     */
    protected void sendLogToChannel(Guild guild, java.util.function.Consumer<EmbedBuilder> embedConsumer) {
        if (settingsManager == null) return;

        String guildId = guild.getId();
        settingsManager.getLogChannelId(guildId).ifPresent(channelId -> {
            TextChannel logChannel = guild.getTextChannelById(channelId);
            if (logChannel != null) {
                EmbedBuilder embed = new EmbedBuilder();
                embedConsumer.accept(embed);
                logChannel.sendMessageEmbeds(embed.build()).queue(
                        success -> {},
                        error -> logger.error("Erreur lors de l'envoi du log (guildId={}): {}", guildId, error.getMessage())
                );
            }
        });
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
