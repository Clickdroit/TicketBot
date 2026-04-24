package fr.sakura.bot.core.store;

import fr.sakura.bot.core.model.TicketEntry;
import fr.sakura.bot.core.util.DbHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Stockage des données de tickets de support.
 * Retrait des synchronized : HikariCP gère déjà la thread-safety des connexions.
 */
public class TicketStore {

    private static final Logger logger = LoggerFactory.getLogger(TicketStore.class);

    public TicketStore() {
    }

    public TicketEntry getActiveTicket(String guildId, String userId) {
        String sql = "SELECT * FROM tickets WHERE guild_id = ? AND user_id = ? AND status IN ('OPEN', 'CLAIMED') ORDER BY id DESC LIMIT 1";
        return DbHelper.queryOne(sql,
                pstmt -> {
                    pstmt.setString(1, guildId);
                    pstmt.setString(2, userId);
                },
                this::readTicket
        ).orElse(null);
    }

    public TicketEntry getOpenTicket(String guildId, String userId) {
        String sql = "SELECT * FROM tickets WHERE guild_id = ? AND user_id = ? AND status = 'OPEN' ORDER BY id DESC LIMIT 1";
        return DbHelper.queryOne(sql,
                pstmt -> {
                    pstmt.setString(1, guildId);
                    pstmt.setString(2, userId);
                },
                this::readTicket
        ).orElse(null);
    }

    public TicketEntry getByChannelId(String guildId, String channelId) {
        String sql = "SELECT * FROM tickets WHERE guild_id = ? AND channel_id = ? ORDER BY id DESC LIMIT 1";
        return DbHelper.queryOne(sql,
                pstmt -> {
                    pstmt.setString(1, guildId);
                    pstmt.setString(2, channelId);
                },
                this::readTicket
        ).orElse(null);
    }

    public TicketEntry createTicket(String guildId, String userId, String channelId) {
        TicketEntry existing = getActiveTicket(guildId, userId);
        if (existing != null) {
            return existing;
        }

        String createdAt = OffsetDateTime.now().toString();
        String sql = "INSERT INTO tickets (guild_id, user_id, channel_id, status, created_at) VALUES (?, ?, ?, 'OPEN', ?)";
        try {
            DbHelper.update(sql, pstmt -> {
                pstmt.setString(1, guildId);
                pstmt.setString(2, userId);
                pstmt.setString(3, channelId);
                pstmt.setString(4, createdAt);
            });
        } catch (Exception e) {
            logger.error("Erreur creation ticket guildId={}, userId={}, channelId={}", guildId, userId, channelId, e);
        }
        return getActiveTicket(guildId, userId);
    }

    public TicketEntry claimTicket(String guildId, String channelId, String claimedBy) {
        String sql = "UPDATE tickets SET status = 'CLAIMED', claimed_by = ?, claimed_at = ? " +
                "WHERE guild_id = ? AND channel_id = ? AND status = 'OPEN'";
        DbHelper.update(sql, pstmt -> {
            pstmt.setString(1, claimedBy);
            pstmt.setString(2, OffsetDateTime.now().toString());
            pstmt.setString(3, guildId);
            pstmt.setString(4, channelId);
        });

        return getByChannelId(guildId, channelId);
    }

    public TicketEntry closeTicket(String guildId, String channelId, String closedBy, String closeReason) {
        String sql = "UPDATE tickets SET status = 'CLOSED', closed_by = ?, closed_at = ?, close_reason = ? " +
                "WHERE guild_id = ? AND channel_id = ? AND status IN ('OPEN', 'CLAIMED')";
        DbHelper.update(sql, pstmt -> {
            pstmt.setString(1, closedBy);
            pstmt.setString(2, OffsetDateTime.now().toString());
            pstmt.setString(3, closeReason);
            pstmt.setString(4, guildId);
            pstmt.setString(5, channelId);
        });

        return getByChannelId(guildId, channelId);
    }

    public List<TicketEntry> getOpenTickets(String guildId) {
        String sql = "SELECT * FROM tickets WHERE guild_id = ? AND status IN ('OPEN', 'CLAIMED') ORDER BY id DESC";
        return DbHelper.queryList(sql,
                pstmt -> pstmt.setString(1, guildId),
                this::readTicket
        );
    }

    private TicketEntry readTicket(ResultSet rs) throws SQLException {
        return new TicketEntry(
                rs.getLong("id"),
                rs.getString("guild_id"),
                rs.getString("user_id"),
                rs.getString("channel_id"),
                rs.getString("status"),
                rs.getString("created_at"),
                rs.getString("claimed_by"),
                rs.getString("claimed_at"),
                rs.getString("closed_by"),
                rs.getString("closed_at"),
                rs.getString("close_reason")
        );
    }
}
