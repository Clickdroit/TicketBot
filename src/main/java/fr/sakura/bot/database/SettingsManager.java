package fr.sakura.bot.database;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SettingsManager {

    private static final Logger logger = LoggerFactory.getLogger(SettingsManager.class);

    /**
     * Initialise par defaut les parametres d'une guilde si elle n'existe pas.
     */
    private void ensureGuildExists(String guildId) {
        String sql = "INSERT OR IGNORE INTO settings (guild_id) VALUES (?)";
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
        return true; // Par defaut, on active
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
        int raw = getIntSetting(guildId, "spam_window_ms", 5000, 2000, 15000);
        return raw;
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

    public List<String> getIgnoredChannels(String guildId) {
        ensureGuildExists(guildId);
        String sql = "SELECT ignored_channels FROM settings WHERE guild_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, guildId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                String val = rs.getString("ignored_channels");
                if (val == null || val.isBlank()) return new ArrayList<>();
                return new ArrayList<>(Arrays.asList(val.split(",")));
            }
        } catch (SQLException e) {
            logger.error("Erreur lecture ignored_channels guildId={}", guildId, e);
        }
        return new ArrayList<>();
    }

    public void addIgnoredChannel(String guildId, String channelId) {
        List<String> ignored = getIgnoredChannels(guildId);
        if (!ignored.contains(channelId)) {
            ignored.add(channelId);
            updateIgnoredChannels(guildId, ignored);
        }
    }

    public void removeIgnoredChannel(String guildId, String channelId) {
        List<String> ignored = getIgnoredChannels(guildId);
        if (ignored.remove(channelId)) {
            updateIgnoredChannels(guildId, ignored);
        }
    }

    private void updateIgnoredChannels(String guildId, List<String> ignored) {
        String val = String.join(",", ignored);
        ensureGuildExists(guildId);
        String sql = "UPDATE settings SET ignored_channels = ? WHERE guild_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, val);
            pstmt.setString(2, guildId);
            pstmt.executeUpdate();
            logger.info("Ignored channels mis a jour pour guildId={}", guildId);
        } catch (SQLException e) {
            logger.error("Erreur update ignored_channels guildId={}", guildId, e);
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
