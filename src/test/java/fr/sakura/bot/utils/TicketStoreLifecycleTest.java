package fr.sakura.bot.utils;

import fr.sakura.bot.database.DatabaseManager;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class TicketStoreLifecycleTest {

    @Test
    void shouldEnforceActiveTicketUniquenessAndLifecycleTransitions() throws Exception {
        Path dbPath = Files.createTempFile("sakura-ticket-store", ".db");
        try {
            DatabaseManager.initialize("jdbc:sqlite:" + dbPath);
            TicketStore store = new TicketStore();

            TicketEntry first = store.createTicket("guild-1", "user-1", "channel-1");
            assertNotNull(first);
            assertEquals("OPEN", first.status());

            TicketEntry duplicate = store.createTicket("guild-1", "user-1", "channel-2");
            assertNotNull(duplicate);
            assertEquals("channel-1", duplicate.channelId(), "A second active ticket should not be created");

            TicketEntry claimed = store.claimTicket("guild-1", "channel-1", "staff-1");
            assertNotNull(claimed);
            assertEquals("CLAIMED", claimed.status());
            assertEquals("staff-1", claimed.claimedBy());
            assertNotNull(claimed.claimedAt());

            TicketEntry closed = store.closeTicket("guild-1", "channel-1", "staff-1", "Done");
            assertNotNull(closed);
            assertEquals("CLOSED", closed.status());
            assertEquals("staff-1", closed.closedBy());
            assertEquals("Done", closed.closeReason());

            TicketEntry afterClose = store.getActiveTicket("guild-1", "user-1");
            assertNull(afterClose);
        } finally {
            DatabaseManager.shutdown();
            Files.deleteIfExists(dbPath);
        }
    }
}
