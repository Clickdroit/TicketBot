package fr.sakura.bot.database;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.URI;
import java.sql.Connection;
import java.sql.DriverManager;
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

        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            
            // Table des avertissements (warnings)
            stmt.execute(createWarningsTableSql());

            // Table des parametres par serveur (settings)
            stmt.execute(createSettingsTableSql());

            // Migration idempotente pour les bases deja existantes
            addColumnIfMissing(stmt, "settings", "anti_link_enabled", "INTEGER DEFAULT 1");
            addColumnIfMissing(stmt, "settings", "allow_gif_links", "INTEGER DEFAULT 1");
            addColumnIfMissing(stmt, "settings", "spam_limit", "INTEGER DEFAULT 5");
            addColumnIfMissing(stmt, "settings", "spam_window_ms", "INTEGER DEFAULT 5000");
            addColumnIfMissing(stmt, "settings", "automod_strikes_to_timeout", "INTEGER DEFAULT 3");
            addColumnIfMissing(stmt, "settings", "automod_timeout_minutes", "INTEGER DEFAULT 10");
            addColumnIfMissing(stmt, "settings", "ignored_channels", "TEXT");

            // Table pour le systeme d'XP et niveaux (levels)
            stmt.execute(createLevelsTableSql());

            // Table pour les tickets (tickets)
            stmt.execute(createTicketsTableSql());

            addColumnIfMissing(stmt, "tickets", "created_at", "TEXT");
            addColumnIfMissing(stmt, "tickets", "claimed_by", "TEXT");
            addColumnIfMissing(stmt, "tickets", "closed_by", "TEXT");
            addColumnIfMissing(stmt, "tickets", "closed_at", "TEXT");
            addColumnIfMissing(stmt, "tickets", "close_reason", "TEXT");

            // Table pour la protection (protect_settings)
            stmt.execute(createProtectSettingsTableSql());

            logger.info("Base de donnees SQLite initialisee avec succes.");

        } catch (SQLException e) {
            logger.error("Erreur lors de l'initialisation de la base SQLite", e);
        }
    }

    public static Connection getConnection() throws SQLException {
        if (dbDialect == DbDialect.POSTGRES && !CONNECTION_PROPERTIES.isEmpty()) {
            return DriverManager.getConnection(dbUrl, CONNECTION_PROPERTIES);
        }

        return DriverManager.getConnection(dbUrl);
    }

    private static void addColumnIfMissing(Statement stmt, String tableName, String columnName, String columnDefinition) throws SQLException {
        if (dbDialect == DbDialect.POSTGRES) {
            stmt.execute("ALTER TABLE " + tableName + " ADD COLUMN IF NOT EXISTS " + columnName + " " + columnDefinition);
            return;
        }

        if (hasColumn(stmt, tableName, columnName)) {
            return;
        }

        stmt.execute("ALTER TABLE " + tableName + " ADD COLUMN " + columnName + " " + columnDefinition);
        logger.info("Migration SQLite: colonne ajoutee {}.{}", tableName, columnName);
    }

    private static boolean hasColumn(Statement stmt, String tableName, String columnName) throws SQLException {
        try (ResultSet rs = stmt.executeQuery("PRAGMA table_info(" + tableName + ")")) {
            while (rs.next()) {
                if (columnName.equalsIgnoreCase(rs.getString("name"))) {
                    return true;
                }
            }
            return false;
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
                    "status TEXT DEFAULT 'OPEN'," +
                    "created_at TEXT," +
                    "claimed_by TEXT," +
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
                "status TEXT DEFAULT 'OPEN'," +
                "created_at TEXT," +
                "claimed_by TEXT," +
                "closed_by TEXT," +
                "closed_at TEXT," +
                "close_reason TEXT" +
                ");";
    }

    private static String createProtectSettingsTableSql() {
        return "CREATE TABLE IF NOT EXISTS protect_settings (" +
                "guild_id TEXT PRIMARY KEY," +
                "whitelist TEXT," +
                "anti_bot_enabled INTEGER DEFAULT 0," +
                "anti_raid_enabled INTEGER DEFAULT 0," +
                "anti_phishing_enabled INTEGER DEFAULT 0," +
                "min_account_age_hours INTEGER DEFAULT 24" +
                ");";
    }
}
