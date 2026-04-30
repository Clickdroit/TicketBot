package fr.sakura.bot.core.store;

import fr.sakura.bot.core.model.TempBanEntry;
import fr.sakura.bot.core.util.DbHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Gestion du stockage des bannissements temporaires.
 */
public class TempBanStore {

    private static final Logger logger = LoggerFactory.getLogger(TempBanStore.class);

    public void addTempBan(TempBanEntry entry) {
        String sql = "INSERT INTO temp_bans (guild_id, user_id, unban_time, reason) VALUES (?, ?, ?, ?)";
        DbHelper.update(sql, pstmt -> {
            pstmt.setString(1, entry.guildId());
            pstmt.setString(2, entry.userId());
            pstmt.setLong(3, entry.unbanTime());
            pstmt.setString(4, entry.reason());
        });
    }

    public void removeTempBan(String guildId, String userId) {
        String sql = "DELETE FROM temp_bans WHERE guild_id = ? AND user_id = ?";
        DbHelper.update(sql, pstmt -> {
            pstmt.setString(1, guildId);
            pstmt.setString(2, userId);
        });
    }

    public List<TempBanEntry> getExpiredBans(long now) {
        String sql = "SELECT guild_id, user_id, unban_time, reason FROM temp_bans WHERE unban_time <= ?";
        return DbHelper.queryList(sql,
                pstmt -> pstmt.setLong(1, now),
                rs -> new TempBanEntry(
                        rs.getString("guild_id"),
                        rs.getString("user_id"),
                        rs.getLong("unban_time"),
                        rs.getString("reason")
                )
        );
    }
}
