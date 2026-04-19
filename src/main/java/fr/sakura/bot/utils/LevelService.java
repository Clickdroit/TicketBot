package fr.sakura.bot.utils;

import fr.sakura.bot.database.SettingsManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public class LevelService {

    private static final Logger logger = LoggerFactory.getLogger(LevelService.class);
    private static final long DEFAULT_COOLDOWN_MS = 60_000L;
    private static final int DEFAULT_MIN_MESSAGE_LENGTH = 5;
    private static final int DEFAULT_MIN_ALNUM_COUNT = 3;
    private static final int DEFAULT_MIN_GAIN = 15;
    private static final int DEFAULT_MAX_GAIN = 25;
    private static final Pattern URL_PATTERN = Pattern.compile("https?://\\S+", Pattern.CASE_INSENSITIVE);

    private final LevelStore levelStore;
    private final SettingsManager settingsManager;
    private final Map<String, Long> lastAwardByMember = new ConcurrentHashMap<>();

    @Deprecated(since = "v3", forRemoval = false)
    public LevelService() {
        this(new LevelStore(), null);
    }

    public LevelService(SettingsManager settingsManager) {
        this(new LevelStore(), settingsManager);
    }

    @Deprecated(since = "v3", forRemoval = false)
    public LevelService(LevelStore levelStore) {
        this(levelStore, null);
    }

    public LevelService(LevelStore levelStore, SettingsManager settingsManager) {
        this.levelStore = levelStore;
        this.settingsManager = settingsManager;
        if (settingsManager == null) {
            logger.warn("LevelService initialisé sans SettingsManager: fallback silencieux sur les constantes par défaut");
        }
    }

    public boolean shouldAwardXp(String content) {
        return shouldAwardXp(null, content);
    }

    public boolean shouldAwardXp(String guildId, String content) {
        if (content == null) {
            return false;
        }

        int minLength = settingsManager != null && guildId != null
                ? settingsManager.getXpMinMessageLength(guildId)
                : DEFAULT_MIN_MESSAGE_LENGTH;
        int minAlnum = settingsManager != null && guildId != null
                ? settingsManager.getXpMinAlnumCount(guildId)
                : DEFAULT_MIN_ALNUM_COUNT;

        String trimmed = content.trim();
        if (trimmed.length() < minLength) {
            return false;
        }

        if (trimmed.startsWith("/")) {
            return false;
        }

        if (URL_PATTERN.matcher(trimmed).find()) {
            return false;
        }

        if (hasExcessiveCharacterRepetition(trimmed)) {
            return false;
        }

        long alphaNumericCount = trimmed.chars().filter(Character::isLetterOrDigit).count();
        return alphaNumericCount >= minAlnum;
    }

    public XpResult addMessageXp(String guildId, String userId, String content) {
        if (!shouldAwardXp(guildId, content)) {
            return new XpResult(levelStore.getProfile(guildId, userId), false, false, 0);
        }

        String memberKey = guildId + ":" + userId;
        long now = Instant.now().toEpochMilli();
        long cooldownMs = settingsManager != null ? settingsManager.getXpCooldownMs(guildId) : DEFAULT_COOLDOWN_MS;
        if (!tryAcquireCooldown(memberKey, now, cooldownMs)) {
            return new XpResult(levelStore.getProfile(guildId, userId), false, false, 0);
        }

        int minGain = settingsManager != null ? settingsManager.getXpMinGain(guildId) : DEFAULT_MIN_GAIN;
        int maxGain = settingsManager != null ? settingsManager.getXpMaxGain(guildId) : DEFAULT_MAX_GAIN;
        int span = Math.max(0, maxGain - minGain);
        int xpGain = minGain + (span == 0 ? 0 : Math.abs((guildId + userId + now).hashCode()) % (span + 1));

        LevelProfile current = levelStore.getProfile(guildId, userId);
        LevelComputation before = computeLevel(current.xp());
        LevelComputation after = computeLevel(current.xp() + xpGain);

        LevelProfile updated = levelStore.addXp(guildId, userId, xpGain, after.level());
        boolean leveledUp = after.level() > before.level();

        if (leveledUp) {
            logger.info("Level up detecte guildId={}, userId={}, oldLevel={}, newLevel={}", guildId, userId, before.level(), after.level());
        }

        return new XpResult(updated, true, leveledUp, xpGain);
    }

    public LevelProfile getProfile(String guildId, String userId) {
        return levelStore.getProfile(guildId, userId);
    }

    public List<LevelProfile> getLeaderboard(String guildId, int limit) {
        return levelStore.getLeaderboard(guildId, limit);
    }

    public void resetUser(String guildId, String userId) {
        levelStore.resetUser(guildId, userId);
    }

    public void setUserXp(String guildId, String userId, long xp) {
        long safeXp = Math.max(0L, xp);
        levelStore.setXp(guildId, userId, safeXp, computeLevelFromTotalXp(safeXp));
    }

    public void addUserXp(String guildId, String userId, long delta) {
        LevelProfile profile = levelStore.getProfile(guildId, userId);
        long safeXp = Math.max(0L, profile.xp() + delta);
        levelStore.setXp(guildId, userId, safeXp, computeLevelFromTotalXp(safeXp));
    }

    public String getRewardRoleId(String guildId, int level) {
        if (settingsManager == null) {
            return null;
        }
        return settingsManager.getLevelRoleId(guildId, level);
    }

    public int getXpForNextLevel(long totalXp) {
        LevelComputation computation = computeLevel(totalXp);
        int nextLevel = computation.level() + 1;
        long xpForNextLevel = (long) nextLevel * nextLevel * 100L;
        return (int) Math.max(0L, xpForNextLevel - totalXp);
    }

    public int getCurrentProgressWithinLevel(long totalXp) {
        LevelComputation computation = computeLevel(totalXp);
        long currentLevelFloor = (long) computation.level() * computation.level() * 100L;
        return (int) Math.max(0L, totalXp - currentLevelFloor);
    }

    public int getXpThresholdForLevel(int level) {
        if (level <= 0) {
            return 0;
        }
        return level * level * 100;
    }

    public int computeLevelFromTotalXp(long totalXp) {
        return computeLevel(totalXp).level();
    }

    private LevelComputation computeLevel(long totalXp) {
        if (totalXp <= 0) {
            return new LevelComputation(0, 0L);
        }

        int level = (int) Math.floor(Math.sqrt(totalXp / 100.0));
        long currentFloor = (long) level * level * 100L;
        return new LevelComputation(level, currentFloor);
    }

    private boolean hasExcessiveCharacterRepetition(String value) {
        int run = 1;
        char previous = 0;

        for (char current : value.toCharArray()) {
            if (current == previous) {
                run++;
                if (run >= 5) {
                    return true;
                }
            } else {
                previous = current;
                run = 1;
            }
        }

        return false;
    }

    private record LevelComputation(int level, long floorXp) {
    }

    public record XpResult(LevelProfile profile, boolean xpAwarded, boolean leveledUp, int xpGained) {
    }

    private boolean tryAcquireCooldown(String memberKey, long now, long cooldownMs) {
        final boolean[] allowed = {false};
        lastAwardByMember.compute(memberKey, (key, previous) -> {
            long last = previous != null ? previous : 0L;
            if (now - last < cooldownMs) {
                allowed[0] = false;
                return last;
            }
            allowed[0] = true;
            return now;
        });
        return allowed[0];
    }
}
