package fr.sakura.bot.protect;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ProtectAuditCorrelationTest {

    @Test
    void shouldSelectMatchingRecentTargetAndIgnoreSelfActor() {
        Instant now = Instant.now();
        List<ProtectAuditCorrelation.AuditEntrySnapshot> entries = List.of(
                new ProtectAuditCorrelation.AuditEntrySnapshot("bot-id", "target-1", now.minusSeconds(1)),
                new ProtectAuditCorrelation.AuditEntrySnapshot("user-42", "target-1", now.minusSeconds(2)),
                new ProtectAuditCorrelation.AuditEntrySnapshot("user-33", "other", now.minusSeconds(1))
        );

        String actor = ProtectAuditCorrelation.findActorId(entries, "target-1", now, "bot-id");

        assertEquals("user-42", actor);
    }

    @Test
    void shouldReturnNullWhenEntriesAreTooOldOrWrongTarget() {
        Instant now = Instant.now();
        List<ProtectAuditCorrelation.AuditEntrySnapshot> entries = List.of(
                new ProtectAuditCorrelation.AuditEntrySnapshot("user-42", "target-1", now.minusSeconds(20)),
                new ProtectAuditCorrelation.AuditEntrySnapshot("user-99", "other", now.minusSeconds(1))
        );

        String actor = ProtectAuditCorrelation.findActorId(entries, "target-1", now, "bot-id");

        assertNull(actor);
    }
}
