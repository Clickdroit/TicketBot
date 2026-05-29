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

    public Optional<String> getSupportRoleId(String guildId) { 
        return getStringSetting(guildId, "support_role_id"); 
    }
    
    public void setSupportRoleId(String guildId, String roleId) { 
        setStringSetting(guildId, "support_role_id", roleId); 
    }

    public java.util.List<String> getSupportRoles(String guildId, String category) {
        if (isDbNotReady()) return java.util.Collections.emptyList();
        java.util.List<String> roleIds = new java.util.ArrayList<>();
        String sql = "SELECT role_id FROM ticket_support_roles WHERE guild_id = ? AND category = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, guildId);
            pstmt.setString(2, category);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    roleIds.add(rs.getString("role_id"));
                }
            }
        } catch (SQLException e) {
            logger.error("Erreur lecture support_roles guildId={}, category={}", guildId, category, e);
        }
        return roleIds;
    }

    public void setSupportRoles(String guildId, String category, java.util.List<String> roleIds) {
        if (isDbNotReady()) return;
        String deleteSql = "DELETE FROM ticket_support_roles WHERE guild_id = ? AND category = ?";
        String insertSql = "INSERT INTO ticket_support_roles (guild_id, category, role_id) VALUES (?, ?, ?)";
        try (Connection conn = DatabaseManager.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement deleteStmt = conn.prepareStatement(deleteSql);
                 PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
                
                deleteStmt.setString(1, guildId);
                deleteStmt.setString(2, category);
                deleteStmt.executeUpdate();
                
                for (String roleId : roleIds) {
                    insertStmt.setString(1, guildId);
                    insertStmt.setString(2, category);
                    insertStmt.setString(3, roleId);
                    insertStmt.addBatch();
                }
                insertStmt.executeBatch();
                conn.commit();
                logger.info("Mise a jour support_roles reussi guildId={}, category={}, count={}", guildId, category, roleIds.size());
            } catch (SQLException e) {
                conn.rollback();
                logger.error("Erreur rollback lors de l'update de support_roles", e);
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            logger.error("Erreur BDD support_roles guildId={}", guildId, e);
        }
    }

    public boolean isGuildPremium(String guildId) {
        if (isDbNotReady()) return false;
        ensureGuildExists(guildId);
        String sql = "SELECT premium FROM settings WHERE guild_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, guildId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("premium") == 1;
                }
            }
        } catch (SQLException e) {
            logger.error("Erreur lecture premium guildId={}", guildId, e);
        }
        return false;
    }

    public void setGuildPremium(String guildId, boolean premium) {
        if (isDbNotReady()) return;
        ensureGuildExists(guildId);
        String sql = "UPDATE settings SET premium = ? WHERE guild_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, premium ? 1 : 0);
            pstmt.setString(2, guildId);
            pstmt.executeUpdate();
            logger.info("Statut premium={} mis a jour pour guildId={}", premium, guildId);
        } catch (SQLException e) {
            logger.error("Erreur update premium guildId={}", guildId, e);
        }
    }

    public java.util.List<fr.sakura.bot.core.model.TicketCategory> getCategories(String guildId) {
        if (isDbNotReady()) return getDefaultCategories();
        java.util.List<fr.sakura.bot.core.model.TicketCategory> list = new java.util.ArrayList<>();
        String sql = "SELECT category_id, label, description, emoji FROM ticket_categories WHERE guild_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, guildId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    list.add(new fr.sakura.bot.core.model.TicketCategory(
                            rs.getString("category_id"),
                            rs.getString("label"),
                            rs.getString("description"),
                            rs.getString("emoji")
                    ));
                }
            }
        } catch (SQLException e) {
            logger.error("Erreur lecture categories guildId={}", guildId, e);
        }

        if (list.isEmpty()) {
            return getDefaultCategories();
        }
        return list;
    }

    public Optional<String> getCategoryLabel(String guildId, String categoryId) {
        if (isDbNotReady()) return Optional.empty();
        String sql = "SELECT label FROM ticket_categories WHERE guild_id = ? AND category_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, guildId);
            pstmt.setString(2, categoryId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(rs.getString("label"));
                }
            }
        } catch (SQLException e) {
            logger.error("Erreur lecture label categorie guildId={}, categoryId={}", guildId, categoryId, e);
        }
        return Optional.empty();
    }

    public void addCategory(String guildId, String categoryId, String label, String description, String emoji) {
        if (isDbNotReady()) return;
        String sql = DatabaseManager.isPostgres()
                ? "INSERT INTO ticket_categories (guild_id, category_id, label, description, emoji) VALUES (?, ?, ?, ?, ?) " +
                  "ON CONFLICT (guild_id, category_id) DO UPDATE SET label = EXCLUDED.label, description = EXCLUDED.description, emoji = EXCLUDED.emoji"
                : "INSERT OR REPLACE INTO ticket_categories (guild_id, category_id, label, description, emoji) VALUES (?, ?, ?, ?, ?)";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, guildId);
            pstmt.setString(2, categoryId.toLowerCase());
            pstmt.setString(3, label);
            pstmt.setString(4, description);
            pstmt.setString(5, emoji);
            pstmt.executeUpdate();
            logger.info("Categorie ajoutee/mise a jour : {} pour guildId={}", categoryId, guildId);
        } catch (SQLException e) {
            logger.error("Erreur ajout categorie guildId={}, categoryId={}", guildId, categoryId, e);
        }
    }

    public void removeCategory(String guildId, String categoryId) {
        if (isDbNotReady()) return;
        String sql = "DELETE FROM ticket_categories WHERE guild_id = ? AND category_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, guildId);
            pstmt.setString(2, categoryId.toLowerCase());
            pstmt.executeUpdate();
            logger.info("Categorie supprimee : {} pour guildId={}", categoryId, guildId);
        } catch (SQLException e) {
            logger.error("Erreur suppression categorie guildId={}, categoryId={}", guildId, categoryId, e);
        }
    }

    private java.util.List<fr.sakura.bot.core.model.TicketCategory> getDefaultCategories() {
        return java.util.List.of(
            new fr.sakura.bot.core.model.TicketCategory("partnership", "Partenariat", "Proposer un partenariat avec le serveur", "🤝"),
            new fr.sakura.bot.core.model.TicketCategory("report", "Signalement", "Signaler un utilisateur ou un comportement", "🚨"),
            new fr.sakura.bot.core.model.TicketCategory("support", "Support", "Aide technique ou questions générales", "🛠️"),
            new fr.sakura.bot.core.model.TicketCategory("suggestion", "Suggestion", "Proposer une idée pour le serveur", "💡"),
            new fr.sakura.bot.core.model.TicketCategory("other", "Autre", "Toute autre demande", "❓")
        );
    }

    public void savePanel(String guildId, String channelId, String messageId) {
        if (isDbNotReady()) return;
        String sql = DatabaseManager.isPostgres()
                ? "INSERT INTO ticket_panels (guild_id, channel_id, message_id) VALUES (?, ?, ?) ON CONFLICT (guild_id, channel_id, message_id) DO NOTHING"
                : "INSERT OR IGNORE INTO ticket_panels (guild_id, channel_id, message_id) VALUES (?, ?, ?)";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, guildId);
            pstmt.setString(2, channelId);
            pstmt.setString(3, messageId);
            pstmt.executeUpdate();
            logger.info("Panel de ticket enregistre : channelId={}, messageId={}", channelId, messageId);
        } catch (SQLException e) {
            logger.error("Erreur enregistrement panel guildId={}", guildId, e);
        }
    }

    public void deletePanel(String guildId, String channelId, String messageId) {
        if (isDbNotReady()) return;
        String sql = "DELETE FROM ticket_panels WHERE guild_id = ? AND channel_id = ? AND message_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, guildId);
            pstmt.setString(2, channelId);
            pstmt.setString(3, messageId);
            pstmt.executeUpdate();
            logger.info("Panel de ticket supprime des enregistrements : channelId={}, messageId={}", channelId, messageId);
        } catch (SQLException e) {
            logger.error("Erreur suppression panel guildId={}", guildId, e);
        }
    }

    public java.util.List<fr.sakura.bot.core.model.PanelEntry> getPanels(String guildId) {
        if (isDbNotReady()) return java.util.Collections.emptyList();
        java.util.List<fr.sakura.bot.core.model.PanelEntry> list = new java.util.ArrayList<>();
        String sql = "SELECT channel_id, message_id FROM ticket_panels WHERE guild_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, guildId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    list.add(new fr.sakura.bot.core.model.PanelEntry(
                            rs.getString("channel_id"),
                            rs.getString("message_id")
                    ));
                }
            }
        } catch (SQLException e) {
            logger.error("Erreur lecture panels guildId={}", guildId, e);
        }
        return list;
    }
}
