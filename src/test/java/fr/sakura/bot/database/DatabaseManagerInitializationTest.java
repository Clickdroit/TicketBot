package fr.sakura.bot.database;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseManagerInitializationTest {

    @Test
    void shouldCreateSchemaMigrationsAndCoreIndexes() throws Exception {
        Path dbPath = Files.createTempFile("sakura-db-init", ".db");
        try {
            DatabaseManager.initialize("jdbc:sqlite:" + dbPath);

            try (Connection conn = DatabaseManager.getConnection()) {
                assertTrue(tableExists(conn, "schema_migrations"));
                assertTrue(tableExists(conn, "level_roles"));
                assertTrue(tableExists(conn, "protect_settings"));
                assertTrue(indexExists(conn, "idx_warnings_guild_user_time"));
                assertTrue(indexExists(conn, "idx_tickets_guild_status_created"));
                assertTrue(indexExists(conn, "uq_tickets_single_active_per_user"));
            }
        } finally {
            DatabaseManager.shutdown();
            // Petit délai pour laisser Windows libérer le verrou sur le fichier
            Thread.sleep(200);
            Files.deleteIfExists(dbPath);
        }
    }

    private boolean tableExists(Connection conn, String name) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM sqlite_master WHERE type='table' AND name=?")) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private boolean indexExists(Connection conn, String name) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM sqlite_master WHERE type='index' AND name=?")) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }
}
