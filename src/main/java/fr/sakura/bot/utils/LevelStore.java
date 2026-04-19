package fr.sakura.bot.utils;

import fr.sakura.bot.database.DatabaseManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

public class LevelStore {

    private static final Logger logger = LoggerFactory.getLogger(LevelStore.class);
    private static final long STALE_LOCK_MS = 60 * 60 * 1000L;

    private static final class LockEntry {
        private final ReentrantLock lock = new ReentrantLock();
        private volatile long lastUsedMs = System.currentTimeMillis();
    }

    private final Map<String, LockEntry> memberLocks = new ConcurrentHashMap<>();
    private final ScheduledExecutorService cleanupExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "levelstore-lock-cleanup");
        t.setDaemon(true);
        return t;
    });

    public LevelStore() {
        cleanupExecutor.scheduleAtFixedRate(this::purgeStaleLocks, 30, 30, TimeUnit.MINUTES);
    }

    public LevelProfile getProfile(String guildId, String userId) {
        String sql = "SELECT xp, level FROM levels WHERE guild_id = ? AND user_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, guildId);
            pstmt.setString(2, userId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return new LevelProfile(guildId, userId, rs.getLong("xp"), rs.getInt("level"));
                }
            }
        } catch (SQLException e) {
            logger.error("Erreur lecture profile XP guildId={}, userId={}", guildId, userId, e);
        }

        return new LevelProfile(guildId, userId, 0L, 0);
    }

    public LevelProfile addXp(String guildId, String userId, long xpToAdd, int newLevel) {
        return withMemberLock(guildId, userId, () -> {
            LevelProfile current = getProfile(guildId, userId);
            long totalXp = Math.max(0L, current.xp() + xpToAdd);
            int level = Math.max(0, newLevel);
            return upsertProfile(guildId, userId, totalXp, level);
        });
    }

    public LevelProfile setXp(String guildId, String userId, long xp, int level) {
        return withMemberLock(guildId, userId, () -> {
            long safeXp = Math.max(0L, xp);
            int safeLevel = Math.max(0, level);
            return upsertProfile(guildId, userId, safeXp, safeLevel);
        });
    }

    public List<LevelProfile> getLeaderboard(String guildId, int limit) {
        List<LevelProfile> profiles = new ArrayList<>();
        String sql = "SELECT user_id, xp, level FROM levels WHERE guild_id = ? ORDER BY level DESC, xp DESC LIMIT ?";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, guildId);
            pstmt.setInt(2, Math.max(1, limit));
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    profiles.add(new LevelProfile(guildId, rs.getString("user_id"), rs.getLong("xp"), rs.getInt("level")));
                }
            }
        } catch (SQLException e) {
            logger.error("Erreur lecture leaderboard guildId={}", guildId, e);
        }

        return profiles;
    }

    public void resetUser(String guildId, String userId) {
        withMemberLock(guildId, userId, () -> {
            String sql = "DELETE FROM levels WHERE guild_id = ? AND user_id = ?";
            try (Connection conn = DatabaseManager.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, guildId);
                pstmt.setString(2, userId);
                pstmt.executeUpdate();
            } catch (SQLException e) {
                logger.error("Erreur reset XP guildId={}, userId={}", guildId, userId, e);
            }
            return null;
        });
    }

    public void resetGuild(String guildId) {
        String sql = "DELETE FROM levels WHERE guild_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, guildId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            logger.error("Erreur reset XP guildId={}", guildId, e);
        }
    }

    private LevelProfile upsertProfile(String guildId, String userId, long xp, int level) {
        /*
         * SQLite  : ON CONFLICT(col) DO UPDATE SET col = excluded.col  (minuscule, pas d'espaces)
         * PostgreSQL : ON CONFLICT (col) DO UPDATE SET col = EXCLUDED.col (majuscule, espaces)
         * Les deux dialectes sont couverts ici pour garantir la compatibilité.
         */
        String sql = DatabaseManager.isPostgres()
                ? "INSERT INTO levels (guild_id, user_id, xp, level) VALUES (?, ?, ?, ?) " +
                  "ON CONFLICT (guild_id, user_id) DO UPDATE SET xp = EXCLUDED.xp, level = EXCLUDED.level"
                : "INSERT INTO levels (guild_id, user_id, xp, level) VALUES (?, ?, ?, ?) " +
                  "ON CONFLICT(guild_id, user_id) DO UPDATE SET xp = excluded.xp, level = excluded.level";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, guildId);
            pstmt.setString(2, userId);
            pstmt.setLong(3, xp);
            pstmt.setInt(4, level);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            logger.error("Erreur upsert XP guildId={}, userId={}", guildId, userId, e);
        }

        return new LevelProfile(guildId, userId, xp, level);
    }

    private String memberKey(String guildId, String userId) {
        return guildId + ":" + userId;
    }

    private <T> T withMemberLock(String guildId, String userId, Supplier<T> task) {
        String key = memberKey(guildId, userId);
        LockEntry entry = memberLocks.computeIfAbsent(key, ignored -> new LockEntry());
        entry.lock.lock();
        try {
            return task.get();
        } finally {
            entry.lastUsedMs = System.currentTimeMillis();
            entry.lock.unlock();
        }
    }

    private void purgeStaleLocks() {
        long now = System.currentTimeMillis();
        int removed = 0;
        for (Map.Entry<String, LockEntry> mapEntry : memberLocks.entrySet()) {
            LockEntry lockEntry = mapEntry.getValue();
            if (now - lockEntry.lastUsedMs < STALE_LOCK_MS) {
                continue;
            }
            if (!lockEntry.lock.tryLock()) {
                continue;
            }
            try {
                if (!lockEntry.lock.hasQueuedThreads() && now - lockEntry.lastUsedMs >= STALE_LOCK_MS) {
                    if (memberLocks.remove(mapEntry.getKey(), lockEntry)) {
                        removed++;
                    }
                }
            } finally {
                lockEntry.lock.unlock();
            }
        }
        if (removed > 0) {
            logger.debug("LevelStore cleanup: {} lock(s) périmé(s) retiré(s)", removed);
        }
    }
}
