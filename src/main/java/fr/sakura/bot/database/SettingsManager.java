package fr.sakura.bot.database;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

public class SettingsManager {

    private static final Logger logger = LoggerFactory.getLogger(SettingsManager.class);

    /**
     * Initialise par defaut les parametres d'une guilde si elle n'existe pas.
     */
    private void ensureGuildExists(String guildId) {
        String sql = DatabaseManager.isPostgres()
                ? "INSERT INTO settings (guild_id) VALUES (?) ON CONFLICT (guild_id) DO NOTHING"
                : "INSERT OR IGNORE INTO settings (guild_id) VALUES (?)";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, guildId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            logger.error("Erreur lors de l'initialisation des parametres pour guildId={}", guildId, e);
        }
    }

    public boolean isAntiSpamEnabled(String guildId) {
        ensureGuildExists(guildId);
        String sql = "SELECT anti_spam_enabled FROM settings WHERE guild_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, guildId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("anti_spam_enabled") == 1;
            }
        } catch (SQLException e) {
            logger.error("Erreur lecture anti_spam_enabled guildId={}", guildId, e);
        }
        return true;
    }

    public void setAntiSpamEnabled(String guildId, boolean enabled) {
        ensureGuildExists(guildId);
        String sql = "UPDATE settings SET anti_spam_enabled = ? WHERE guild_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, enabled ? 1 : 0);
            pstmt.setString(2, guildId);
            pstmt.executeUpdate();
            logger.info("Anti-spam {} pour guildId={}", enabled ? "active" : "desactive", guildId);
        } catch (SQLException e) {
            logger.error("Erreur update anti_spam_enabled guildId={}", guildId, e);
        }
    }

    public boolean isAntiLinkEnabled(String guildId) {
        ensureGuildExists(guildId);
        String sql = "SELECT anti_link_enabled FROM settings WHERE guild_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, guildId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("anti_link_enabled") == 1;
            }
        } catch (SQLException e) {
            logger.error("Erreur lecture anti_link_enabled guildId={}", guildId, e);
        }
        return true;
    }

    public void setAntiLinkEnabled(String guildId, boolean enabled) {
        ensureGuildExists(guildId);
        String sql = "UPDATE settings SET anti_link_enabled = ? WHERE guild_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, enabled ? 1 : 0);
            pstmt.setString(2, guildId);
            pstmt.executeUpdate();
            logger.info("Anti-liens {} pour guildId={}", enabled ? "active" : "desactive", guildId);
        } catch (SQLException e) {
            logger.error("Erreur update anti_link_enabled guildId={}", guildId, e);
        }
    }

    public boolean isGifLinksAllowed(String guildId) {
        ensureGuildExists(guildId);
        String sql = "SELECT allow_gif_links FROM settings WHERE guild_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, guildId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("allow_gif_links") == 1;
            }
        } catch (SQLException e) {
            logger.error("Erreur lecture allow_gif_links guildId={}", guildId, e);
        }
        return true;
    }

    public void setGifLinksAllowed(String guildId, boolean enabled) {
        ensureGuildExists(guildId);
        String sql = "UPDATE settings SET allow_gif_links = ? WHERE guild_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, enabled ? 1 : 0);
            pstmt.setString(2, guildId);
            pstmt.executeUpdate();
            logger.info("Liens GIF {} pour guildId={}", enabled ? "autorises" : "bloques", guildId);
        } catch (SQLException e) {
            logger.error("Erreur update allow_gif_links guildId={}", guildId, e);
        }
    }

    public int getSpamLimit(String guildId) {
        return getIntSetting(guildId, "spam_limit", 5, 3, 20);
    }

    public void setSpamLimit(String guildId, int value) {
        setIntSetting(guildId, "spam_limit", clamp(value, 3, 20));
    }

    public long getSpamWindowMs(String guildId) {
        return getIntSetting(guildId, "spam_window_ms", 5000, 2000, 15000);
    }

    public void setSpamWindowMs(String guildId, long value) {
        int bounded = (int) Math.max(2000, Math.min(15000, value));
        setIntSetting(guildId, "spam_window_ms", bounded);
    }

    public int getAutomodStrikesToTimeout(String guildId) {
        return getIntSetting(guildId, "automod_strikes_to_timeout", 3, 1, 10);
    }

    public void setAutomodStrikesToTimeout(String guildId, int value) {
        setIntSetting(guildId, "automod_strikes_to_timeout", clamp(value, 1, 10));
    }

    public int getAutomodTimeoutMinutes(String guildId) {
        return getIntSetting(guildId, "automod_timeout_minutes", 10, 1, 1440);
    }

    public void setAutomodTimeoutMinutes(String guildId, int value) {
        setIntSetting(guildId, "automod_timeout_minutes", clamp(value, 1, 1440));
    }

    public int getAutomodStrikeResetMinutes(String guildId) {
        return getIntSetting(guildId, "automod_strike_reset_minutes", 10, 1, 180);
    }

    public void setAutomodStrikeResetMinutes(String guildId, int value) {
        setIntSetting(guildId, "automod_strike_reset_minutes", clamp(value, 1, 180));
    }

    public int getAutomodNoticeCooldownSeconds(String guildId) {
        return getIntSetting(guildId, "automod_notice_cooldown_seconds", 15, 3, 120);
    }

    public void setAutomodNoticeCooldownSeconds(String guildId, int value) {
        setIntSetting(guildId, "automod_notice_cooldown_seconds", clamp(value, 3, 120));
    }

    public int getXpCooldownMs(String guildId) {
        return getIntSetting(guildId, "xp_cooldown_ms", 60_000, 5_000, 300_000);
    }

    public void setXpCooldownMs(String guildId, int valueMs) {
        setIntSetting(guildId, "xp_cooldown_ms", clamp(valueMs, 5_000, 300_000));
    }

    public int getXpMinMessageLength(String guildId) {
        return getIntSetting(guildId, "xp_min_message_length", 5, 1, 300);
    }

    public void setXpMinMessageLength(String guildId, int value) {
        setIntSetting(guildId, "xp_min_message_length", clamp(value, 1, 300));
    }

    public int getXpMinAlnumCount(String guildId) {
        return getIntSetting(guildId, "xp_min_alnum_count", 3, 1, 100);
    }

    public void setXpMinAlnumCount(String guildId, int value) {
        setIntSetting(guildId, "xp_min_alnum_count", clamp(value, 1, 100));
    }

    public int getXpMinGain(String guildId) {
        int min = getIntSetting(guildId, "xp_min_gain", 15, 1, 1000);
        int max = getXpMaxGain(guildId);
        return Math.min(min, max);
    }

    public int getXpMaxGain(String guildId) {
        int max = getIntSetting(guildId, "xp_max_gain", 25, 1, 1000);
        int min = getIntSetting(guildId, "xp_min_gain", 15, 1, 1000);
        return Math.max(max, min);
    }

    public void setXpGainRange(String guildId, int minGain, int maxGain) {
        int boundedMin = clamp(minGain, 1, 1000);
        int boundedMax = clamp(maxGain, 1, 1000);
        if (boundedMax < boundedMin) {
            int tmp = boundedMin;
            boundedMin = boundedMax;
            boundedMax = tmp;
        }
        setIntSetting(guildId, "xp_min_gain", boundedMin);
        setIntSetting(guildId, "xp_max_gain", boundedMax);
    }

    public Map<Integer, String> getLevelRoleMappings(String guildId) {
        Map<Integer, String> result = new LinkedHashMap<>();
        String sql = "SELECT level, role_id FROM level_roles WHERE guild_id = ? ORDER BY level ASC";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, guildId);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                result.put(rs.getInt("level"), rs.getString("role_id"));
            }
        } catch (SQLException e) {
            logger.error("Erreur lecture mappings level_roles guildId={}", guildId, e);
        }
        return result;
    }

    public String getLevelRoleId(String guildId, int level) {
        String sql = "SELECT role_id FROM level_roles WHERE guild_id = ? AND level = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, guildId);
            pstmt.setInt(2, level);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getString("role_id");
            }
        } catch (SQLException e) {
            logger.error("Erreur lecture level_role guildId={}, level={}", guildId, level, e);
        }
        return null;
    }

    public void setLevelRole(String guildId, int level, String roleId) {
        String sql = DatabaseManager.isPostgres()
                ? "INSERT INTO level_roles (guild_id, level, role_id) VALUES (?, ?, ?) ON CONFLICT (guild_id, level) DO UPDATE SET role_id = EXCLUDED.role_id"
                : "INSERT INTO level_roles (guild_id, level, role_id) VALUES (?, ?, ?) ON CONFLICT(guild_id, level) DO UPDATE SET role_id = excluded.role_id";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, guildId);
            pstmt.setInt(2, level);
            pstmt.setString(3, roleId);
            pstmt.executeUpdate();
            logger.info("Level role configure guildId={}, level={}, roleId={}", guildId, level, roleId);
        } catch (SQLException e) {
            logger.error("Erreur upsert level_role guildId={}, level={}", guildId, level, e);
        }
    }

    public void removeLevelRole(String guildId, int level) {
        String sql = "DELETE FROM level_roles WHERE guild_id = ? AND level = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, guildId);
            pstmt.setInt(2, level);
            pstmt.executeUpdate();
            logger.info("Level role supprime guildId={}, level={}", guildId, level);
        } catch (SQLException e) {
            logger.error("Erreur suppression level_role guildId={}, level={}", guildId, level, e);
        }
    }

    private int getIntSetting(String guildId, String field, int fallback, int min, int max) {
        ensureGuildExists(guildId);
        String sql = "SELECT " + field + " FROM settings WHERE guild_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, guildId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return clamp(rs.getInt(field), min, max);
            }
        } catch (SQLException e) {
            logger.error("Erreur lecture {} guildId={}", field, guildId, e);
        }
        return fallback;
    }

    private void setIntSetting(String guildId, String field, int value) {
        ensureGuildExists(guildId);
        String sql = "UPDATE settings SET " + field + " = ? WHERE guild_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, value);
            pstmt.setString(2, guildId);
            pstmt.executeUpdate();
            logger.info("Config {}={} pour guildId={}", field, value, guildId);
        } catch (SQLException e) {
            logger.error("Erreur update {} guildId={}", field, guildId, e);
        }
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
