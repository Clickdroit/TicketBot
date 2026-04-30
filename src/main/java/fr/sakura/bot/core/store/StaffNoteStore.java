package fr.sakura.bot.core.store;

import fr.sakura.bot.core.model.StaffNoteEntry;
import fr.sakura.bot.core.util.DbHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;

/**
 * Gestion du stockage des notes de staff.
 */
public class StaffNoteStore {

    private static final Logger logger = LoggerFactory.getLogger(StaffNoteStore.class);

    public void addNote(String guildId, String userId, String authorId, String content) {
        String sql = "INSERT INTO staff_notes (guild_id, user_id, author_id, content, created_at) VALUES (?, ?, ?, ?, ?)";
        DbHelper.update(sql, pstmt -> {
            pstmt.setString(1, guildId);
            pstmt.setString(2, userId);
            pstmt.setString(3, authorId);
            pstmt.setString(4, content);
            pstmt.setString(5, Instant.now().toString());
        });
    }

    public List<StaffNoteEntry> getNotes(String guildId, String userId) {
        String sql = "SELECT id, guild_id, user_id, author_id, content, created_at FROM staff_notes WHERE guild_id = ? AND user_id = ? ORDER BY id DESC";
        return DbHelper.queryList(sql,
                pstmt -> {
                    pstmt.setString(1, guildId);
                    pstmt.setString(2, userId);
                },
                rs -> new StaffNoteEntry(
                        rs.getLong("id"),
                        rs.getString("guild_id"),
                        rs.getString("user_id"),
                        rs.getString("author_id"),
                        rs.getString("content"),
                        rs.getString("created_at")
                )
        );
    }

    public boolean deleteNote(String guildId, long noteId) {
        String sql = "DELETE FROM staff_notes WHERE guild_id = ? AND id = ?";
        return DbHelper.update(sql, pstmt -> {
            pstmt.setString(1, guildId);
            pstmt.setLong(2, noteId);
        }) > 0;
    }
}
