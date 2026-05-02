package fr.sakura.bot.database;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

public class SnapshotStore {
    private static final Logger logger = LoggerFactory.getLogger(SnapshotStore.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    public void saveSnapshot(String guildId, String targetId, String type, Object snapshot) {
        String sql = DatabaseManager.isPostgres()
                ? "INSERT INTO protect_snapshots (guild_id, target_id, type, data, updated_at) VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP) " +
                  "ON CONFLICT (guild_id, target_id) DO UPDATE SET data = EXCLUDED.data, updated_at = EXCLUDED.updated_at"
                : "INSERT OR REPLACE INTO protect_snapshots (guild_id, target_id, type, data, updated_at) VALUES (?, ?, ?, ?, datetime('now'))";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, guildId);
            pstmt.setString(2, targetId);
            pstmt.setString(3, type);
            pstmt.setString(4, objectMapper.writeValueAsString(snapshot));
            pstmt.executeUpdate();
        } catch (Exception e) {
            logger.error("Erreur sauvegarde snapshot guildId={}, targetId={}", guildId, targetId, e);
        }
    }

    public void deleteSnapshot(String guildId, String targetId) {
        String sql = "DELETE FROM protect_snapshots WHERE guild_id = ? AND target_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, guildId);
            pstmt.setString(2, targetId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            logger.error("Erreur suppression snapshot guildId={}, targetId={}", guildId, targetId, e);
        }
    }

    public <T> Map<String, T> loadSnapshots(String guildId, String type, Class<T> clazz) {
        Map<String, T> snapshots = new HashMap<>();
        String sql = "SELECT target_id, data FROM protect_snapshots WHERE guild_id = ? AND type = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, guildId);
            pstmt.setString(2, type);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                String targetId = rs.getString("target_id");
                String data = rs.getString("data");
                snapshots.put(targetId, objectMapper.readValue(data, clazz));
            }
        } catch (Exception e) {
            logger.error("Erreur chargement snapshots guildId={}, type={}", guildId, type, e);
        }
        return snapshots;
    }
}
