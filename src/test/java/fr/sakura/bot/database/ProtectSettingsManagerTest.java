package fr.sakura.bot.database;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProtectSettingsManagerTest {

    @Test
    void shouldCreateProtectSettingsAndPersistProtectConfiguration() throws Exception {
        Path dbPath = Files.createTempFile("sakura-protect-settings", ".db");
        try {
            DatabaseManager.initialize("jdbc:sqlite:" + dbPath);

            ProtectSettingsManager manager = new ProtectSettingsManager();
            String guildId = "guild-1";

            manager.setAntiBotEnabled(guildId, true);
            manager.setAntiRaidEnabled(guildId, true);
            manager.setAntiPhishingEnabled(guildId, true);
            manager.setMinAccountAgeHours(guildId, 48);
            manager.setRaidJoinThreshold(guildId, 12);
            manager.setRaidWindowSeconds(guildId, 90);
            manager.setRaidModeDurationSeconds(guildId, 600);
            manager.setQuarantineRoleId(guildId, "role-q");
            manager.addTrustedRoleId(guildId, "role-staff");
            manager.addPhishingAllowDomain(guildId, "docs.discord.com");
            manager.addToWhitelist(guildId, "user-1");

            assertTrue(tableExists("protect_settings"));
            assertTrue(manager.isAntiBotEnabled(guildId));
            assertEquals(48, manager.getMinAccountAgeHours(guildId));
            assertEquals(12, manager.getRaidJoinThreshold(guildId));
            assertEquals(90, manager.getRaidWindowSeconds(guildId));
            assertEquals(600, manager.getRaidModeDurationSeconds(guildId));
            assertEquals("role-q", manager.getQuarantineRoleId(guildId));
            assertEquals(1, manager.getTrustedRoleIds(guildId).size());
            assertEquals(1, manager.getPhishingAllowlist(guildId).size());
            assertEquals(1, manager.getWhitelist(guildId).size());
        } finally {
            DatabaseManager.shutdown();
            Thread.sleep(200);
            Files.deleteIfExists(dbPath);
        }
    }

    private boolean tableExists(String name) throws Exception {
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM sqlite_master WHERE type='table' AND name=?")) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }
}
