package fr.sakura.bot.core.store;

import fr.sakura.bot.core.model.WarningEntry;
import fr.sakura.bot.core.util.DbHelper;
import fr.sakura.bot.database.DatabaseManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Stockage des avertissements (warnings) des membres.
 */
public class WarningStore {

    private static final Logger logger = LoggerFactory.getLogger(WarningStore.class);

    public WarningStore() {
        logger.info("WarningStore initialisé — stockage via DatabaseManager ({})",
                DatabaseManager.isPostgres() ? "PostgreSQL" : "SQLite");
    }

    public List<WarningEntry> getWarnings(String guildId, String userId) {
        String sql = "SELECT moderator_id, reason, timestamp FROM warnings WHERE guild_id = ? AND user_id = ?";
        return DbHelper.queryList(sql,
                pstmt -> {
                    pstmt.setString(1, guildId);
                    pstmt.setString(2, userId);
                },
                rs -> new WarningEntry(
                        rs.getString("moderator_id"),
                        rs.getString("reason"),
                        rs.getString("timestamp")
                )
        );
    }

    public int getWarningsCount(String guildId, String userId) {
        String sql = "SELECT COUNT(*) FROM warnings WHERE guild_id = ? AND user_id = ?";
        return DbHelper.queryOne(sql,
                pstmt -> {
                    pstmt.setString(1, guildId);
                    pstmt.setString(2, userId);
                },
                rs -> rs.getInt(1)
        ).orElse(0);
    }

    public int addWarning(String guildId, String userId, WarningEntry warningEntry) {
        String insertSql = "INSERT INTO warnings (guild_id, user_id, reason, timestamp, moderator_id) VALUES (?, ?, ?, ?, ?)";
        DbHelper.update(insertSql, pstmt -> {
            pstmt.setString(1, guildId);
            pstmt.setString(2, userId);
            pstmt.setString(3, warningEntry.reason());
            pstmt.setString(4, warningEntry.timestamp());
            pstmt.setString(5, warningEntry.moderatorId());
        });
        return getWarningsCount(guildId, userId);
    }

    public int clearWarnings(String guildId, String userId) {
        String deleteSql = "DELETE FROM warnings WHERE guild_id = ? AND user_id = ?";
        return DbHelper.update(deleteSql, pstmt -> {
            pstmt.setString(1, guildId);
            pstmt.setString(2, userId);
        });
    }

    public record WeeklyStats(java.util.Map<String, Integer> topUsers, java.util.Map<String, Integer> topReasons, int total) {}

    public WeeklyStats getWeeklyStats(String guildId, String sinceIso) {
        String sqlUsers = "SELECT user_id, COUNT(*) as cnt FROM warnings WHERE guild_id = ? AND timestamp >= ? GROUP BY user_id ORDER BY cnt DESC LIMIT 5";
        String sqlReasons = "SELECT reason, COUNT(*) as cnt FROM warnings WHERE guild_id = ? AND timestamp >= ? GROUP BY reason ORDER BY cnt DESC LIMIT 5";
        
        java.util.Map<String, Integer> users = new java.util.LinkedHashMap<>();
        DbHelper.queryList(sqlUsers, pstmt -> {
            pstmt.setString(1, guildId);
            pstmt.setString(2, sinceIso);
        }, rs -> {
            users.put(rs.getString("user_id"), rs.getInt("cnt"));
            return null;
        });

        java.util.Map<String, Integer> reasons = new java.util.LinkedHashMap<>();
        DbHelper.queryList(sqlReasons, pstmt -> {
            pstmt.setString(1, guildId);
            pstmt.setString(2, sinceIso);
        }, rs -> {
            reasons.put(rs.getString("reason"), rs.getInt("cnt"));
            return null;
        });

        int total = DbHelper.queryOne("SELECT COUNT(*) FROM warnings WHERE guild_id = ? AND timestamp >= ?",
                pstmt -> {
                    pstmt.setString(1, guildId);
                    pstmt.setString(2, sinceIso);
                },
                rs -> rs.getInt(1)
        ).orElse(0);

        return new WeeklyStats(users, reasons, total);
    }
}
