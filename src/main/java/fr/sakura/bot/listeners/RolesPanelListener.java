package fr.sakura.bot.listeners;

import fr.sakura.bot.core.service.RolesPanelService;
import fr.sakura.bot.core.util.MdcContext;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import org.slf4j.MDC;

/**
 * Listener pour les interactions avec les boutons des panels de rôles.
 */
public class RolesPanelListener extends ListenerAdapter {

    private final RolesPanelService rolesPanelService;

    public RolesPanelListener(RolesPanelService rolesPanelService) {
        this.rolesPanelService = rolesPanelService;
    }

    @Override
    public void onButtonInteraction(@NotNull ButtonInteractionEvent event) {
        String componentId = event.getComponentId();
        if (componentId == null || !componentId.startsWith("rolespanel:")) {
            return;
        }

        try (var ignored = MdcContext.of("guildId", event.getGuild().getId(), "userId", event.getUser().getId())) {
            String[] parts = componentId.split(":", 3);
            if (parts.length != 3) {
                event.reply("❌ Bouton invalide.").setEphemeral(true).queue();
                return;
            }

            long panelId = Long.parseLong(parts[1]);
            String roleId = parts[2];
            rolesPanelService.handleToggle(event, panelId, roleId);
        } catch (NumberFormatException e) {
            event.reply("❌ Identifiant de panel invalide.").setEphemeral(true).queue();
        }
    }
}
