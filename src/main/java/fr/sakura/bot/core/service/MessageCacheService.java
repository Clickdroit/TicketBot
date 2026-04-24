package fr.sakura.bot.core.service;

import net.dv8tion.jda.api.entities.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Service gérant le cache des messages récents pour la détection des suppressions et ghost pings.
 * Inclut une purge automatique périodique.
 */
public class MessageCacheService {

    private static final Logger logger = LoggerFactory.getLogger(MessageCacheService.class);
    private static final int MAX_CACHE_SIZE = 10000;
    private static final long CACHE_DURATION_MS = TimeUnit.HOURS.toMillis(1);

    private final Map<String, CachedMessage> cache = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "message-cache-cleaner");
        t.setDaemon(true);
        return t;
    });

    public MessageCacheService() {
        // Purge toutes les 10 minutes
        scheduler.scheduleAtFixedRate(this::purgeExpired, 10, 10, TimeUnit.MINUTES);
    }

    public void put(Message message) {
        if (message.getAuthor().isBot()) return;

        if (cache.size() >= MAX_CACHE_SIZE) {
            purgeExpired();
        }
        cache.put(message.getId(), new CachedMessage(message));
    }

    public CachedMessage get(String messageId) {
        return cache.get(messageId);
    }

    public CachedMessage remove(String messageId) {
        return cache.remove(messageId);
    }

    public void purgeExpired() {
        long now = System.currentTimeMillis();
        int before = cache.size();
        cache.entrySet().removeIf(entry -> now - entry.getValue().timestamp > CACHE_DURATION_MS);
        int after = cache.size();
        if (before != after) {
            logger.debug("Purge du cache messages : {} entrées retirées", before - after);
        }
    }

    public void shutdown() {
        scheduler.shutdown();
    }

    public static class CachedMessage {
        public String content;
        public final String authorId;
        public final String authorName;
        public final String authorAvatar;
        public final String channelId;
        public final String channelName;
        public final Set<String> mentionedUserIds;
        public final List<CachedAttachment> attachments;
        public final long timestamp;

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

    public static class CachedAttachment {
        public final String url;
        public final String proxyUrl;
        public final String fileName;
        public final String contentType;
        public final long size;
        public final boolean isImage;

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
}
