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
 * Stockage SQLite des warnings.
 */
public class WarningStore {

    private static final Logger logger = LoggerFactory.getLogger(WarningStore.class);

    // Conservé pour compatibilité avec CommandManager
    public WarningStore(String warningsFilePath) {
        logger.info("WarningStore utilise desormais SQLite via DatabaseManager");
    }

    public synchronized List<WarningEntry> getWarnings(String guildId, String userId) {
        List<WarningEntry> warnings = new ArrayList<>();
        String sql = "SELECT moderator_id, reason, timestamp FROM warnings WHERE guild_id = ? AND user_id = ?";
        
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, guildId);
            pstmt.setString(2, userId);
            
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                warnings.add(new WarningEntry(
                        rs.getString("moderator_id"),
                        rs.getString("reason"),
                        rs.getString("timestamp")
                ));
            }
        } catch (SQLException e) {
            logger.error("Erreur lors de la recuperation des warnings pour guildId={}, userId={}", guildId, userId, e);
        }
        return warnings;
    }

    public synchronized int addWarning(String guildId, String userId, WarningEntry warningEntry) {
        String insertSql = "INSERT INTO warnings (guild_id, user_id, reason, timestamp, moderator_id) VALUES (?, ?, ?, ?, ?)";
        
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(insertSql)) {
            
            pstmt.setString(1, guildId);
            pstmt.setString(2, userId);
            pstmt.setString(3, warningEntry.getReason());
            pstmt.setString(4, warningEntry.getTimestamp());
            pstmt.setString(5, warningEntry.getModeratorId());
            
            pstmt.executeUpdate();
            
        } catch (SQLException e) {
            logger.error("Erreur lors de l'ajout d'un warning pour guildId={}, userId={}", guildId, userId, e);
        }

        // Retourne le nouveau nombre total
        return getWarnings(guildId, userId).size();
    }

    public synchronized int clearWarnings(String guildId, String userId) {
        int count = getWarnings(guildId, userId).size();
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
