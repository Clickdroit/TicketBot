package fr.sakura.bot.database;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Gère la configuration de la guilde en base de données.
 * Fail-safe : retourne les valeurs par défaut si la DB est offline.
 */
public class SettingsManager {

    private static final Logger logger = LoggerFactory.getLogger(SettingsManager.class);

    private static final Map<SettingKey, String> INT_GETTERS;
    private static final Map<SettingKey, String> INT_SETTERS;

    static {
        Map<SettingKey, String> getters = new HashMap<>();
        Map<SettingKey, String> setters = new HashMap<>();

        for (SettingKey key : SettingKey.values()) {
            getters.put(key, "SELECT " + key.getSqlColumn() + " FROM settings WHERE guild_id = ?");
            setters.put(key, "UPDATE settings SET " + key.getSqlColumn() + " = ? WHERE guild_id = ?");
        }

        INT_GETTERS = Collections.unmodifiableMap(getters);
        INT_SETTERS = Collections.unmodifiableMap(setters);
    }

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

    // --- Méthodes Génériques ---

    public int getIntSetting(String guildId, SettingKey key) {
        if (isDbNotReady()) return key.getDefaultValue();

        String sql = INT_GETTERS.get(key);
        ensureGuildExists(guildId);
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, guildId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    int val = rs.getInt(key.getSqlColumn());
                    if (rs.wasNull()) return key.getDefaultValue();
                    return clamp(val, key.getMin(), key.getMax());
                }
            }
        } catch (SQLException e) {
            logger.error("Erreur lecture {} guildId={} (retour default)", key.name(), guildId);
        }
        return key.getDefaultValue();
    }

    public void setIntSetting(String guildId, SettingKey key, int value) {
        if (isDbNotReady()) return;

        String sql = INT_SETTERS.get(key);
        ensureGuildExists(guildId);
        int bounded = clamp(value, key.getMin(), key.getMax());
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, bounded);
            pstmt.setString(2, guildId);
            pstmt.executeUpdate();
            logger.info("Config {}={} pour guildId={}", key.name(), bounded, guildId);
        } catch (SQLException e) {
            logger.error("Erreur update {} guildId={}", key.name(), guildId, e);
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

    public boolean isAntiSpamEnabled(String guildId) { return getIntSetting(guildId, SettingKey.ANTI_SPAM_ENABLED) == 1; }
    public void setAntiSpamEnabled(String guildId, boolean enabled) { setIntSetting(guildId, SettingKey.ANTI_SPAM_ENABLED, enabled ? 1 : 0); }

    public boolean isAntiLinkEnabled(String guildId) { return getIntSetting(guildId, SettingKey.ANTI_LINK_ENABLED) == 1; }
    public void setAntiLinkEnabled(String guildId, boolean enabled) { setIntSetting(guildId, SettingKey.ANTI_LINK_ENABLED, enabled ? 1 : 0); }

    public boolean isGifLinksAllowed(String guildId) { return getIntSetting(guildId, SettingKey.ALLOW_GIF_LINKS) == 1; }
    public void setGifLinksAllowed(String guildId, boolean enabled) { setIntSetting(guildId, SettingKey.ALLOW_GIF_LINKS, enabled ? 1 : 0); }

    public Optional<String> getLogChannelId(String guildId) { return getStringSetting(guildId, "log_channel_id"); }
    public void setLogChannelId(String guildId, String channelId) { setStringSetting(guildId, "log_channel_id", channelId); }

    public int getSpamLimit(String guildId) { return getIntSetting(guildId, SettingKey.SPAM_LIMIT); }
    public void setSpamLimit(String guildId, int value) { setIntSetting(guildId, SettingKey.SPAM_LIMIT, value); }

    public long getSpamWindowMs(String guildId) { return getIntSetting(guildId, SettingKey.SPAM_WINDOW_MS); }
    public void setSpamWindowMs(String guildId, long value) { setIntSetting(guildId, SettingKey.SPAM_WINDOW_MS, (int) value); }

    public int getAutomodStrikesToTimeout(String guildId) { return getIntSetting(guildId, SettingKey.AUTOMOD_STRIKES_TO_TIMEOUT); }
    public void setAutomodStrikesToTimeout(String guildId, int value) { setIntSetting(guildId, SettingKey.AUTOMOD_STRIKES_TO_TIMEOUT, value); }

    public int getAutomodTimeoutMinutes(String guildId) { return getIntSetting(guildId, SettingKey.AUTOMOD_TIMEOUT_MINUTES); }
    public void setAutomodTimeoutMinutes(String guildId, int value) { setIntSetting(guildId, SettingKey.AUTOMOD_TIMEOUT_MINUTES, value); }

    public int getAutomodStrikeResetMinutes(String guildId) { return getIntSetting(guildId, SettingKey.AUTOMOD_STRIKE_RESET_MINUTES); }
    public void setAutomodStrikeResetMinutes(String guildId, int value) { setIntSetting(guildId, SettingKey.AUTOMOD_STRIKE_RESET_MINUTES, value); }

    public int getAutomodNoticeCooldownSeconds(String guildId) { return getIntSetting(guildId, SettingKey.AUTOMOD_NOTICE_COOLDOWN_SECONDS); }
    public void setAutomodNoticeCooldownSeconds(String guildId, int value) { setIntSetting(guildId, SettingKey.AUTOMOD_NOTICE_COOLDOWN_SECONDS, value); }

    public boolean isLevelsEnabled(String guildId) { return getIntSetting(guildId, SettingKey.LEVELS_ENABLED) == 1; }
    public void setLevelsEnabled(String guildId, boolean enabled) { setIntSetting(guildId, SettingKey.LEVELS_ENABLED, enabled ? 1 : 0); }

    public int getXpCooldownMs(String guildId) { return getIntSetting(guildId, SettingKey.XP_COOLDOWN_MS); }
    public void setXpCooldownMs(String guildId, long valueMs) { setIntSetting(guildId, SettingKey.XP_COOLDOWN_MS, (int) valueMs); }

    public int getXpMinMessageLength(String guildId) { return getIntSetting(guildId, SettingKey.XP_MIN_MESSAGE_LENGTH); }
    public void setXpMinMessageLength(String guildId, int value) { setIntSetting(guildId, SettingKey.XP_MIN_MESSAGE_LENGTH, value); }

    public int getXpMinAlnumCount(String guildId) { return getIntSetting(guildId, SettingKey.XP_MIN_ALNUM_COUNT); }
    public void setXpMinAlnumCount(String guildId, int value) { setIntSetting(guildId, SettingKey.XP_MIN_ALNUM_COUNT, value); }

    public int getXpMinGain(String guildId) { return getIntSetting(guildId, SettingKey.XP_MIN_GAIN); }
    public int getXpMaxGain(String guildId) { return getIntSetting(guildId, SettingKey.XP_MAX_GAIN); }

    public void setXpGainRange(String guildId, int minGain, int maxGain) {
        setIntSetting(guildId, SettingKey.XP_MIN_GAIN, minGain);
        setIntSetting(guildId, SettingKey.XP_MAX_GAIN, maxGain);
    }

    public Optional<String> getWelcomeChannelId(String guildId) { return getStringSetting(guildId, "welcome_channel_id"); }
    public void setWelcomeChannelId(String guildId, String channelId) { setStringSetting(guildId, "welcome_channel_id", channelId); }

    public Optional<String> getWelcomeImageUrl(String guildId) { return getStringSetting(guildId, "welcome_image_url"); }
    public void setWelcomeImageUrl(String guildId, String imageUrl) { setStringSetting(guildId, "welcome_image_url", imageUrl); }

    public Optional<String> getTranscriptChannelId(String guildId) { return getStringSetting(guildId, "transcript_channel_id"); }
    public void setTranscriptChannelId(String guildId, String channelId) { setStringSetting(guildId, "transcript_channel_id", channelId); }

    public boolean isAutoSlowmodeEnabled(String guildId) { return getIntSetting(guildId, SettingKey.AUTO_SLOWMODE_ENABLED) == 1; }
    public int getAutoSlowmodeThreshold(String guildId) { return getIntSetting(guildId, SettingKey.AUTO_SLOWMODE_THRESHOLD); }
    public int getAutoSlowmodeDuration(String guildId) { return getIntSetting(guildId, SettingKey.AUTO_SLOWMODE_DURATION); }

    // --- Gestion des Rôles de Niveau ---

    public Map<Integer, String> getLevelRoleMappings(String guildId) {
        if (isDbNotReady()) return Collections.emptyMap();

        Map<Integer, String> result = new LinkedHashMap<>();
        String sql = "SELECT level, role_id FROM level_roles WHERE guild_id = ? ORDER BY level ASC";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, guildId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    result.put(rs.getInt("level"), rs.getString("role_id"));
                }
            }
        } catch (SQLException e) {
            logger.error("Erreur lecture mappings level_roles guildId={}", guildId);
        }
        return result;
    }

    public String getLevelRoleId(String guildId, int level) {
        if (isDbNotReady()) return null;

        String sql = "SELECT role_id FROM level_roles WHERE guild_id = ? AND level = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, guildId);
            pstmt.setInt(2, level);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("role_id");
                }
            }
        } catch (SQLException e) {
            logger.error("Erreur lecture level_role guildId={}, level={}", guildId, level);
        }
        return null;
    }

    public void setLevelRole(String guildId, int level, String roleId) {
        if (isDbNotReady()) return;

        String sql = DatabaseManager.isPostgres()
                ? "INSERT INTO level_roles (guild_id, level, role_id) VALUES (?, ?, ?) ON CONFLICT (guild_id, level) DO UPDATE SET role_id = EXCLUDED.role_id"
                : "INSERT INTO level_roles (guild_id, level, role_id) VALUES (?, ?, ?) ON CONFLICT(guild_id, level) DO UPDATE SET role_id = excluded.role_id";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, guildId);
            pstmt.setInt(2, level);
            pstmt.setString(3, roleId);
            pstmt.executeUpdate();
            logger.info("Level role configuré guildId={}, level={}, roleId={}", guildId, level, roleId);
        } catch (SQLException e) {
            logger.error("Erreur upsert level_role guildId={}, level={}", guildId, level, e);
        }
    }

    public void removeLevelRole(String guildId, int level) {
        if (isDbNotReady()) return;

        String sql = "DELETE FROM level_roles WHERE guild_id = ? AND level = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, guildId);
            pstmt.setInt(2, level);
            pstmt.executeUpdate();
            logger.info("Level role supprimé guildId={}, level={}", guildId, level);
        } catch (SQLException e) {
            logger.error("Erreur suppression level_role guildId={}, level={}", guildId, level, e);
        }
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
