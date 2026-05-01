package fr.sakura.bot.core.service;

import fr.sakura.bot.core.model.StaffNoteEntry;
import fr.sakura.bot.core.store.StaffNoteStore;

import java.util.List;

/**
 * Service gérant les notes internes de staff.
 */
public class StaffNoteService {

    private final StaffNoteStore store;

    public StaffNoteService(StaffNoteStore store) {
        this.store = store;
    }

    public void addNote(String guildId, String userId, String authorId, String content) {
        store.addNote(guildId, userId, authorId, content);
    }

    public List<StaffNoteEntry> getNotes(String guildId, String userId) {
        return store.getNotes(guildId, userId);
    }

    public boolean deleteNote(String guildId, long noteId) {
        return store.deleteNote(guildId, noteId);
    }

    public StaffNoteStore getStaffNoteStore() {
        return store;
    }
}
