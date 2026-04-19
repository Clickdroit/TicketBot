package fr.sakura.bot.commands;

import fr.sakura.bot.utils.EmbedStyle;
import fr.sakura.bot.utils.RolesPanelService;
import fr.sakura.bot.utils.RolesPanelStore;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;

import java.util.List;

public class RolesPanelCommand implements ICommand {

    private final RolesPanelService rolesPanelService;

    public RolesPanelCommand(RolesPanelService rolesPanelService) {
        this.rolesPanelService = rolesPanelService;
    }

    @Override
    public String getName() {
        return "rolespanel";
    }

    @Override
    public SlashCommandData getCommandData() {
        return Commands.slash(getName(), "Gestion des panels de rôles")
                .addSubcommands(
                        new SubcommandData("create", "Crée un panel dans le salon courant"),
                        new SubcommandData("add", "Ajoute un bouton de rôle")
                                .addOptions(
                                        new OptionData(OptionType.INTEGER, "panel_id", "ID du panel", true),
                                        new OptionData(OptionType.ROLE, "role", "Rôle ciblé", true),
                                        new OptionData(OptionType.STRING, "label", "Texte du bouton", true).setMaxLength(80),
                                        new OptionData(OptionType.STRING, "emoji", "Emoji (optionnel)")
                                ),
                        new SubcommandData("remove", "Retire un bouton de rôle")
                                .addOptions(
                                        new OptionData(OptionType.INTEGER, "panel_id", "ID du panel", true),
                                        new OptionData(OptionType.ROLE, "role", "Rôle ciblé", true)
                                ),
                        new SubcommandData("list", "Liste les panels actifs")
                )
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MANAGE_ROLES));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (event.getGuild() == null || event.getMember() == null) {
            event.reply("❌ Commande utilisable uniquement sur un serveur.").setEphemeral(true).queue();
            return;
        }

        String sub = event.getSubcommandName();
        if (sub == null) {
            event.reply("❌ Sous-commande manquante.").setEphemeral(true).queue();
            return;
        }

        switch (sub) {
            case "create" -> handleCreate(event);
            case "add" -> handleAdd(event);
            case "remove" -> handleRemove(event);
            case "list" -> handleList(event);
            default -> event.reply("❌ Sous-commande inconnue.").setEphemeral(true).queue();
        }
    }

    private void handleCreate(SlashCommandInteractionEvent event) {
        event.deferReply(true).queue();
        EmbedBuilder embed = EmbedStyle.newInfoEmbed("🎭", "Choisis tes rôles");
        embed.setDescription("Clique sur un bouton pour ajouter/retirer un rôle.");
        EmbedStyle.setFooter(embed, "Panel auto-rôles");

        event.getChannel().sendMessageEmbeds(embed.build()).queue(message -> {
            RolesPanelStore.RolePanel panel = rolesPanelService.createPanel(
                    event.getGuild().getId(),
                    event.getChannel().getId(),
                    message.getId()
            );
            if (panel == null) {
                event.getHook().sendMessage("❌ Impossible de créer le panel en base.").queue();
                return;
            }
            event.getHook().sendMessage("✅ Panel créé : ID **" + panel.id() + "** dans " + event.getChannel().getAsMention()).queue();
        }, err -> event.getHook().sendMessage("❌ Impossible d'envoyer le panel.").queue());
    }

    private void handleAdd(SlashCommandInteractionEvent event) {
        long panelId = event.getOption("panel_id", 0L, OptionMapping::getAsLong);
        Role role = event.getOption("role", OptionMapping::getAsRole);
        String label = event.getOption("label", "", OptionMapping::getAsString);
        String emoji = event.getOption("emoji", "", OptionMapping::getAsString).trim();

        if (role == null || panelId <= 0) {
            event.reply("❌ Paramètres invalides.").setEphemeral(true).queue();
            return;
        }

        var guild = event.getGuild();
        var self = guild.getSelfMember();
        if (!self.hasPermission(Permission.MANAGE_ROLES) || !self.canInteract(role)) {
            event.reply("❌ Je ne peux pas gérer ce rôle (permission/hierarchie).").setEphemeral(true).queue();
            return;
        }

        boolean ok = rolesPanelService.addButton(guild.getId(), panelId, role.getId(), label, emoji.isBlank() ? null : emoji);
        if (!ok) {
            event.reply("❌ Ajout impossible (panel introuvable ou limite de 5 boutons atteinte).").setEphemeral(true).queue();
            return;
        }

        RolesPanelStore.RolePanel panel = rolesPanelService.getPanel(guild.getId(), panelId);
        rolesPanelService.refreshPanelMessage(guild, panel);
        event.reply("✅ Bouton ajouté au panel **" + panelId + "** pour " + role.getAsMention() + ".").setEphemeral(true).queue();
    }

    private void handleRemove(SlashCommandInteractionEvent event) {
        long panelId = event.getOption("panel_id", 0L, OptionMapping::getAsLong);
        Role role = event.getOption("role", OptionMapping::getAsRole);
        if (role == null || panelId <= 0) {
            event.reply("❌ Paramètres invalides.").setEphemeral(true).queue();
            return;
        }

        boolean removed = rolesPanelService.removeButton(event.getGuild().getId(), panelId, role.getId());
        if (!removed) {
            event.reply("❌ Aucun bouton trouvé pour ce rôle/panel.").setEphemeral(true).queue();
            return;
        }

        RolesPanelStore.RolePanel panel = rolesPanelService.getPanel(event.getGuild().getId(), panelId);
        rolesPanelService.refreshPanelMessage(event.getGuild(), panel);
        event.reply("✅ Bouton supprimé du panel **" + panelId + "**.").setEphemeral(true).queue();
    }

    private void handleList(SlashCommandInteractionEvent event) {
        List<RolesPanelStore.RolePanel> panels = rolesPanelService.listPanels(event.getGuild().getId());
        if (panels.isEmpty()) {
            event.reply("ℹ️ Aucun panel actif.").setEphemeral(true).queue();
            return;
        }

        StringBuilder content = new StringBuilder("📋 Panels actifs :\n");
        for (RolesPanelStore.RolePanel panel : panels) {
            content.append("• ID **").append(panel.id())
                    .append("** — salon <#").append(panel.channelId())
                    .append("> — boutons: ").append(panel.buttons().size())
                    .append(" — [message](https://discord.com/channels/")
                    .append(event.getGuild().getId()).append("/")
                    .append(panel.channelId()).append("/")
                    .append(panel.messageId()).append(")\n");
        }
        event.reply(content.toString()).setEphemeral(true).queue();
    }
}
