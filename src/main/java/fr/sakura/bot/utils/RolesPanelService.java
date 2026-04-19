package fr.sakura.bot.utils;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class RolesPanelService {

    private static final Logger logger = LoggerFactory.getLogger(RolesPanelService.class);
    public static final int MAX_BUTTONS = 5;

    private final RolesPanelStore store;

    public RolesPanelService(RolesPanelStore store) {
        this.store = store;
    }

    public RolesPanelStore.RolePanel createPanel(String guildId, String channelId, String messageId) {
        return store.createPanel(guildId, channelId, messageId);
    }

    public RolesPanelStore.RolePanel getPanel(String guildId, long panelId) {
        return store.findPanel(guildId, panelId);
    }

    public List<RolesPanelStore.RolePanel> listPanels(String guildId) {
        return store.listPanels(guildId);
    }

    public boolean addButton(String guildId, long panelId, String roleId, String label, String emoji) {
        RolesPanelStore.RolePanel panel = store.findPanel(guildId, panelId);
        if (panel == null) {
            return false;
        }
        RolesPanelStore.RolePanelButton existing = store.findButton(guildId, panelId, roleId);
        if (existing == null && store.countButtons(panelId) >= MAX_BUTTONS) {
            return false;
        }
        store.upsertButton(panelId, roleId, label, emoji);
        return true;
    }

    public boolean removeButton(String guildId, long panelId, String roleId) {
        RolesPanelStore.RolePanel panel = store.findPanel(guildId, panelId);
        if (panel == null) {
            return false;
        }
        return store.removeButton(panelId, roleId);
    }

    public void refreshPanelMessage(Guild guild, RolesPanelStore.RolePanel panel) {
        if (guild == null || panel == null) {
            return;
        }
        TextChannel channel = guild.getTextChannelById(panel.channelId());
        if (channel == null) {
            logger.warn("RolesPanel: channel introuvable panelId={}, channelId={}", panel.id(), panel.channelId());
            return;
        }
        channel.retrieveMessageById(panel.messageId()).queue(
                message -> applyButtons(message, panel.buttons()),
                error -> logger.warn("RolesPanel: message introuvable panelId={}, messageId={}", panel.id(), panel.messageId(), error)
        );
    }

    public void rebuildPanels(Guild guild) {
        if (guild == null) {
            return;
        }
        List<RolesPanelStore.RolePanel> panels = listPanels(guild.getId());
        for (RolesPanelStore.RolePanel panel : panels) {
            refreshPanelMessage(guild, panel);
        }
        logger.info("RolesPanel: {} panel(s) reconstruits guildId={}", panels.size(), guild.getId());
    }

    public void handleToggle(ButtonInteractionEvent event, long panelId, String roleId) {
        if (!event.isFromGuild() || event.getGuild() == null || event.getMember() == null) {
            event.reply("❌ Interaction invalide.").setEphemeral(true).queue();
            return;
        }
        RolesPanelStore.RolePanelButton button = store.findButton(event.getGuild().getId(), panelId, roleId);
        if (button == null) {
            event.reply("❌ Bouton de rôle introuvable ou obsolète.").setEphemeral(true).queue();
            return;
        }

        Role role = event.getGuild().getRoleById(roleId);
        if (role == null) {
            event.reply("❌ Le rôle associé n'existe plus.").setEphemeral(true).queue();
            return;
        }

        Member self = event.getGuild().getSelfMember();
        Member member = event.getMember();
        if (!self.hasPermission(Permission.MANAGE_ROLES) || !self.canInteract(role) || !self.canInteract(member)) {
            event.reply("❌ Je ne peux pas gérer ce rôle (permission/hierarchie).").setEphemeral(true).queue();
            return;
        }

        if (member.getRoles().stream().anyMatch(r -> r.getId().equals(roleId))) {
            event.getGuild().removeRoleFromMember(member, role).queue(
                    ok -> event.reply("➖ Rôle retiré : " + role.getAsMention()).setEphemeral(true).queue(),
                    err -> event.reply("❌ Impossible de retirer ce rôle.").setEphemeral(true).queue()
            );
            return;
        }

        event.getGuild().addRoleToMember(member, role).queue(
                ok -> event.reply("➕ Rôle ajouté : " + role.getAsMention()).setEphemeral(true).queue(),
                err -> event.reply("❌ Impossible d'ajouter ce rôle.").setEphemeral(true).queue()
        );
    }

    public List<Button> buildButtons(List<RolesPanelStore.RolePanelButton> panelButtons) {
        List<Button> buttons = new ArrayList<>();
        if (panelButtons == null) {
            return buttons;
        }
        for (RolesPanelStore.RolePanelButton panelButton : panelButtons) {
            String customId = "rolespanel:" + panelButton.panelId() + ":" + panelButton.roleId();
            String label = EmbedStyle.truncate(panelButton.label(), 80);
            Button base = Button.secondary(customId, label);
            if (panelButton.emoji() != null && !panelButton.emoji().isBlank()) {
                try {
                    base = base.withEmoji(Emoji.fromFormatted(panelButton.emoji()));
                } catch (IllegalArgumentException ignored) {
                    logger.warn("Emoji invalide ignorée pour panelId={}, roleId={}", panelButton.panelId(), panelButton.roleId());
                }
            }
            buttons.add(base);
        }
        return buttons;
    }

    public void applyButtons(Message message, List<RolesPanelStore.RolePanelButton> panelButtons) {
        List<Button> buttons = buildButtons(panelButtons);
        if (buttons.isEmpty()) {
            message.editMessageComponents().queue();
        } else {
            message.editMessageComponents(ActionRow.of(buttons)).queue();
        }
    }
}
