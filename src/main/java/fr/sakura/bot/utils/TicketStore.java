package fr.sakura.bot.utils;

import fr.sakura.bot.database.DatabaseManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

public class TicketStore {

    private static final Logger logger = LoggerFactory.getLogger(TicketStore.class);

    /**
     * Constructeur sans argument requis par TicketService(SettingsManager).
     * Utilise DatabaseManager via ses méthodes statiques — aucune init locale nécessaire.
     */
    public TicketStore() {
    }

    public synchronized TicketEntry getActiveTicket(String guildId, String userId) {
        String sql = "SELECT * FROM tickets WHERE guild_id = ? AND user_id = ? AND status IN ('OPEN', 'CLAIMED') ORDER BY id DESC LIMIT 1";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, guildId);
            pstmt.setString(2, userId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) return readTicket(rs);
            }
        } catch (SQLException e) {
            logger.error("Erreur lecture ticket actif guildId={}, userId={}", guildId, userId, e);
        }
        return null;
    }

    public synchronized TicketEntry getOpenTicket(String guildId, String userId) {
        String sql = "SELECT * FROM tickets WHERE guild_id = ? AND user_id = ? AND status = 'OPEN' ORDER BY id DESC LIMIT 1";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, guildId);
            pstmt.setString(2, userId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) return readTicket(rs);
            }
        } catch (SQLException e) {
            logger.error("Erreur lecture ticket ouvert guildId={}, userId={}", guildId, userId, e);
        }
        return null;
    }

    public synchronized TicketEntry getByChannelId(String guildId, String channelId) {
        String sql = "SELECT * FROM tickets WHERE guild_id = ? AND channel_id = ? ORDER BY id DESC LIMIT 1";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, guildId);
            pstmt.setString(2, channelId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) return readTicket(rs);
            }
        } catch (SQLException e) {
            logger.error("Erreur lecture ticket channelId={}, guildId={}", channelId, guildId, e);
        }
        return null;
    }

    public synchronized TicketEntry createTicket(String guildId, String userId, String channelId) {
        TicketEntry existing = getActiveTicket(guildId, userId);
        if (existing != null) {
            return existing;
        }

        String createdAt = OffsetDateTime.now().toString();
        String sql = "INSERT INTO tickets (guild_id, user_id, channel_id, status, created_at) VALUES (?, ?, ?, 'OPEN', ?)";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, guildId);
            pstmt.setString(2, userId);
            pstmt.setString(3, channelId);
            pstmt.setString(4, createdAt);
            pstmt.executeUpdate();
            return getActiveTicket(guildId, userId);
        } catch (SQLException e) {
            logger.error("Erreur creation ticket guildId={}, userId={}, channelId={}", guildId, userId, channelId, e);
            return getActiveTicket(guildId, userId);
        }
    }

    public synchronized TicketEntry claimTicket(String guildId, String channelId, String claimedBy) {
        String sql = "UPDATE tickets SET status = 'CLAIMED', claimed_by = ?, claimed_at = ? " +
                "WHERE guild_id = ? AND channel_id = ? AND status = 'OPEN'";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, claimedBy);
            pstmt.setString(2, OffsetDateTime.now().toString());
            pstmt.setString(3, guildId);
            pstmt.setString(4, channelId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            logger.error("Erreur claim ticket guildId={}, channelId={}", guildId, channelId, e);
        }

        return getByChannelId(guildId, channelId);
    }

    public synchronized TicketEntry closeTicket(String guildId, String channelId, String closedBy, String closeReason) {
        String sql = "UPDATE tickets SET status = 'CLOSED', closed_by = ?, closed_at = ?, close_reason = ? " +
                "WHERE guild_id = ? AND channel_id = ? AND status IN ('OPEN', 'CLAIMED')";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, closedBy);
            pstmt.setString(2, OffsetDateTime.now().toString());
            pstmt.setString(3, closeReason);
            pstmt.setString(4, guildId);
            pstmt.setString(5, channelId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            logger.error("Erreur fermeture ticket guildId={}, channelId={}", guildId, channelId, e);
        }

        return getByChannelId(guildId, channelId);
    }

    public synchronized List<TicketEntry> getOpenTickets(String guildId) {
        List<TicketEntry> tickets = new ArrayList<>();
        String sql = "SELECT * FROM tickets WHERE guild_id = ? AND status IN ('OPEN', 'CLAIMED') ORDER BY id DESC";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, guildId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) tickets.add(readTicket(rs));
            }
        } catch (SQLException e) {
            logger.error("Erreur lecture tickets ouverts guildId={}", guildId, e);
        }
        return tickets;
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