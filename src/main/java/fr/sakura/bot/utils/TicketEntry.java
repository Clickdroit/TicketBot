package fr.sakura.bot.utils;

public record TicketEntry(
        long id,
        String guildId,
        String userId,
        String channelId,
        String status,
        String createdAt,
        String claimedBy,
        String claimedAt,
        String closedBy,
        String closedAt,
        String closeReason
) {
}
