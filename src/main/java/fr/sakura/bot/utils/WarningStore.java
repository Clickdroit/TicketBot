package fr.sakura.bot.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import fr.sakura.bot.database.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Stockage SQLite/PostgreSQL des warnings via DatabaseManager.
 */
public class WarningStore {

    private static final Logger logger = LoggerFactory.getLogger(WarningStore.class);

    public WarningStore() {
        logger.info("WarningStore initialisé — stockage via DatabaseManager ({})",
                DatabaseManager.isPostgres() ? "PostgreSQL" : "SQLite");
    }

    public synchronized List<WarningEntry> getWarnings(String guildId, String userId) {
        List<WarningEntry> warnings = new ArrayList<>();
        String sql = "SELECT moderator_id, reason, timestamp FROM warnings WHERE guild_id = ? AND user_id = ?";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, guildId);
            pstmt.setString(2, userId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    warnings.add(new WarningEntry(
                            rs.getString("moderator_id"),
                            rs.getString("reason"),
                            rs.getString("timestamp")
                    ));
                }
            }
        } catch (SQLException e) {
            logger.error("Erreur lors de la récupération des warnings pour guildId={}, userId={}", guildId, userId, e);
        }
        return warnings;
    }

    public synchronized int getWarningsCount(String guildId, String userId) {
        String sql = "SELECT COUNT(*) FROM warnings WHERE guild_id = ? AND user_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, guildId);
            pstmt.setString(2, userId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            logger.error("Erreur lors du comptage des warnings pour guildId={}, userId={}", guildId, userId, e);
        }
        return 0;
    }

    public synchronized int addWarning(String guildId, String userId, WarningEntry warningEntry) {
        String insertSql = "INSERT INTO warnings (guild_id, user_id, reason, timestamp, moderator_id) VALUES (?, ?, ?, ?, ?)";
        String countSql = "SELECT COUNT(*) FROM warnings WHERE guild_id = ? AND user_id = ?";

        try (Connection conn = DatabaseManager.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement insertStmt = conn.prepareStatement(insertSql);
                 PreparedStatement countStmt = conn.prepareStatement(countSql)) {
                insertStmt.setString(1, guildId);
                insertStmt.setString(2, userId);
                insertStmt.setString(3, warningEntry.reason());
                insertStmt.setString(4, warningEntry.timestamp());
                insertStmt.setString(5, warningEntry.moderatorId());
                insertStmt.executeUpdate();

                countStmt.setString(1, guildId);
                countStmt.setString(2, userId);
                try (ResultSet rs = countStmt.executeQuery()) {
                    int total = rs.next() ? rs.getInt(1) : 0;
                    conn.commit();
                    return total;
                }
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            logger.error("Erreur lors de l'ajout d'un warning pour guildId={}, userId={}", guildId, userId, e);
        }
        return 0;
    }

    public synchronized int clearWarnings(String guildId, String userId) {
        int count = getWarningsCount(guildId, userId);
        if (count == 0) return 0;

        String deleteSql = "DELETE FROM warnings WHERE guild_id = ? AND user_id = ?";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(deleteSql)) {
            pstmt.setString(1, guildId);
            pstmt.setString(2, userId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            logger.error("Erreur lors de la suppression des warnings pour guildId={}, userId={}", guildId, userId, e);
        }

        return count;
    }
}
