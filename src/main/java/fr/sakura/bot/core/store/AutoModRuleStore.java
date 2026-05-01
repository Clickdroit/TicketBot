package fr.sakura.bot.core.store;

import fr.sakura.bot.core.model.AutoModRuleEntry;
import fr.sakura.bot.core.util.DbHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class AutoModRuleStore {

    private static final Logger logger = LoggerFactory.getLogger(AutoModRuleStore.class);

    public void addRule(AutoModRuleEntry entry) {
        String sql = "INSERT INTO automod_rules (guild_id, type, pattern, action, created_at) VALUES (?, ?, ?, ?, ?)";
        DbHelper.update(sql, pstmt -> {
            pstmt.setString(1, entry.guildId());
            pstmt.setString(2, entry.type());
            pstmt.setString(3, entry.pattern());
            pstmt.setString(4, entry.action());
            pstmt.setString(5, entry.createdAt());
        });
    }

    public List<AutoModRuleEntry> getRules(String guildId) {
        String sql = "SELECT * FROM automod_rules WHERE guild_id = ?";
        return DbHelper.queryList(sql, pstmt -> pstmt.setString(1, guildId), rs -> new AutoModRuleEntry(
                rs.getLong("id"),
                rs.getString("guild_id"),
                rs.getString("type"),
                rs.getString("pattern"),
                rs.getString("action"),
                rs.getString("created_at")
        ));
    }

    public boolean removeRule(String guildId, long ruleId) {
        String sql = "DELETE FROM automod_rules WHERE guild_id = ? AND id = ?";
        return DbHelper.update(sql, pstmt -> {
            pstmt.setString(1, guildId);
            pstmt.setLong(2, ruleId);
        }) > 0;
    }
}
