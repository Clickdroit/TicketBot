package fr.sakura.bot.core.service;


import fr.sakura.bot.core.store.RolesPanelStore;
import fr.sakura.bot.core.util.EmbedStyle;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class RolesPanelService {

    private static final Logger logger = LoggerFactory.getLogger(RolesPanelService.class);
    public static final int MAX_BUTTONS_PER_ROW = 5;
    public static final int MAX_ROWS = 5;
    public static final int MAX_BUTTONS = MAX_BUTTONS_PER_ROW * MAX_ROWS; // 25

    private final RolesPanelStore store;

    public RolesPanelService(RolesPanelStore store) {
        this.store = store;
    }

    public RolesPanelStore.RolePanel createPanel(String guildId, String channelId, String messageId, boolean exclusive, boolean useButtons, String title, String headerEmoji) {
        return store.createPanel(guildId, channelId, messageId, exclusive, useButtons, title, headerEmoji);
    }

    public boolean deletePanel(Guild guild, long panelId) {
        RolesPanelStore.RolePanel panel = store.findPanel(guild.getId(), panelId);
        if (panel == null) return false;

        // Tentative de suppression du message Discord
        TextChannel channel = guild.getTextChannelById(panel.channelId());
        if (channel != null) {
            channel.deleteMessageById(panel.messageId()).queue(
                    ok -> logger.info("RolesPanel: message supprime pour panelId={}", panelId),
                    err -> logger.warn("RolesPanel: impossible de supprimer le message panelId={}", panelId)
            );
        }

        return store.deletePanel(guild.getId(), panelId);
    }

    public RolesPanelStore.RolePanel getPanel(String guildId, long panelId) {
        return store.findPanel(guildId, panelId);
    }

    public boolean updatePanelLocation(String guildId, long panelId, String channelId, String messageId) {
        return store.updatePanelLocation(guildId, panelId, channelId, messageId);
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
                message -> {
                    if (panel.useButtons()) {
                        applyButtons(message, panel.buttons());
                    } else {
                        // Mode Réactions : Mise en page esthétique Sakura
                        updateReactionPanelDisplay(message, panel);
                        syncReactions(message, panel.buttons());
                    }
                },
                error -> logger.warn("RolesPanel: message introuvable panelId={}, messageId={}", panel.id(), panel.messageId(), error)
        );
    }

    private void updateReactionPanelDisplay(Message message, RolesPanelStore.RolePanel panel) {
        String title = panel.title() != null ? panel.title() : "Choisis tes rôles";
        String headerEmoji = panel.headerEmoji() != null ? panel.headerEmoji() : "🎭";
        
        StringBuilder desc = new StringBuilder();
        desc.append("**").append(headerEmoji).append(" ・ ").append(title).append("**\n\n");

        for (int i = 0; i < panel.buttons().size(); i++) {
            RolesPanelStore.RolePanelButton btn = panel.buttons().get(i);
            String emoji = btn.emoji() != null ? btn.emoji() : getNumberEmoji(i + 1);
            desc.append(emoji).append(" ・ <@&").append(btn.roleId()).append(">\n");
        }

        if (panel.exclusive()) {
            desc.append("\n*(Un seul rôle possible)*");
        }

        net.dv8tion.jda.api.EmbedBuilder embed = new net.dv8tion.jda.api.EmbedBuilder();
        embed.setColor(EmbedStyle.SAKURA_PINK);
        embed.setDescription(desc.toString());
        EmbedStyle.setFooter(embed, "Sakura Role Panel");
        
        message.editMessageEmbeds(embed.build()).setComponents().queue();
    }

    private String getNumberEmoji(int n) {
        return switch (n) {
            case 1 -> "1️⃣";
            case 2 -> "2️⃣";
            case 3 -> "3️⃣";
            case 4 -> "4️⃣";
            case 5 -> "5️⃣";
            case 6 -> "6️⃣";
            case 7 -> "7️⃣";
            case 8 -> "8️⃣";
            case 9 -> "9️⃣";
            case 10 -> "🔟";
            default -> "🔘";
        };
    }

    private void syncReactions(Message message, List<RolesPanelStore.RolePanelButton> buttons) {
        for (int i = 0; i < buttons.size(); i++) {
            RolesPanelStore.RolePanelButton button = buttons.get(i);
            String emojiStr = button.emoji() != null ? button.emoji() : getNumberEmoji(i + 1);
            try {
                message.addReaction(Emoji.fromFormatted(emojiStr)).queue(
                        null,
                        err -> logger.warn("Impossible d'ajouter la réaction {} sur messageId={}", emojiStr, message.getId())
                );
            } catch (Exception ignored) {}
        }
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
        RolesPanelStore.RolePanel panel = store.findPanel(event.getGuild().getId(), panelId);
        if (panel == null) {
            event.reply("❌ Panel introuvable.").setEphemeral(true).queue();
            return;
        }
        RolesPanelStore.RolePanelButton button = panel.buttons().stream()
                .filter(b -> b.roleId().equals(roleId))
                .findFirst()
                .orElse(null);
        if (button == null) {
            event.reply("❌ Bouton de rôle introuvable ou obsolète.").setEphemeral(true).queue();
            return;
        }

        performToggle(event.getGuild(), event.getMember(), panel, button, successMsg -> {
            event.reply(successMsg).setEphemeral(true).queue();
        }, errMsg -> {
            event.reply(errMsg).setEphemeral(true).queue();
        });
    }

    public void handleReaction(Guild guild, Member member, String messageId, Emoji emoji, boolean added) {
        List<RolesPanelStore.RolePanel> panels = listPanels(guild.getId());
        RolesPanelStore.RolePanel panel = panels.stream()
                .filter(p -> p.messageId().equals(messageId))
                .findFirst()
                .orElse(null);

        if (panel == null || panel.useButtons()) return;

        String emojiFormatted = emoji.getFormatted();
        RolesPanelStore.RolePanelButton button = null;
        
        for (int i = 0; i < panel.buttons().size(); i++) {
            RolesPanelStore.RolePanelButton b = panel.buttons().get(i);
            String bEmoji = b.emoji() != null ? b.emoji() : getNumberEmoji(i + 1);
            if (bEmoji.equals(emojiFormatted)) {
                button = b;
                break;
            }
        }

        if (button == null) return;

        Role role = guild.getRoleById(button.roleId());
        if (role == null) return;

        boolean hasRole = member.getRoles().contains(role);

        if (!added) {
            // Si la réaction est enlevée et que le membre a le rôle -> on l'enlève
            if (hasRole) {
                Member self = guild.getSelfMember();
                if (self.hasPermission(Permission.MANAGE_ROLES) && self.canInteract(role)) {
                    guild.removeRoleFromMember(member, role).queue();
                }
            }
            return;
        }

        // Si la réaction est ajoutée et que le membre n'a pas le rôle -> on l'ajoute
        if (!hasRole) {
            performToggle(guild, member, panel, button, ok -> {}, err -> {});
        }
    }

    private void performToggle(Guild guild, Member member, RolesPanelStore.RolePanel panel, RolesPanelStore.RolePanelButton button, 
                               java.util.function.Consumer<String> onSuccess, java.util.function.Consumer<String> onError) {
        String roleId = button.roleId();
        Role role = guild.getRoleById(roleId);
        if (role == null) {
            onError.accept("❌ Le rôle associé n'existe plus.");
            return;
        }

        Member self = guild.getSelfMember();
        if (!self.hasPermission(Permission.MANAGE_ROLES) || !self.canInteract(role) || !self.canInteract(member)) {
            onError.accept("❌ Je ne peux pas gérer ce rôle (permission/hierarchie).");
            return;
        }

        if (member.getRoles().stream().anyMatch(r -> r.getId().equals(roleId))) {
            guild.removeRoleFromMember(member, role).queue(
                    ok -> onSuccess.accept("➖ Rôle retiré : " + role.getAsMention()),
                    err -> onError.accept("❌ Impossible de retirer ce rôle.")
            );
            return;
        }

        if (panel.exclusive()) {
            List<Role> rolesToRemove = new ArrayList<>();
            for (RolesPanelStore.RolePanelButton b : panel.buttons()) {
                if (b.roleId().equals(roleId)) continue;
                Role r = guild.getRoleById(b.roleId());
                if (r != null && member.getRoles().contains(r)) {
                    rolesToRemove.add(r);
                }
            }
            
            for (Role r : rolesToRemove) {
                guild.removeRoleFromMember(member, r).queue();
            }
        }

        guild.addRoleToMember(member, role).queue(
                ok -> onSuccess.accept("➕ Rôle ajouté : " + role.getAsMention()),
                err -> onError.accept("❌ Impossible d'ajouter ce rôle.")
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
            return;
        }

        List<ActionRow> rows = new ArrayList<>();
        for (int i = 0; i < buttons.size(); i += MAX_BUTTONS_PER_ROW) {
            int end = Math.min(i + MAX_BUTTONS_PER_ROW, buttons.size());
            rows.add(ActionRow.of(buttons.subList(i, end)));
        }
        message.editMessageComponents(rows).queue();
    }
}
