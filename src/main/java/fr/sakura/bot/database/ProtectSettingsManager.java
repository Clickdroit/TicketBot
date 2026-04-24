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
import java.util.stream.Collectors;

public class ProtectSettingsManager {

    private static final Logger logger = LoggerFactory.getLogger(ProtectSettingsManager.class);

    private void ensureGuildExists(String guildId) {
        String sql = "INSERT OR IGNORE INTO protect_settings (guild_id) VALUES (?)";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, guildId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            logger.error("Erreur lors de l'initialisation des protect_settings pour guildId={}", guildId, e);
        }
    }

    public List<String> getWhitelist(String guildId) {
        ensureGuildExists(guildId);
        String sql = "SELECT whitelist FROM protect_settings WHERE guild_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, guildId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                String val = rs.getString("whitelist");
                if (val == null || val.isBlank()) return new ArrayList<>();
                return new ArrayList<>(Arrays.asList(val.split(",")));
            }
        } catch (SQLException e) {
            logger.error("Erreur lecture whitelist guildId={}", guildId, e);
        }
        return new ArrayList<>();
    }

    public void addToWhitelist(String guildId, String userId) {
        List<String> whitelist = getWhitelist(guildId);
        if (!whitelist.contains(userId)) {
            whitelist.add(userId);
            updateWhitelist(guildId, whitelist);
        }
    }

    public void removeFromWhitelist(String guildId, String userId) {
        List<String> whitelist = getWhitelist(guildId);
        if (whitelist.remove(userId)) {
            updateWhitelist(guildId, whitelist);
        }
    }

    private void updateWhitelist(String guildId, List<String> whitelist) {
        String val = String.join(",", whitelist);
        String sql = "UPDATE protect_settings SET whitelist = ? WHERE guild_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, val);
            pstmt.setString(2, guildId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            logger.error("Erreur update whitelist guildId={}", guildId, e);
        }
    }

    public boolean isAntiBotEnabled(String guildId) {
        return getBooleanSetting(guildId, "anti_bot_enabled");
    }

    public void setAntiBotEnabled(String guildId, boolean enabled) {
        setBooleanSetting(guildId, "anti_bot_enabled", enabled);
    }

    public boolean isAntiRaidEnabled(String guildId) {
        return getBooleanSetting(guildId, "anti_raid_enabled");
    }

    public void setAntiRaidEnabled(String guildId, boolean enabled) {
        setBooleanSetting(guildId, "anti_raid_enabled", enabled);
    }

    public boolean isAntiPhishingEnabled(String guildId) {
        return getBooleanSetting(guildId, "anti_phishing_enabled");
    }

    public void setAntiPhishingEnabled(String guildId, boolean enabled) {
        setBooleanSetting(guildId, "anti_phishing_enabled", enabled);
    }

    public int getMinAccountAgeHours(String guildId) {
        return getIntSetting(guildId, "min_account_age_hours", 24);
    }

    public void setMinAccountAgeHours(String guildId, int hours) {
        setIntSetting(guildId, "min_account_age_hours", hours);
    }

    private boolean getBooleanSetting(String guildId, String column) {
        ensureGuildExists(guildId);
        String sql = "SELECT " + column + " FROM protect_settings WHERE guild_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, guildId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getInt(column) == 1;
            }
        } catch (SQLException e) {
            logger.error("Erreur lecture {} guildId={}", column, guildId, e);
        }
        return false;
    }

    private void setBooleanSetting(String guildId, String column, boolean value) {
        ensureGuildExists(guildId);
        String sql = "UPDATE protect_settings SET " + column + " = ? WHERE guild_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, value ? 1 : 0);
            pstmt.setString(2, guildId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            logger.error("Erreur update {} guildId={}", column, guildId, e);
        }
    }

    private int getIntSetting(String guildId, String column, int defaultValue) {
        ensureGuildExists(guildId);
        String sql = "SELECT " + column + " FROM protect_settings WHERE guild_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, guildId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getInt(column);
            }
        } catch (SQLException e) {
            logger.error("Erreur lecture {} guildId={}", column, guildId, e);
        }
        return defaultValue;
    }

    private void setIntSetting(String guildId, String column, int value) {
        ensureGuildExists(guildId);
        String sql = "UPDATE protect_settings SET " + column + " = ? WHERE guild_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, value);
            pstmt.setString(2, guildId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            logger.error("Erreur update {} guildId={}", column, guildId, e);
        }
    }
}
