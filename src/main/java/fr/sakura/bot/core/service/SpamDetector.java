package fr.sakura.bot.core.service;

import fr.sakura.bot.database.SettingsManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service détectant le spam en fonction de la fréquence des messages.
 */
public class SpamDetector {

    private static final Logger logger = LoggerFactory.getLogger(SpamDetector.class);

    private final Map<String, UserSpamData> spamTracker = new ConcurrentHashMap<>();

    private record UserSpamData(long lastMessageTime, int messageCount, int strikes, long lastStrikeTime) {}

    /**
     * Vérifie si un message constitue du spam.
     * @return true si le spam est détecté (limite dépassée dans la fenêtre).
     */
    public boolean check(String guildId, String userId, SettingsManager settings) {
        long now = System.currentTimeMillis();
        
        long windowMs = settings.getSpamWindowMs(guildId);
        int limit = settings.getSpamLimit(guildId);
        int strikeResetMs = settings.getAutomodStrikeResetMinutes(guildId) * 60 * 1000;

        UserSpamData data = spamTracker.getOrDefault(userId, new UserSpamData(now, 0, 0, 0));
        
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
        
        spamTracker.put(userId, data);
        return isSpamming;
    }

    public int getStrikes(String userId) {
        UserSpamData data = spamTracker.get(userId);
        return data != null ? data.strikes() : 0;
    }

    public void resetStrikes(String userId) {
        UserSpamData data = spamTracker.get(userId);
        if (data != null) {
            spamTracker.put(userId, new UserSpamData(data.lastMessageTime(), data.messageCount(), 0, 0));
        }
    }
}
