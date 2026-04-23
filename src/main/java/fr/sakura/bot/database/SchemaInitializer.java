package fr.sakura.bot.database;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Gère l'initialisation du schéma et les migrations versionnées.
 */
public class SchemaInitializer {
    private static final Logger logger = LoggerFactory.getLogger(SchemaInitializer.class);

    public static void initializeSchema(Connection conn, boolean isPostgres) throws SQLException {
        ensureSchemaMigrationsTable(conn);
        applyMigrations(conn, isPostgres);
    }

    private static void applyMigrations(Connection conn, boolean isPostgres) throws SQLException {
        applyMigration(conn, 1, "create core tables", () -> {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute(createWarningsTableSql(isPostgres));
                stmt.execute(createSettingsTableSql());
                stmt.execute(createLevelsTableSql());
                stmt.execute(createTicketsTableSql(isPostgres));
                stmt.execute(createLevelRolesTableSql());
            }
        });

        applyMigration(conn, 2, "add settings and tickets columns", () -> {
            addColumnIfMissing(conn, "settings", "anti_link_enabled", "INTEGER DEFAULT 1", isPostgres);
            addColumnIfMissing(conn, "settings", "allow_gif_links", "INTEGER DEFAULT 1", isPostgres);
            addColumnIfMissing(conn, "settings", "spam_limit", "INTEGER DEFAULT 5", isPostgres);
            addColumnIfMissing(conn, "settings", "spam_window_ms", "INTEGER DEFAULT 5000", isPostgres);
            addColumnIfMissing(conn, "settings", "automod_strikes_to_timeout", "INTEGER DEFAULT 3", isPostgres);
            addColumnIfMissing(conn, "settings", "automod_timeout_minutes", "INTEGER DEFAULT 10", isPostgres);
            addColumnIfMissing(conn, "settings", "automod_strike_reset_minutes", "INTEGER DEFAULT 10", isPostgres);
            addColumnIfMissing(conn, "settings", "automod_notice_cooldown_seconds", "INTEGER DEFAULT 15", isPostgres);
            addColumnIfMissing(conn, "settings", "xp_cooldown_ms", "INTEGER DEFAULT 60000", isPostgres);
            addColumnIfMissing(conn, "settings", "xp_min_message_length", "INTEGER DEFAULT 5", isPostgres);
            addColumnIfMissing(conn, "settings", "xp_min_alnum_count", "INTEGER DEFAULT 3", isPostgres);
            addColumnIfMissing(conn, "settings", "xp_min_gain", "INTEGER DEFAULT 15", isPostgres);
            addColumnIfMissing(conn, "settings", "xp_max_gain", "INTEGER DEFAULT 25", isPostgres);

            addColumnIfMissing(conn, "tickets", "created_at", "TEXT", isPostgres);
            addColumnIfMissing(conn, "tickets", "claimed_by", "TEXT", isPostgres);
            addColumnIfMissing(conn, "tickets", "claimed_at", "TEXT", isPostgres);
            addColumnIfMissing(conn, "tickets", "closed_by", "TEXT", isPostgres);
            addColumnIfMissing(conn, "tickets", "closed_at", "TEXT", isPostgres);
            addColumnIfMissing(conn, "tickets", "close_reason", "TEXT", isPostgres);
        });

        applyMigration(conn, 3, "create performance indexes", () -> {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_warnings_guild_user_time ON warnings(guild_id, user_id, timestamp)");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_levels_guild_level_xp ON levels(guild_id, level DESC, xp DESC)");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_tickets_guild_status_created ON tickets(guild_id, status, created_at)");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_tickets_channel ON tickets(guild_id, channel_id)");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_level_roles_guild_level ON level_roles(guild_id, level)");
                stmt.execute("CREATE UNIQUE INDEX IF NOT EXISTS uq_tickets_single_active_per_user ON tickets(guild_id, user_id) WHERE status IN ('OPEN', 'CLAIMED')");
            }
        });

        applyMigration(conn, 4, "welcome settings and role panels", () -> {
            addColumnIfMissing(conn, "settings", "welcome_channel_id", "TEXT", isPostgres);
            addColumnIfMissing(conn, "settings", "welcome_image_url", "TEXT", isPostgres);
            try (Statement stmt = conn.createStatement()) {
                stmt.execute(createRolePanelsTableSql(isPostgres));
                stmt.execute(createRolePanelButtonsTableSql(isPostgres));
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_role_panels_guild ON role_panels(guild_id)");
                stmt.execute("CREATE UNIQUE INDEX IF NOT EXISTS uq_role_panel_buttons_panel_role ON role_panel_buttons(panel_id, role_id)");
            }
        });

        applyMigration(conn, 5, "add levels_enabled toggle", () -> {
            addColumnIfMissing(conn, "settings", "levels_enabled", "INTEGER DEFAULT 1", isPostgres);
        });
    }

    private static void applyMigration(Connection conn, int version, String description, Migration migration) throws SQLException {
        if (isMigrationApplied(conn, version)) {
            return;
        }

        migration.apply();
        recordMigration(conn, version, description);
        logger.info("Migration appliquee v{}: {}", version, description);
    }

    private static boolean isMigrationApplied(Connection conn, int version) throws SQLException {
        try (PreparedStatement pstmt = conn.prepareStatement("SELECT 1 FROM schema_migrations WHERE version = ?")) {
            pstmt.setInt(1, version);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static void recordMigration(Connection conn, int version, String description) throws SQLException {
        try (PreparedStatement pstmt = conn.prepareStatement(
                "INSERT INTO schema_migrations (version, description, applied_at) VALUES (?, ?, CURRENT_TIMESTAMP)")) {
            pstmt.setInt(1, version);
            pstmt.setString(2, description);
            pstmt.executeUpdate();
        }
    }

    private static void ensureSchemaMigrationsTable(Connection conn) throws SQLException {
        String sql = "CREATE TABLE IF NOT EXISTS schema_migrations (" +
                "version INTEGER PRIMARY KEY," +
                "description TEXT NOT NULL," +
                "applied_at TEXT NOT NULL" +
                ")";
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        }
    }

    private static void addColumnIfMissing(Connection conn, String tableName, String columnName, String columnDefinition, boolean isPostgres) throws SQLException {
        if (hasColumn(conn, tableName, columnName, isPostgres)) {
            return;
        }

        String sql = "ALTER TABLE " + tableName + " ADD COLUMN " + columnName + " " + columnDefinition;
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        }
        logger.info("Migration: colonne ajoutee {}.{}", tableName, columnName);
    }

    private static boolean hasColumn(Connection conn, String tableName, String columnName, boolean isPostgres) throws SQLException {
        DatabaseMetaData metaData = conn.getMetaData();

        if (isPostgres) {
            try (ResultSet rs = metaData.getColumns(null, "public", tableName, columnName)) {
                if (rs.next()) return true;
            }
            try (ResultSet rs = metaData.getColumns(null, "public", tableName.toLowerCase(), columnName.toLowerCase())) {
                if (rs.next()) return true;
            }
        }

        try (ResultSet rs = metaData.getColumns(null, null, tableName, columnName)) {
            if (rs.next()) return true;
        }
        try (ResultSet rs = metaData.getColumns(null, null, tableName.toLowerCase(), columnName.toLowerCase())) {
            return rs.next();
        }
    }

    private static String createWarningsTableSql(boolean isPostgres) {
        if (isPostgres) {
            return "CREATE TABLE IF NOT EXISTS warnings (" +
                    "id BIGSERIAL PRIMARY KEY," +
                    "guild_id TEXT NOT NULL," +
                    "user_id TEXT NOT NULL," +
                    "reason TEXT," +
                    "timestamp TEXT," +
                    "moderator_id TEXT" +
                    ");";
        }
        return "CREATE TABLE IF NOT EXISTS warnings (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "guild_id TEXT NOT NULL," +
                "user_id TEXT NOT NULL," +
                "reason TEXT," +
                "timestamp TEXT," +
                "moderator_id TEXT" +
                ");";
    }

    private static String createSettingsTableSql() {
        return "CREATE TABLE IF NOT EXISTS settings (" +
                "guild_id TEXT PRIMARY KEY," +
                "anti_spam_enabled INTEGER DEFAULT 1," +
                "anti_link_enabled INTEGER DEFAULT 1," +
                "allow_gif_links INTEGER DEFAULT 1," +
                "spam_limit INTEGER DEFAULT 5," +
                "spam_window_ms INTEGER DEFAULT 5000," +
                "automod_strikes_to_timeout INTEGER DEFAULT 3," +
                "automod_timeout_minutes INTEGER DEFAULT 10," +
                "automod_strike_reset_minutes INTEGER DEFAULT 10," +
                "automod_notice_cooldown_seconds INTEGER DEFAULT 15," +
                "levels_enabled INTEGER DEFAULT 1," +
                "xp_cooldown_ms INTEGER DEFAULT 60000," +
                "xp_min_message_length INTEGER DEFAULT 5," +
                "xp_min_alnum_count INTEGER DEFAULT 3," +
                "xp_min_gain INTEGER DEFAULT 15," +
                "xp_max_gain INTEGER DEFAULT 25," +
                "log_channel_id TEXT," +
                "welcome_channel_id TEXT," +
                "welcome_image_url TEXT" +
                ");";
    }

    private static String createLevelsTableSql() {
        return "CREATE TABLE IF NOT EXISTS levels (" +
                "guild_id TEXT NOT NULL," +
                "user_id TEXT NOT NULL," +
                "xp INTEGER DEFAULT 0," +
                "level INTEGER DEFAULT 0," +
                "PRIMARY KEY (guild_id, user_id)" +
                ");";
    }

    private static String createTicketsTableSql(boolean isPostgres) {
        if (isPostgres) {
            return "CREATE TABLE IF NOT EXISTS tickets (" +
                    "id BIGSERIAL PRIMARY KEY," +
                    "guild_id TEXT NOT NULL," +
                    "user_id TEXT NOT NULL," +
                    "channel_id TEXT NOT NULL," +
                    "status TEXT NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'CLAIMED', 'CLOSED'))," +
                    "created_at TEXT," +
                    "claimed_by TEXT," +
                    "claimed_at TEXT," +
                    "closed_by TEXT," +
                    "closed_at TEXT," +
                    "close_reason TEXT" +
                    ");";
        }
        return "CREATE TABLE IF NOT EXISTS tickets (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "guild_id TEXT NOT NULL," +
                "user_id TEXT NOT NULL," +
                "channel_id TEXT NOT NULL," +
                "status TEXT NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'CLAIMED', 'CLOSED'))," +
                "created_at TEXT," +
                "claimed_by TEXT," +
                "claimed_at TEXT," +
                "closed_by TEXT," +
                "closed_at TEXT," +
                "close_reason TEXT" +
                ");";
    }

    private static String createLevelRolesTableSql() {
        return "CREATE TABLE IF NOT EXISTS level_roles (" +
                "guild_id TEXT NOT NULL," +
                "level INTEGER NOT NULL," +
                "role_id TEXT NOT NULL," +
                "PRIMARY KEY (guild_id, level)" +
                ");";
    }

    private static String createRolePanelsTableSql(boolean isPostgres) {
        if (isPostgres) {
            return "CREATE TABLE IF NOT EXISTS role_panels (" +
                    "id BIGSERIAL PRIMARY KEY," +
                    "guild_id TEXT NOT NULL," +
                    "channel_id TEXT NOT NULL," +
                    "message_id TEXT NOT NULL," +
                    "created_at TEXT" +
                    ");";
        }
        return "CREATE TABLE IF NOT EXISTS role_panels (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "guild_id TEXT NOT NULL," +
                "channel_id TEXT NOT NULL," +
                "message_id TEXT NOT NULL," +
                "created_at TEXT" +
                ");";
    }

    private static String createRolePanelButtonsTableSql(boolean isPostgres) {
        if (isPostgres) {
            return "CREATE TABLE IF NOT EXISTS role_panel_buttons (" +
                    "id BIGSERIAL PRIMARY KEY," +
                    "panel_id BIGINT NOT NULL REFERENCES role_panels(id) ON DELETE CASCADE," +
                    "role_id TEXT NOT NULL," +
                    "label TEXT NOT NULL," +
                    "emoji TEXT" +
                    ");";
        }
        return "CREATE TABLE IF NOT EXISTS role_panel_buttons (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "panel_id INTEGER NOT NULL REFERENCES role_panels(id) ON DELETE CASCADE," +
                "role_id TEXT NOT NULL," +
                "label TEXT NOT NULL," +
                "emoji TEXT" +
                ");";
    }

    @FunctionalInterface
    private interface Migration {
        void apply() throws SQLException;
    }
}
