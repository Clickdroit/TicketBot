package fr.sakura.bot.core.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import fr.sakura.bot.database.SettingsManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Service détectant le spam en fonction de la fréquence des messages.
 * Utilise Caffeine pour éviter les fuites de mémoire.
 */
public class SpamDetector {

    private static final Logger logger = LoggerFactory.getLogger(SpamDetector.class);

    private final Cache<String, UserSpamData> spamTracker = Caffeine.newBuilder()
            .expireAfterAccess(30, TimeUnit.MINUTES) // TTL de 30 min après la dernière activité
            .maximumSize(10_000)
            .build();

    private final Cache<String, Integer> channelCounter = Caffeine.newBuilder()
            .expireAfterWrite(5, TimeUnit.SECONDS) // Fenêtre glissante de 5s pour le slowmode auto
            .build();

    private final Map<String, Long> activeSlowmodes = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    private record UserSpamData(long lastMessageTime, int messageCount, int strikes, long lastStrikeTime) {}

    /**
     * Vérifie si un message constitue du spam.
     * @return true si le spam est détecté (limite dépassée dans la fenêtre).
     */
    public boolean check(net.dv8tion.jda.api.events.message.MessageReceivedEvent event, SettingsManager settings) {
        String guildId = event.getGuild().getId();
        String userId = event.getAuthor().getId();
        String channelId = event.getChannel().getId();
        long now = System.currentTimeMillis();
        
        long windowMs = settings.getSpamWindowMs(guildId);
        int limit = settings.getSpamLimit(guildId);
        int strikeResetMs = settings.getAutomodStrikeResetMinutes(guildId) * 60 * 1000;

        // ── Slowmode automatique ──
        if (settings.isAutoSlowmodeEnabled(guildId)) {
            int channelTotal = channelCounter.get(channelId, k -> 0) + 1;
            channelCounter.put(channelId, channelTotal);

            if (channelTotal >= settings.getAutoSlowmodeThreshold(guildId)) {
                triggerAutoSlowmode(event.getGuild(), (net.dv8tion.jda.api.entities.channel.concrete.TextChannel) event.getChannel(), settings);
            }
        }

        String key = guildId + ":" + userId;
        UserSpamData data = spamTracker.getIfPresent(key);
        if (data == null) {
            data = new UserSpamData(now, 0, 0, 0);
        }
        
        // Reset des strikes si assez de temps s'est écoulé
        int currentStrikes = data.strikes();
        if (data.lastStrikeTime() > 0 && now - data.lastStrikeTime() > strikeResetMs) {
            currentStrikes = 0;
        }

        int currentCount = data.messageCount();
        if (now - data.lastMessageTime() < windowMs) {
            currentCount++;
        } else {
            currentCount = 1;
        }

        boolean isSpamming = false;
        if (currentCount > limit) {
            currentCount = 0; // Reset pour éviter les déclenchements en rafale
            currentStrikes++;
            isSpamming = true;
            data = new UserSpamData(now, currentCount, currentStrikes, now);
        } else {
            data = new UserSpamData(now, currentCount, currentStrikes, data.lastStrikeTime());
        }
        
        spamTracker.put(key, data);
        return isSpamming;
    }

    private void triggerAutoSlowmode(net.dv8tion.jda.api.entities.Guild guild, net.dv8tion.jda.api.entities.channel.concrete.TextChannel channel, SettingsManager settings) {
        String channelId = channel.getId();
        if (activeSlowmodes.containsKey(channelId)) return;

        int duration = settings.getAutoSlowmodeDuration(guild.getId());
        activeSlowmodes.put(channelId, System.currentTimeMillis());

        channel.getManager().setSlowmode(duration).reason("Sakura Auto-Slowmode: spam détecté").queue(
                success -> {
                    channel.sendMessage("🛡️ **Slowmode automatique activé** (" + duration + "s) dû à une forte activité.").queue();
                    logger.info("Auto-Slowmode activé sur {} ({})", channel.getName(), channelId);
                    
                    // Programmer le retrait après 2 minutes de calme (ou fixe pour simplifier ici)
                    scheduler.schedule(() -> removeAutoSlowmode(channel), 2, TimeUnit.MINUTES);
                }
        );
    }

    private void removeAutoSlowmode(net.dv8tion.jda.api.entities.channel.concrete.TextChannel channel) {
        activeSlowmodes.remove(channel.getId());
        channel.getManager().setSlowmode(0).reason("Sakura Auto-Slowmode: fin de l'alerte").queue(
                success -> logger.info("Auto-Slowmode désactivé sur {}", channel.getName())
        );
    }

    public void shutdown() {
        scheduler.shutdown();
    }

    public int getStrikes(String guildId, String userId) {
        UserSpamData data = spamTracker.getIfPresent(guildId + ":" + userId);
        return data != null ? data.strikes() : 0;
    }

    public void resetStrikes(String guildId, String userId) {
        String key = guildId + ":" + userId;
        UserSpamData data = spamTracker.getIfPresent(key);
        if (data != null) {
            spamTracker.put(key, new UserSpamData(data.lastMessageTime(), data.messageCount(), 0, 0));
        }
    }
}
