package fr.sakura.bot.core.store;

import fr.sakura.bot.core.util.DbHelper;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Stockage des panels de choix de rôles.
 */
public class RolesPanelStore {

    private static final Logger logger = LoggerFactory.getLogger(RolesPanelStore.class);

    public record RolePanel(long id, String guildId, String channelId, String messageId, String createdAt, boolean exclusive, boolean useButtons, String title, String headerEmoji, List<RolePanelButton> buttons) {
    }

    public record RolePanelButton(long id, long panelId, String roleId, String label, String emoji) {
    }

    public RolePanel createPanel(String guildId, String channelId, String messageId, boolean exclusive, boolean useButtons, String title, String headerEmoji) {
        String sql = "INSERT INTO role_panels (guild_id, channel_id, message_id, created_at, is_exclusive, use_buttons, title, header_emoji) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        String createdAt = OffsetDateTime.now().toString();
        
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setString(1, guildId);
            pstmt.setString(2, channelId);
            pstmt.setString(3, messageId);
            pstmt.setString(4, createdAt);
            pstmt.setInt(5, exclusive ? 1 : 0);
            pstmt.setInt(6, useButtons ? 1 : 0);
            pstmt.setString(7, title);
            pstmt.setString(8, headerEmoji);
            pstmt.executeUpdate();

            try (ResultSet keys = pstmt.getGeneratedKeys()) {
                if (keys.next()) {
                    long panelId = keys.getLong(1);
                    return new RolePanel(panelId, guildId, channelId, messageId, createdAt, exclusive, useButtons, title, headerEmoji, new ArrayList<>());
                }
            }
        } catch (SQLException e) {
            logger.error("Erreur create role panel guildId={}", guildId, e);
        }
        return null;
    }

    public boolean deletePanel(String guildId, long panelId) {
        String sql = "DELETE FROM role_panels WHERE guild_id = ? AND id = ?";
        try {
            return DbHelper.update(sql, pstmt -> {
                pstmt.setString(1, guildId);
                pstmt.setLong(2, panelId);
            }) > 0;
        } catch (Exception e) {
            logger.error("Erreur delete role panel guildId={}, panelId={}", guildId, panelId, e);
            return false;
        }
    }

    public RolePanel findPanel(String guildId, long panelId) {
        String sql = "SELECT id, guild_id, channel_id, message_id, created_at, is_exclusive, use_buttons, title, header_emoji FROM role_panels WHERE guild_id = ? AND id = ?";
        return DbHelper.queryOne(sql,
                pstmt -> {
                    pstmt.setString(1, guildId);
                    pstmt.setLong(2, panelId);
                },
                rs -> new RolePanel(
                        rs.getLong("id"),
                        rs.getString("guild_id"),
                        rs.getString("channel_id"),
                        rs.getString("message_id"),
                        rs.getString("created_at"),
                        rs.getInt("is_exclusive") == 1,
                        rs.getInt("use_buttons") == 1,
                        rs.getString("title"),
                        rs.getString("header_emoji"),
                        listButtons(rs.getLong("id"))
                )
        ).orElse(null);
    }

    /**
     * Liste tous les panels d'une guilde avec leurs boutons (Optimisé via JOIN).
     */
    public List<RolePanel> listPanels(String guildId) {
        String sql = "SELECT p.id, p.guild_id, p.channel_id, p.message_id, p.created_at, p.is_exclusive, p.use_buttons, p.title, p.header_emoji, " +
                     "b.id AS b_id, b.role_id, b.label, b.emoji " +
                     "FROM role_panels p " +
                     "LEFT JOIN role_panel_buttons b ON b.panel_id = p.id " +
                     "WHERE p.guild_id = ? " +
                     "ORDER BY p.id DESC, b.id ASC";

        Map<Long, RolePanel> panelMap = new LinkedHashMap<>();

        DbHelper.queryList(sql,
                pstmt -> pstmt.setString(1, guildId),
                rs -> {
                    long panelId = rs.getLong("id");
                    RolePanel panel = panelMap.computeIfAbsent(panelId, id -> {
                        try {
                            return new RolePanel(
                                    panelId,
                                    rs.getString("guild_id"),
                                    rs.getString("channel_id"),
                                    rs.getString("message_id"),
                                    rs.getString("created_at"),
                                    rs.getInt("is_exclusive") == 1,
                                    rs.getInt("use_buttons") == 1,
                                    rs.getString("title"),
                                    rs.getString("header_emoji"),
                                    new ArrayList<>()
                            );
                        } catch (SQLException e) {
                            throw new RuntimeException(e);
                        }
                    });

                    long buttonId = rs.getLong("b_id");
                    if (buttonId > 0 || !rs.wasNull()) {
                        panel.buttons().add(new RolePanelButton(
                                buttonId,
                                panelId,
                                rs.getString("role_id"),
                                rs.getString("label"),
                                rs.getString("emoji")
                        ));
                    }
                    return null;
                }
        );

        return new ArrayList<>(panelMap.values());
    }

    public List<RolePanelButton> listButtons(long panelId) {
        String sql = "SELECT id, panel_id, role_id, label, emoji FROM role_panel_buttons WHERE panel_id = ? ORDER BY id ASC";
        return DbHelper.queryList(sql,
                pstmt -> pstmt.setLong(1, panelId),
                rs -> new RolePanelButton(
                        rs.getLong("id"),
                        rs.getLong("panel_id"),
                        rs.getString("role_id"),
                        rs.getString("label"),
                        rs.getString("emoji")
                )
        );
    }

    public int countButtons(long panelId) {
        String sql = "SELECT COUNT(*) FROM role_panel_buttons WHERE panel_id = ?";
        return DbHelper.queryOne(sql,
                pstmt -> pstmt.setLong(1, panelId),
                rs -> rs.getInt(1)
        ).orElse(0);
    }

    public void upsertButton(long panelId, String roleId, String label, String emoji) {
        String sql = DatabaseManager.isPostgres()
                ? "INSERT INTO role_panel_buttons (panel_id, role_id, label, emoji) VALUES (?, ?, ?, ?) " +
                  "ON CONFLICT (panel_id, role_id) DO UPDATE SET label = EXCLUDED.label, emoji = EXCLUDED.emoji"
                : "INSERT INTO role_panel_buttons (panel_id, role_id, label, emoji) VALUES (?, ?, ?, ?) " +
                  "ON CONFLICT(panel_id, role_id) DO UPDATE SET label = excluded.label, emoji = excluded.emoji";
        
        try {
            DbHelper.update(sql, pstmt -> {
                pstmt.setLong(1, panelId);
                pstmt.setString(2, roleId);
                pstmt.setString(3, label);
                pstmt.setString(4, emoji);
            });
        } catch (Exception e) {
            logger.error("Erreur upsert role panel button panelId={}, roleId={}", panelId, roleId, e);
        }
    }

    public boolean removeButton(long panelId, String roleId) {
        String sql = "DELETE FROM role_panel_buttons WHERE panel_id = ? AND role_id = ?";
        try {
            return DbHelper.update(sql, pstmt -> {
                pstmt.setLong(1, panelId);
                pstmt.setString(2, roleId);
            }) > 0;
        } catch (Exception e) {
            logger.error("Erreur remove role panel button panelId={}, roleId={}", panelId, roleId, e);
            return false;
        }
    }

    public boolean updatePanelLocation(String guildId, long panelId, String channelId, String messageId) {
        String sql = "UPDATE role_panels SET channel_id = ?, message_id = ? WHERE guild_id = ? AND id = ?";
        try {
            return DbHelper.update(sql, pstmt -> {
                pstmt.setString(1, channelId);
                pstmt.setString(2, messageId);
                pstmt.setString(3, guildId);
                pstmt.setLong(4, panelId);
            }) > 0;
        } catch (Exception e) {
            logger.error("Erreur update role panel location guildId={}, panelId={}", guildId, panelId, e);
            return false;
        }
    }

    public RolePanelButton findButton(String guildId, long panelId, String roleId) {
        String sql = "SELECT b.id, b.panel_id, b.role_id, b.label, b.emoji " +
                "FROM role_panel_buttons b " +
                "JOIN role_panels p ON p.id = b.panel_id " +
                "WHERE p.guild_id = ? AND b.panel_id = ? AND b.role_id = ?";
        return DbHelper.queryOne(sql,
                pstmt -> {
                    pstmt.setString(1, guildId);
                    pstmt.setLong(2, panelId);
                    pstmt.setString(3, roleId);
                },
                rs -> new RolePanelButton(
                        rs.getLong("id"),
                        rs.getLong("panel_id"),
                        rs.getString("role_id"),
                        rs.getString("label"),
                        rs.getString("emoji")
                )
        ).orElse(null);
    }
}
