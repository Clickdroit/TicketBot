package fr.sakura.bot.database;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

/**
 * Gère la configuration de la guilde en base de données pour TicketBot.
 */
public class SettingsManager {

    private static final Logger logger = LoggerFactory.getLogger(SettingsManager.class);

    private boolean isDbNotReady() {
        return !DatabaseManager.isReady();
    }

    private void ensureGuildExists(String guildId) {
        if (isDbNotReady()) return;

        String sql = DatabaseManager.isPostgres()
                ? "INSERT INTO settings (guild_id) VALUES (?) ON CONFLICT (guild_id) DO NOTHING"
                : "INSERT OR IGNORE INTO settings (guild_id) VALUES (?)";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, guildId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            logger.error("Erreur lors de l'initialisation des paramètres pour guildId={}", guildId, e);
        }
    }

    private Optional<String> getStringSetting(String guildId, String column) {
        if (isDbNotReady()) return Optional.empty();

        ensureGuildExists(guildId);
        String sql = "SELECT " + column + " FROM settings WHERE guild_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, guildId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    String val = rs.getString(column);
                    return (val == null || val.isBlank()) ? Optional.empty() : Optional.of(val);
                }
            }
        } catch (SQLException e) {
            logger.error("Erreur lecture {} guildId={}", column, guildId);
        }
        return Optional.empty();
    }

    private void setStringSetting(String guildId, String column, String value) {
        if (isDbNotReady()) return;

        ensureGuildExists(guildId);
        String sql = "UPDATE settings SET " + column + " = ? WHERE guild_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, value);
            pstmt.setString(2, guildId);
            pstmt.executeUpdate();
            logger.info("Config {}={} pour guildId={}", column, value, guildId);
        } catch (SQLException e) {
            logger.error("Erreur update {} guildId={}", column, guildId, e);
        }
    }

    // --- Façades Publiques ---

    public Optional<String> getLogChannelId(String guildId) { 
        return getStringSetting(guildId, "log_channel_id"); 
    }
    
    public void setLogChannelId(String guildId, String channelId) { 
        setStringSetting(guildId, "log_channel_id", channelId); 
    }

    public Optional<String> getTranscriptChannelId(String guildId) { 
        return getStringSetting(guildId, "transcript_channel_id"); 
    }
    
    public void setTranscriptChannelId(String guildId, String channelId) { 
        setStringSetting(guildId, "transcript_channel_id", channelId); 
    }
}
