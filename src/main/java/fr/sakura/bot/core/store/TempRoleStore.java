package fr.sakura.bot.core.store;

import fr.sakura.bot.core.model.TempRoleEntry;
import fr.sakura.bot.core.util.DbHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class TempRoleStore {

    private static final Logger logger = LoggerFactory.getLogger(TempRoleStore.class);

    public void addTempRole(TempRoleEntry entry) {
        String sql = "INSERT INTO temp_roles (guild_id, user_id, role_id, expiry_time) VALUES (?, ?, ?, ?)";
        DbHelper.update(sql, pstmt -> {
            pstmt.setString(1, entry.guildId());
            pstmt.setString(2, entry.userId());
            pstmt.setString(3, entry.roleId());
            pstmt.setLong(4, entry.expiryTime());
        });
    }

    public List<TempRoleEntry> getExpiredRoles(long now) {
        String sql = "SELECT * FROM temp_roles WHERE expiry_time <= ?";
        return DbHelper.queryList(sql, pstmt -> pstmt.setLong(1, now), rs -> new TempRoleEntry(
                rs.getLong("id"),
                rs.getString("guild_id"),
                rs.getString("user_id"),
                rs.getString("role_id"),
                rs.getLong("expiry_time")
        ));
    }

    public void removeTempRole(long id) {
        String sql = "DELETE FROM temp_roles WHERE id = ?";
        DbHelper.update(sql, pstmt -> pstmt.setLong(1, id));
    }
    
    public void removeTempRole(String guildId, String userId, String roleId) {
        String sql = "DELETE FROM temp_roles WHERE guild_id = ? AND user_id = ? AND role_id = ?";
        DbHelper.update(sql, pstmt -> {
            pstmt.setString(1, guildId);
            pstmt.setString(2, userId);
            pstmt.setString(3, roleId);
        });
    }
}
