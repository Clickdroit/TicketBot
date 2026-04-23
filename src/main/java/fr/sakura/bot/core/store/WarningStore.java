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
        logger.info("WarningStore initialisÃ© â€” stockage via DatabaseManager ({})",
                DatabaseManager.isPostgres() ? "PostgreSQL" : "SQLite");
    }

    public synchronized List<WarningEntry> getWarnings(String guildId, String userId) {
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

    public synchronized int getWarningsCount(String guildId, String userId) {
        String sql = "SELECT COUNT(*) FROM warnings WHERE guild_id = ? AND user_id = ?";
        return DbHelper.queryOne(sql,
                pstmt -> {
                    pstmt.setString(1, guildId);
                    pstmt.setString(2, userId);
                },
                rs -> rs.getInt(1)
        ).orElse(0);
    }

    public synchronized int addWarning(String guildId, String userId, WarningEntry warningEntry) {
        String insertSql = "INSERT INTO warnings (guild_id, user_id, reason, timestamp, moderator_id) VALUES (?, ?, ?, ?, ?)";
        try {
            DbHelper.update(insertSql, pstmt -> {
                pstmt.setString(1, guildId);
                pstmt.setString(2, userId);
                pstmt.setString(3, warningEntry.reason());
                pstmt.setString(4, warningEntry.timestamp());
                pstmt.setString(5, warningEntry.moderatorId());
            });
            return getWarningsCount(guildId, userId);
        } catch (Exception e) {
            logger.error("Erreur lors de l'ajout d'un warning pour guildId={}, userId={}", guildId, userId, e);
            return 0;
        }
    }

    public synchronized int clearWarnings(String guildId, String userId) {
        String deleteSql = "DELETE FROM warnings WHERE guild_id = ? AND user_id = ?";
        try {
            return DbHelper.update(deleteSql, pstmt -> {
                pstmt.setString(1, guildId);
                pstmt.setString(2, userId);
            });
        } catch (Exception e) {
            logger.error("Erreur lors de la suppression des warnings pour guildId={}, userId={}", guildId, userId, e);
            return 0;
        }
    }
}
