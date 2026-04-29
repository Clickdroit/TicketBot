package fr.sakura.bot.core.service;

import fr.sakura.bot.core.store.RolesPanelStore;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RolesPanelServiceTest {

    private RolesPanelStore store;
    private RolesPanelService service;
    private Guild guild;

    @BeforeEach
    void setUp() {
        store = mock(RolesPanelStore.class);
        service = new RolesPanelService(store);
        guild = mock(Guild.class);
        when(guild.getId()).thenReturn("guild-1");
    }

    @Test
    void addButton_LimitReached() {
        // Simuler un panel existant
        RolesPanelStore.RolePanel panel = new RolesPanelStore.RolePanel(1L, "guild-1", "chan-1", "msg-1", "", false, true, "Title", "🌸", null);
        when(store.findPanel(anyString(), anyLong())).thenReturn(panel);
        
        // Simuler que la limite est atteinte (25 boutons maintenant)
        when(store.countButtons(1L)).thenReturn(25);
        when(store.findButton(anyString(), anyLong(), anyString())).thenReturn(null);

        boolean result = service.addButton("guild-1", 1L, "role-1", "Label", null);
        
        assertFalse(result, "L'ajout devrait être refusé si la limite est atteinte");
        verify(store, never()).upsertButton(anyLong(), anyString(), anyString(), anyString());
    }

    @Test
    void addButton_Success() {
        RolesPanelStore.RolePanel panel = new RolesPanelStore.RolePanel(1L, "guild-1", "chan-1", "msg-1", "", false, true, "Title", "🌸", null);
        when(store.findPanel("guild-1", 1L)).thenReturn(panel);
        when(store.countButtons(1L)).thenReturn(10);

        boolean result = service.addButton("guild-1", 1L, "role-1", "Label", "emoji");

        assertTrue(result);
        verify(store).upsertButton(1L, "role-1", "Label", "emoji");
    }

    @Test
    void deletePanel_Success() {
        RolesPanelStore.RolePanel panel = new RolesPanelStore.RolePanel(1L, "guild-1", "chan-1", "msg-1", "", false, true, "Title", "🌸", null);
        when(store.findPanel("guild-1", 1L)).thenReturn(panel);
        when(store.deletePanel("guild-1", 1L)).thenReturn(true);
        
        TextChannel channel = mock(TextChannel.class);
        when(guild.getTextChannelById("chan-1")).thenReturn(channel);
        when(channel.deleteMessageById("msg-1")).thenReturn(mock(net.dv8tion.jda.api.requests.restaction.AuditableRestAction.class));

        boolean result = service.deletePanel(guild, 1L);

        assertTrue(result);
        verify(store).deletePanel("guild-1", 1L);
        verify(channel).deleteMessageById("msg-1");
    }
}
