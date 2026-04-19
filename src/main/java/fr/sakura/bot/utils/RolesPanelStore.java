package fr.sakura.bot.utils;

import fr.sakura.bot.database.DatabaseManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

public class RolesPanelStore {

    private static final Logger logger = LoggerFactory.getLogger(RolesPanelStore.class);

    public record RolePanel(long id, String guildId, String channelId, String messageId, String createdAt, List<RolePanelButton> buttons) {
    }

    public record RolePanelButton(long id, long panelId, String roleId, String label, String emoji) {
    }

    public RolePanel createPanel(String guildId, String channelId, String messageId) {
        String sql = "INSERT INTO role_panels (guild_id, channel_id, message_id, created_at) VALUES (?, ?, ?, ?)";
        String createdAt = OffsetDateTime.now().toString();
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setString(1, guildId);
            pstmt.setString(2, channelId);
            pstmt.setString(3, messageId);
            pstmt.setString(4, createdAt);
            pstmt.executeUpdate();

            try (ResultSet keys = pstmt.getGeneratedKeys()) {
                if (keys.next()) {
                    long panelId = keys.getLong(1);
                    return new RolePanel(panelId, guildId, channelId, messageId, createdAt, List.of());
                }
            }
        } catch (SQLException e) {
            logger.error("Erreur create role panel guildId={}", guildId, e);
        }
        return null;
    }

    public RolePanel findPanel(String guildId, long panelId) {
        String sql = "SELECT id, guild_id, channel_id, message_id, created_at FROM role_panels WHERE guild_id = ? AND id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, guildId);
            pstmt.setLong(2, panelId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return new RolePanel(
                            rs.getLong("id"),
                            rs.getString("guild_id"),
                            rs.getString("channel_id"),
                            rs.getString("message_id"),
                            rs.getString("created_at"),
                            listButtons(rs.getLong("id"))
                    );
                }
            }
        } catch (SQLException e) {
            logger.error("Erreur find role panel guildId={}, panelId={}", guildId, panelId, e);
        }
        return null;
    }

    public List<RolePanel> listPanels(String guildId) {
        List<RolePanel> panels = new ArrayList<>();
        String sql = "SELECT id, guild_id, channel_id, message_id, created_at FROM role_panels WHERE guild_id = ? ORDER BY id DESC";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, guildId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    long panelId = rs.getLong("id");
                    panels.add(new RolePanel(
                            panelId,
                            rs.getString("guild_id"),
                            rs.getString("channel_id"),
                            rs.getString("message_id"),
                            rs.getString("created_at"),
                            listButtons(panelId)
                    ));
                }
            }
        } catch (SQLException e) {
            logger.error("Erreur list role panels guildId={}", guildId, e);
        }
        return panels;
    }

    public List<RolePanelButton> listButtons(long panelId) {
        List<RolePanelButton> buttons = new ArrayList<>();
        String sql = "SELECT id, panel_id, role_id, label, emoji FROM role_panel_buttons WHERE panel_id = ? ORDER BY id ASC";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, panelId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    buttons.add(new RolePanelButton(
                            rs.getLong("id"),
                            rs.getLong("panel_id"),
                            rs.getString("role_id"),
                            rs.getString("label"),
                            rs.getString("emoji")
                    ));
                }
            }
        } catch (SQLException e) {
            logger.error("Erreur list role panel buttons panelId={}", panelId, e);
        }
        return buttons;
    }

    public int countButtons(long panelId) {
        String sql = "SELECT COUNT(*) FROM role_panel_buttons WHERE panel_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, panelId);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            logger.error("Erreur count role panel buttons panelId={}", panelId, e);
        }
        return 0;
    }

    public void upsertButton(long panelId, String roleId, String label, String emoji) {
        String sql = DatabaseManager.isPostgres()
                ? "INSERT INTO role_panel_buttons (panel_id, role_id, label, emoji) VALUES (?, ?, ?, ?) ON CONFLICT (panel_id, role_id) DO UPDATE SET label = EXCLUDED.label, emoji = EXCLUDED.emoji"
                : "INSERT INTO role_panel_buttons (panel_id, role_id, label, emoji) VALUES (?, ?, ?, ?) ON CONFLICT(panel_id, role_id) DO UPDATE SET label = excluded.label, emoji = excluded.emoji";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, panelId);
            pstmt.setString(2, roleId);
            pstmt.setString(3, label);
            pstmt.setString(4, emoji);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            logger.error("Erreur upsert role panel button panelId={}, roleId={}", panelId, roleId, e);
        }
    }

    public boolean removeButton(long panelId, String roleId) {
        String sql = "DELETE FROM role_panel_buttons WHERE panel_id = ? AND role_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, panelId);
            pstmt.setString(2, roleId);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.error("Erreur remove role panel button panelId={}, roleId={}", panelId, roleId, e);
        }
        return false;
    }

    public RolePanelButton findButton(String guildId, long panelId, String roleId) {
        String sql = "SELECT b.id, b.panel_id, b.role_id, b.label, b.emoji " +
                "FROM role_panel_buttons b " +
                "JOIN role_panels p ON p.id = b.panel_id " +
                "WHERE p.guild_id = ? AND b.panel_id = ? AND b.role_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, guildId);
            pstmt.setLong(2, panelId);
            pstmt.setString(3, roleId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return new RolePanelButton(
                            rs.getLong("id"),
                            rs.getLong("panel_id"),
                            rs.getString("role_id"),
                            rs.getString("label"),
                            rs.getString("emoji")
                    );
                }
            }
        } catch (SQLException e) {
            logger.error("Erreur find role panel button guildId={}, panelId={}, roleId={}", guildId, panelId, roleId, e);
        }
        return null;
    }
}
