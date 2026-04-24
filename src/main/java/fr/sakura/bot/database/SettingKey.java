package fr.sakura.bot.database;

/**
 * Définit les clés de configuration numérique avec leurs métadonnées.
 */
public enum SettingKey {
    ANTI_SPAM_ENABLED("anti_spam_enabled", 1, 0, 1),
    ANTI_LINK_ENABLED("anti_link_enabled", 0, 0, 1),
    ALLOW_GIF_LINKS("allow_gif_links", 1, 0, 1),
    SPAM_LIMIT("spam_limit", 5, 3, 20),
    SPAM_WINDOW_MS("spam_window_ms", 5000, 2000, 15000),
    AUTOMOD_STRIKES_TO_TIMEOUT("automod_strikes_to_timeout", 3, 1, 10),
    AUTOMOD_TIMEOUT_MINUTES("automod_timeout_minutes", 10, 1, 1440),
    AUTOMOD_STRIKE_RESET_MINUTES("automod_strike_reset_minutes", 10, 1, 180),
    AUTOMOD_NOTICE_COOLDOWN_SECONDS("automod_notice_cooldown_seconds", 15, 3, 120),
    LEVELS_ENABLED("levels_enabled", 1, 0, 1),
    XP_COOLDOWN_MS("xp_cooldown_ms", 60000, 5000, 300000),
    XP_MIN_MESSAGE_LENGTH("xp_min_message_length", 5, 1, 300),
    XP_MIN_ALNUM_COUNT("xp_min_alnum_count", 3, 1, 100),
    XP_MIN_GAIN("xp_min_gain", 15, 1, 1000),
    XP_MAX_GAIN("xp_max_gain", 25, 1, 1000);

    private final String sqlColumn;
    private final int defaultValue;
    private final int min;
    private final int max;

    SettingKey(String sqlColumn, int defaultValue, int min, int max) {
        this.sqlColumn = sqlColumn;
        this.defaultValue = defaultValue;
        this.min = min;
        this.max = max;
    }

    public String getSqlColumn() { return sqlColumn; }
    public int getDefaultValue() { return defaultValue; }
    public int getMin() { return min; }
    public int getMax() { return max; }
}
