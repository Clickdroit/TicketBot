package fr.sakura.bot.database;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.URI;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

public class DatabaseManager {

    private enum DbDialect {
        SQLITE,
        POSTGRES
    }

    private static final Logger logger = LoggerFactory.getLogger(DatabaseManager.class);
    private static final String DB_FOLDER = "data";
    private static final String DB_FILE = "sakura.db";
    private static final String DEFAULT_DB_URL = "jdbc:sqlite:" + DB_FOLDER + "/" + DB_FILE;
    private static final Properties CONNECTION_PROPERTIES = new Properties();
    private static String dbUrl = DEFAULT_DB_URL;
    private static DbDialect dbDialect = DbDialect.SQLITE;

    public static String getDbUrl() {
        return dbUrl;
    }

    public static boolean isPostgres() {
        return dbDialect == DbDialect.POSTGRES;
    }

    public static boolean isSqlite() {
        return dbDialect == DbDialect.SQLITE;
    }

    public static void configure(String configuredDbUrl) {
        CONNECTION_PROPERTIES.clear();

        if (configuredDbUrl == null || configuredDbUrl.isBlank()) {
            dbDialect = DbDialect.SQLITE;
            dbUrl = DEFAULT_DB_URL;
            logger.info("DATABASE_URL absente: fallback sur {}", dbUrl);
            return;
        }

        String trimmed = configuredDbUrl.trim();
        if (!trimmed.startsWith("jdbc:sqlite:")) {
            if (trimmed.startsWith("jdbc:postgresql:") || trimmed.startsWith("postgresql:") || trimmed.startsWith("postgres:")) {
                configurePostgres(trimmed);
                return;
            }

            logger.warn("DATABASE_URL invalide (schema non supporte). Fallback sur {}", DEFAULT_DB_URL);
            dbDialect = DbDialect.SQLITE;
            dbUrl = DEFAULT_DB_URL;
            return;
        }

        dbDialect = DbDialect.SQLITE;
        dbUrl = trimmed;
        logger.info("DATABASE_URL configuree en mode SQLite");
    }

    public static void initialize() {
        initialize(null);
    }

    public static void initialize(String configuredDbUrl) {
        configure(configuredDbUrl);
        if (dbDialect == DbDialect.SQLITE) {
            File folder = new File(DB_FOLDER);
            if (!folder.exists()) {
                boolean created = folder.mkdirs();
                if (created) logger.info("Dossier {} cree pour la base de donnees", DB_FOLDER);
            }
        }

        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);

            ensureSchemaMigrationsTable(conn);
            applyMigrations(conn);

            conn.commit();
            logger.info("Base de donnees initialisee avec succes (dialect={})", dbDialect);
        } catch (SQLException e) {
            logger.error("Erreur lors de l'initialisation de la base", e);
        }
    }

    public static Connection getConnection() throws SQLException {
        if (dbDialect == DbDialect.POSTGRES && !CONNECTION_PROPERTIES.isEmpty()) {
            return DriverManager.getConnection(dbUrl, CONNECTION_PROPERTIES);
        }

        return DriverManager.getConnection(dbUrl);
    }

    private static void applyMigrations(Connection conn) throws SQLException {
        applyMigration(conn, 1, "create core tables", () -> {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute(createWarningsTableSql());
                stmt.execute(createSettingsTableSql());
                stmt.execute(createLevelsTableSql());
                stmt.execute(createTicketsTableSql());
                stmt.execute(createLevelRolesTableSql());
            }
        });

        applyMigration(conn, 2, "add settings and tickets columns", () -> {
            addColumnIfMissing(conn, "settings", "anti_link_enabled", "INTEGER DEFAULT 1");
            addColumnIfMissing(conn, "settings", "allow_gif_links", "INTEGER DEFAULT 1");
            addColumnIfMissing(conn, "settings", "spam_limit", "INTEGER DEFAULT 5");
            addColumnIfMissing(conn, "settings", "spam_window_ms", "INTEGER DEFAULT 5000");
            addColumnIfMissing(conn, "settings", "automod_strikes_to_timeout", "INTEGER DEFAULT 3");
            addColumnIfMissing(conn, "settings", "automod_timeout_minutes", "INTEGER DEFAULT 10");
            addColumnIfMissing(conn, "settings", "automod_strike_reset_minutes", "INTEGER DEFAULT 10");
            addColumnIfMissing(conn, "settings", "automod_notice_cooldown_seconds", "INTEGER DEFAULT 15");
            addColumnIfMissing(conn, "settings", "xp_cooldown_ms", "INTEGER DEFAULT 60000");
            addColumnIfMissing(conn, "settings", "xp_min_message_length", "INTEGER DEFAULT 5");
            addColumnIfMissing(conn, "settings", "xp_min_alnum_count", "INTEGER DEFAULT 3");
            addColumnIfMissing(conn, "settings", "xp_min_gain", "INTEGER DEFAULT 15");
            addColumnIfMissing(conn, "settings", "xp_max_gain", "INTEGER DEFAULT 25");

            addColumnIfMissing(conn, "tickets", "created_at", "TEXT");
            addColumnIfMissing(conn, "tickets", "claimed_by", "TEXT");
            addColumnIfMissing(conn, "tickets", "claimed_at", "TEXT");
            addColumnIfMissing(conn, "tickets", "closed_by", "TEXT");
            addColumnIfMissing(conn, "tickets", "closed_at", "TEXT");
            addColumnIfMissing(conn, "tickets", "close_reason", "TEXT");
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

    private static void addColumnIfMissing(Connection conn, String tableName, String columnName, String columnDefinition) throws SQLException {
        if (hasColumn(conn, tableName, columnName)) {
            return;
        }

        String sql = "ALTER TABLE " + tableName + " ADD COLUMN " + columnName + " " + columnDefinition;
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        }
        logger.info("Migration: colonne ajoutee {}.{}", tableName, columnName);
    }

    private static boolean hasColumn(Connection conn, String tableName, String columnName) throws SQLException {
        DatabaseMetaData metaData = conn.getMetaData();

        try (ResultSet rs = metaData.getColumns(null, null, tableName, columnName)) {
            if (rs.next()) {
                return true;
            }
        }

        try (ResultSet rs = metaData.getColumns(null, null, tableName.toLowerCase(), columnName.toLowerCase())) {
            return rs.next();
        }
    }

    private static void configurePostgres(String rawUrl) {
        String candidate = rawUrl.startsWith("jdbc:") ? rawUrl.substring(5) : rawUrl;
        URI uri = URI.create(candidate);

        if (uri.getHost() == null || uri.getPath() == null || uri.getPath().isBlank()) {
            logger.warn("DATABASE_URL PostgreSQL invalide (hote/chemin manquant). Fallback sur {}", DEFAULT_DB_URL);
            dbDialect = DbDialect.SQLITE;
            dbUrl = DEFAULT_DB_URL;
            return;
        }

        dbDialect = DbDialect.POSTGRES;
        dbUrl = "jdbc:postgresql://" + uri.getHost() +
                (uri.getPort() > 0 ? ":" + uri.getPort() : "") +
                uri.getPath() +
                (uri.getQuery() != null && !uri.getQuery().isBlank() ? "?" + uri.getQuery() : "");

        String userInfo = uri.getUserInfo();
        if (userInfo != null && !userInfo.isBlank()) {
            String[] parts = userInfo.split(":", 2);
            if (!parts[0].isBlank()) {
                CONNECTION_PROPERTIES.setProperty("user", parts[0]);
            }
            if (parts.length == 2 && !parts[1].isBlank()) {
                CONNECTION_PROPERTIES.setProperty("password", parts[1]);
            }
        }

        logger.info("DATABASE_URL configuree en mode PostgreSQL ({}:{}/{})", uri.getHost(), uri.getPort(), uri.getPath());
    }

    private static String createWarningsTableSql() {
        if (dbDialect == DbDialect.POSTGRES) {
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
                "xp_cooldown_ms INTEGER DEFAULT 60000," +
                "xp_min_message_length INTEGER DEFAULT 5," +
                "xp_min_alnum_count INTEGER DEFAULT 3," +
                "xp_min_gain INTEGER DEFAULT 15," +
                "xp_max_gain INTEGER DEFAULT 25," +
                "log_channel_id TEXT," +
                "welcome_channel_id TEXT" +
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

    private static String createTicketsTableSql() {
        if (dbDialect == DbDialect.POSTGRES) {
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

    @FunctionalInterface
    private interface Migration {
        void apply() throws SQLException;
    }
}
