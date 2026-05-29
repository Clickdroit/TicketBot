package fr.sakura.bot.core.store;

import fr.sakura.bot.core.model.TicketEntry;
import fr.sakura.bot.database.DatabaseManager;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class TicketStoreLifecycleTest {

    @Test
    void shouldEnforceActiveTicketUniquenessAndLifecycleTransitions() throws Exception {
        Path dbPath = Path.of("data/sakura.db");
        Files.deleteIfExists(dbPath);
        DatabaseManager.initialize();

        try {
            TicketStore store = new TicketStore();

            // 1. Create first ticket
            TicketEntry first = store.createTicket("guild-1", "user-1", "channel-1");
            assertNotNull(first);
            assertEquals("OPEN", first.status());

            // 2. Try to create second ticket (should return first)
            TicketEntry duplicate = store.createTicket("guild-1", "user-1", "channel-2");
            assertEquals(first.id(), duplicate.id());
            assertEquals("channel-1", duplicate.channelId());

            // 3. Claim ticket
            TicketEntry claimed = store.claimTicket("guild-1", "channel-1", "staff-1");
            assertNotNull(claimed);
            assertEquals("CLAIMED", claimed.status());
            assertEquals("staff-1", claimed.claimedBy());

            // 4. Close ticket
            TicketEntry closed = store.closeTicket("guild-1", "channel-1", "staff-1", "Done");
            assertNotNull(closed);
            assertEquals("CLOSED", closed.status());
            assertEquals("Done", closed.closeReason());

            TicketEntry afterClose = store.getActiveTicket("guild-1", "user-1");
            assertNull(afterClose);
        } finally {
            DatabaseManager.shutdown();
            Files.deleteIfExists(dbPath);
        }
    }

    @Test
    void insertUserRequestedCategories() throws Exception {
        DatabaseManager.initialize();
        try {
            fr.sakura.bot.database.SettingsManager settingsManager = new fr.sakura.bot.database.SettingsManager();
            String guildId = "1509905845696069702";
            
            // Delete old categories just in case
            settingsManager.removeCategory(guildId, "partnership");
            settingsManager.removeCategory(guildId, "report");
            settingsManager.removeCategory(guildId, "support");
            settingsManager.removeCategory(guildId, "suggestion");
            settingsManager.removeCategory(guildId, "other");
            
            // Insert user categories
            settingsManager.addCategory(guildId, "cmd", "Nouvelle commande", "Demander ou suggérer une nouvelle commande", "✨");
            settingsManager.addCategory(guildId, "question", "Question sur le bot", "Poser une question concernant l'utilisation du bot", "❓");
            settingsManager.addCategory(guildId, "bug", "Problème technique", "Signaler un bug ou un dysfonctionnement technique", "🛠️");
            settingsManager.addCategory(guildId, "feature", "Demande de fonctionnalité", "Proposer une amélioration ou une nouvelle idée", "💡");
            
            System.out.println("🎉 Categories inserted successfully for guild " + guildId + "!");
        } finally {
            DatabaseManager.shutdown();
        }
    }
}
