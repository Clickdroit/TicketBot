package fr.sakura.bot.database;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class ProtectSettingsManager {

    private static final Logger logger = LoggerFactory.getLogger(ProtectSettingsManager.class);

    private final Cache<String, Object> cache = Caffeine.newBuilder()
            .expireAfterWrite(30, TimeUnit.SECONDS)
            .maximumSize(1000)
            .build();

    private String cacheKey(String guildId, String column) {
        return guildId + ":" + column;
    }

    private void ensureGuildExists(String guildId) {
        String sql = DatabaseManager.isPostgres()
                ? "INSERT INTO protect_settings (guild_id) VALUES (?) ON CONFLICT (guild_id) DO NOTHING"
                : "INSERT OR IGNORE INTO protect_settings (guild_id) VALUES (?)";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, guildId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            logger.error("Erreur lors de l'initialisation des protect_settings pour guildId={}", guildId, e);
        }
    }

    public List<String> getWhitelist(String guildId) {
        return getCsvSetting(guildId, "whitelist");
    }

    public void addToWhitelist(String guildId, String userId) {
        addCsvValue(guildId, "whitelist", userId);
    }

    public void removeFromWhitelist(String guildId, String userId) {
        removeCsvValue(guildId, "whitelist", userId);
    }

    public List<String> getTrustedRoleIds(String guildId) {
        return getCsvSetting(guildId, "trusted_role_ids");
    }

    public void addTrustedRoleId(String guildId, String roleId) {
        addCsvValue(guildId, "trusted_role_ids", roleId);
    }

    public void removeTrustedRoleId(String guildId, String roleId) {
        removeCsvValue(guildId, "trusted_role_ids", roleId);
    }

    public List<String> getPhishingAllowlist(String guildId) {
        return getCsvSetting(guildId, "phishing_allowlist");
    }

    public void addPhishingAllowDomain(String guildId, String domain) {
        addCsvValue(guildId, "phishing_allowlist", normalizeDomain(domain));
    }

    public void removePhishingAllowDomain(String guildId, String domain) {
        removeCsvValue(guildId, "phishing_allowlist", normalizeDomain(domain));
    }

    public boolean isAntiBotEnabled(String guildId) {
        return getBooleanSetting(guildId, "anti_bot_enabled", true);
    }

    public void setAntiBotEnabled(String guildId, boolean enabled) {
        setBooleanSetting(guildId, "anti_bot_enabled", enabled);
    }

    public boolean isAntiRaidEnabled(String guildId) {
        return getBooleanSetting(guildId, "anti_raid_enabled", true);
    }

    public void setAntiRaidEnabled(String guildId, boolean enabled) {
        setBooleanSetting(guildId, "anti_raid_enabled", enabled);
    }

    public boolean isAntiPhishingEnabled(String guildId) {
        return getBooleanSetting(guildId, "anti_phishing_enabled", true);
    }

    public void setAntiPhishingEnabled(String guildId, boolean enabled) {
        setBooleanSetting(guildId, "anti_phishing_enabled", enabled);
    }

    public int getMinAccountAgeHours(String guildId) {
        return getIntSetting(guildId, "min_account_age_hours", 24);
    }

    public void setMinAccountAgeHours(String guildId, int hours) {
        setIntSetting(guildId, "min_account_age_hours", Math.max(0, hours));
    }

    public int getRaidJoinThreshold(String guildId) {
        return getIntSetting(guildId, "raid_join_threshold", 10);
    }

    public void setRaidJoinThreshold(String guildId, int threshold) {
        setIntSetting(guildId, "raid_join_threshold", Math.max(3, threshold));
    }

    public int getRaidWindowSeconds(String guildId) {
        return getIntSetting(guildId, "raid_window_seconds", 60);
    }

    public void setRaidWindowSeconds(String guildId, int seconds) {
        setIntSetting(guildId, "raid_window_seconds", Math.max(10, seconds));
    }

    public int getRaidModeDurationSeconds(String guildId) {
        return getIntSetting(guildId, "raid_mode_duration_seconds", 300);
    }

    public void setRaidModeDurationSeconds(String guildId, int seconds) {
        setIntSetting(guildId, "raid_mode_duration_seconds", Math.max(30, seconds));
    }

    public boolean isRaidModeActive(String guildId) {
        return getBooleanSetting(guildId, "raid_mode_active", false);
    }

    public void setRaidModeActive(String guildId, boolean active) {
        setBooleanSetting(guildId, "raid_mode_active", active);
    }

    public long getRaidModeUntil(String guildId) {
        String key = cacheKey(guildId, "raid_mode_until");
        Long cached = (Long) cache.getIfPresent(key);
        if (cached != null) return cached;

        ensureGuildExists(guildId);
        String sql = "SELECT raid_mode_until FROM protect_settings WHERE guild_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, guildId);
            ResultSet rs = pstmt.executeQuery();
            long value = 0;
            if (rs.next()) {
                value = rs.getLong("raid_mode_until");
            }
            cache.put(key, value);
            return value;
        } catch (SQLException e) {
            logger.error("Erreur lecture raid_mode_until guildId={}", guildId, e);
        }
        return 0;
    }

    public void setRaidModeUntil(String guildId, long until) {
        ensureGuildExists(guildId);
        String sql = "UPDATE protect_settings SET raid_mode_until = ? WHERE guild_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, until);
            pstmt.setString(2, guildId);
            pstmt.executeUpdate();
            cache.invalidate(cacheKey(guildId, "raid_mode_until"));
        } catch (SQLException e) {
            logger.error("Erreur update raid_mode_until guildId={}", guildId, e);
        }
    }

    public String getQuarantineRoleId(String guildId) {
        String key = cacheKey(guildId, "quarantine_role_id");
        String cached = (String) cache.getIfPresent(key);
        if (cached != null) return cached.equals("__NULL__") ? null : cached;

        ensureGuildExists(guildId);
        String sql = "SELECT quarantine_role_id FROM protect_settings WHERE guild_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, guildId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                String roleId = rs.getString("quarantine_role_id");
                String value = (roleId == null || roleId.isBlank()) ? null : roleId;
                cache.put(key, value == null ? "__NULL__" : value);
                return value;
            }
        } catch (SQLException e) {
            logger.error("Erreur lecture quarantine_role_id guildId={}", guildId, e);
        }
        return null;
    }

    public void setQuarantineRoleId(String guildId, String roleId) {
        ensureGuildExists(guildId);
        String sql = "UPDATE protect_settings SET quarantine_role_id = ? WHERE guild_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            if (roleId == null || roleId.isBlank()) {
                pstmt.setNull(1, java.sql.Types.VARCHAR);
            } else {
                pstmt.setString(1, roleId);
            }
            pstmt.setString(2, guildId);
            pstmt.executeUpdate();
            cache.invalidate(cacheKey(guildId, "quarantine_role_id"));
        } catch (SQLException e) {
            logger.error("Erreur update quarantine_role_id guildId={}", guildId, e);
        }
    }

    private boolean getBooleanSetting(String guildId, String column, boolean defaultValue) {
        String key = cacheKey(guildId, column);
        Boolean cached = (Boolean) cache.getIfPresent(key);
        if (cached != null) return cached;

        ensureGuildExists(guildId);
        String sql = "SELECT " + column + " FROM protect_settings WHERE guild_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, guildId);
            ResultSet rs = pstmt.executeQuery();
            boolean value = defaultValue;
            if (rs.next()) {
                value = rs.getInt(column) == 1;
            }
            cache.put(key, value);
            return value;
        } catch (SQLException e) {
            logger.error("Erreur lecture {} guildId={}", column, guildId, e);
        }
        return defaultValue;
    }

    private void setBooleanSetting(String guildId, String column, boolean value) {
        ensureGuildExists(guildId);
        String sql = "UPDATE protect_settings SET " + column + " = ? WHERE guild_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, value ? 1 : 0);
            pstmt.setString(2, guildId);
            pstmt.executeUpdate();
            cache.invalidate(cacheKey(guildId, column));
        } catch (SQLException e) {
            logger.error("Erreur update {} guildId={}", column, guildId, e);
        }
    }

    private int getIntSetting(String guildId, String column, int defaultValue) {
        String key = cacheKey(guildId, column);
        Integer cached = (Integer) cache.getIfPresent(key);
        if (cached != null) return cached;

        ensureGuildExists(guildId);
        String sql = "SELECT " + column + " FROM protect_settings WHERE guild_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, guildId);
            ResultSet rs = pstmt.executeQuery();
            int value = defaultValue;
            if (rs.next()) {
                value = rs.getInt(column);
            }
            cache.put(key, value);
            return value;
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
            cache.invalidate(cacheKey(guildId, column));
        } catch (SQLException e) {
            logger.error("Erreur update {} guildId={}", column, guildId, e);
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> getCsvSetting(String guildId, String column) {
        String key = cacheKey(guildId, column);
        List<String> cached = (List<String>) cache.getIfPresent(key);
        if (cached != null) return cached;

        ensureGuildExists(guildId);
        String sql = "SELECT " + column + " FROM protect_settings WHERE guild_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, guildId);
            ResultSet rs = pstmt.executeQuery();
            List<String> value = new ArrayList<>();
            if (rs.next()) {
                value = parseCsv(rs.getString(column));
            }
            cache.put(key, value);
            return value;
        } catch (SQLException e) {
            logger.error("Erreur lecture {} guildId={}", column, guildId, e);
        }
        return new ArrayList<>();
    }

    private void addCsvValue(String guildId, String column, String rawValue) {
        if (rawValue == null || rawValue.isBlank()) return;
        List<String> values = getCsvSetting(guildId, column);
        if (!values.contains(rawValue)) {
            values.add(rawValue);
            setCsvSetting(guildId, column, values);
        }
    }

    private void removeCsvValue(String guildId, String column, String rawValue) {
        if (rawValue == null || rawValue.isBlank()) return;
        List<String> values = getCsvSetting(guildId, column);
        if (values.remove(rawValue)) {
            setCsvSetting(guildId, column, values);
        }
    }

    private void setCsvSetting(String guildId, String column, List<String> values) {
        ensureGuildExists(guildId);
        String sql = "UPDATE protect_settings SET " + column + " = ? WHERE guild_id = ?";
        String serialized = values.stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.joining(","));
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, serialized);
            pstmt.setString(2, guildId);
            pstmt.executeUpdate();
            cache.invalidate(cacheKey(guildId, column));
        } catch (SQLException e) {
            logger.error("Erreur update {} guildId={}", column, guildId, e);
        }
    }

    private List<String> parseCsv(String csv) {
        if (csv == null || csv.isBlank()) {
            return new ArrayList<>();
        }
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String token : csv.split(",")) {
            String value = token.trim();
            if (!value.isEmpty()) {
                unique.add(value);
            }
        }
        return new ArrayList<>(unique);
    }

    private String normalizeDomain(String domain) {
        if (domain == null) return "";
        String normalized = domain.trim().toLowerCase();
        if (normalized.startsWith("http://")) normalized = normalized.substring(7);
        if (normalized.startsWith("https://")) normalized = normalized.substring(8);
        int slashIdx = normalized.indexOf('/');
        if (slashIdx >= 0) normalized = normalized.substring(0, slashIdx);
        if (normalized.startsWith("www.")) normalized = normalized.substring(4);
        if (normalized.endsWith(".")) normalized = normalized.substring(0, normalized.length() - 1);
        return normalized;
    }
}