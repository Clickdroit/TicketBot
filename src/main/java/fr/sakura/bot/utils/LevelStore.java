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

public class LevelStore {

    private static final Logger logger = LoggerFactory.getLogger(LevelStore.class);

    public synchronized LevelProfile getProfile(String guildId, String userId) {
        String sql = "SELECT xp, level FROM levels WHERE guild_id = ? AND user_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, guildId);
            pstmt.setString(2, userId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return new LevelProfile(guildId, userId, rs.getLong("xp"), rs.getInt("level"));
            }
        } catch (SQLException e) {
            logger.error("Erreur lecture profile XP guildId={}, userId={}", guildId, userId, e);
        }

        return new LevelProfile(guildId, userId, 0L, 0);
    }

    public synchronized LevelProfile addXp(String guildId, String userId, long xpToAdd, int newLevel) {
        LevelProfile current = getProfile(guildId, userId);
        long totalXp = Math.max(0L, current.xp() + xpToAdd);
        int level = Math.max(0, newLevel);
        return upsertProfile(guildId, userId, totalXp, level);
    }

    public synchronized LevelProfile setXp(String guildId, String userId, long xp, int level) {
        long safeXp = Math.max(0L, xp);
        int safeLevel = Math.max(0, level);
        return upsertProfile(guildId, userId, safeXp, safeLevel);
    }

    public synchronized List<LevelProfile> getLeaderboard(String guildId, int limit) {
        List<LevelProfile> profiles = new ArrayList<>();
        String sql = "SELECT user_id, xp, level FROM levels WHERE guild_id = ? ORDER BY level DESC, xp DESC LIMIT ?";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, guildId);
            pstmt.setInt(2, Math.max(1, limit));
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                profiles.add(new LevelProfile(guildId, rs.getString("user_id"), rs.getLong("xp"), rs.getInt("level")));
            }
        } catch (SQLException e) {
            logger.error("Erreur lecture leaderboard guildId={}", guildId, e);
        }

        return profiles;
    }

    public synchronized void resetUser(String guildId, String userId) {
        String sql = "DELETE FROM levels WHERE guild_id = ? AND user_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, guildId);
            pstmt.setString(2, userId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            logger.error("Erreur reset XP guildId={}, userId={}", guildId, userId, e);
        }
    }

    public synchronized void resetGuild(String guildId) {
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
        String sql = "INSERT INTO levels (guild_id, user_id, xp, level) VALUES (?, ?, ?, ?) " +
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
}
