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
 * Gère l'initialisation du schéma de BDD pour TicketBot.
 * Intègre des migrations robustes pour les bases SQLite et PostgreSQL (Supabase) existantes.
 */
public class SchemaInitializer {
    private static final Logger logger = LoggerFactory.getLogger(SchemaInitializer.class);

    public static void initializeSchema(Connection conn, boolean isPostgres) throws SQLException {
        ensureSchemaMigrationsTable(conn);
        applyMigrations(conn, isPostgres);
    }

    private static void applyMigrations(Connection conn, boolean isPostgres) throws SQLException {
        applyMigration(conn, 1, "create ticketbot core tables", () -> {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute(createSettingsTableSql());
                stmt.execute(createTicketsTableSql(isPostgres));
            }
        });

        applyMigration(conn, 2, "create ticketbot indexes", () -> {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_tickets_guild_status_created ON tickets(guild_id, status, created_at)");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_tickets_channel ON tickets(guild_id, channel_id)");
                stmt.execute("CREATE UNIQUE INDEX IF NOT EXISTS uq_tickets_single_active_per_user ON tickets(guild_id, user_id) WHERE status IN ('OPEN', 'CLAIMED')");
            }
        });

        applyMigration(conn, 3, "add support_role_id column to settings", () -> {
            addColumnIfMissing(conn, "settings", "support_role_id", "TEXT", isPostgres);
        });

        applyMigration(conn, 4, "create ticket_support_roles table", () -> {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute(createTicketSupportRolesTableSql());
            }
        });
    }

    private static void applyMigration(Connection conn, int version, String description, Migration migration) throws SQLException {
        if (isMigrationApplied(conn, version)) {
            return;
        }

        migration.apply();
        recordMigration(conn, version, description);
        logger.info("Migration appliquée v{}: {}", version, description);
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
        logger.info("Migration: colonne ajoutée {}.{}", tableName, columnName);
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

    private static String createSettingsTableSql() {
        return "CREATE TABLE IF NOT EXISTS settings (" +
                "guild_id TEXT PRIMARY KEY," +
                "log_channel_id TEXT," +
                "transcript_channel_id TEXT," +
                "support_role_id TEXT" +
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

    private static String createTicketSupportRolesTableSql() {
        return "CREATE TABLE IF NOT EXISTS ticket_support_roles (" +
                "guild_id TEXT NOT NULL," +
                "category TEXT NOT NULL," +
                "role_id TEXT NOT NULL," +
                "PRIMARY KEY (guild_id, category, role_id)" +
                ");";
    }

    @FunctionalInterface
    private interface Migration {
        void apply() throws SQLException;
    }
}
