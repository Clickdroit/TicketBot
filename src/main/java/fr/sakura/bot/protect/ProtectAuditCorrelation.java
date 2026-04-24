package fr.sakura.bot.protect;

import java.time.Instant;
import java.util.List;

public final class ProtectAuditCorrelation {

    private ProtectAuditCorrelation() {
    }

    private static final long AUDIT_CORRELATION_WINDOW_SECONDS = 15;

    public record AuditEntrySnapshot(String actorId, String targetId, Instant createdAt) {
    }

    public static String findActorId(List<AuditEntrySnapshot> entries, String expectedTargetId, Instant eventTime, String selfUserId) {
        Instant earliest = eventTime.minusSeconds(AUDIT_CORRELATION_WINDOW_SECONDS);

        for (AuditEntrySnapshot entry : entries) {
            if (entry == null || entry.actorId == null || entry.targetId == null || entry.createdAt == null) continue;
            if (entry.actorId.equals(selfUserId)) continue;
            if (!entry.targetId.equals(expectedTargetId)) continue;
            if (entry.createdAt.isBefore(earliest)) continue;
            return entry.actorId;
        }

        return null;
    }
}
