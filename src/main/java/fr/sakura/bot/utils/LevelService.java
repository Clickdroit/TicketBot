package fr.sakura.bot.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class LevelService {

    private static final Logger logger = LoggerFactory.getLogger(LevelService.class);
    private static final long DEFAULT_COOLDOWN_MS = 60_000L;
    private static final int MIN_MESSAGE_LENGTH = 5;
    private static final java.util.regex.Pattern URL_PATTERN = java.util.regex.Pattern.compile("https?://\\S+", java.util.regex.Pattern.CASE_INSENSITIVE);

    private final LevelStore levelStore;
    private final Map<String, Long> lastAwardByMember = new ConcurrentHashMap<>();

    public LevelService() {
        this(new LevelStore());
    }

    public LevelService(LevelStore levelStore) {
        this.levelStore = levelStore;
    }

    public boolean shouldAwardXp(String content) {
        if (content == null) {
            return false;
        }

        String trimmed = content.trim();
        if (trimmed.length() < MIN_MESSAGE_LENGTH) {
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
        return alphaNumericCount >= 3;
    }

    public synchronized XpResult addMessageXp(String guildId, String userId, String content) {
        if (!shouldAwardXp(content)) {
            return new XpResult(levelStore.getProfile(guildId, userId), false, false, 0);
        }

        String memberKey = guildId + ":" + userId;
        long now = Instant.now().toEpochMilli();
        long lastAward = lastAwardByMember.getOrDefault(memberKey, 0L);
        if (now - lastAward < DEFAULT_COOLDOWN_MS) {
            return new XpResult(levelStore.getProfile(guildId, userId), false, false, 0);
        }

        lastAwardByMember.put(memberKey, now);
        int xpGain = 15 + Math.abs((guildId + userId + now).hashCode()) % 11;
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

    public java.util.List<LevelProfile> getLeaderboard(String guildId, int limit) {
        return levelStore.getLeaderboard(guildId, limit);
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
}



