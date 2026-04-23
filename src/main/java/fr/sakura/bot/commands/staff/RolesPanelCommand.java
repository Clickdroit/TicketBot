package fr.sakura.bot.commands.staff;

import fr.sakura.bot.commands.ICommand;
import fr.sakura.bot.core.util.EmbedStyle;
import fr.sakura.bot.core.service.RolesPanelService;
import fr.sakura.bot.core.store.RolesPanelStore;
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
    public String getCategory() {
        return "Staff & Divers";
    }

    @Override
    public SlashCommandData getCommandData() {
        return Commands.slash(getName(), "Gestion des panels de rôles")
                .addSubcommands(
                        new SubcommandData("create", "Crée un panel dans le salon courant")
                                .addOptions(
                                        new OptionData(OptionType.STRING, "type", "Type d'interaction", true)
                                                .addChoice("Boutons", "BUTTON")
                                                .addChoice("Réactions", "REACTION"),
                                        new OptionData(OptionType.STRING, "title", "Titre du panel (ex: Pings)"),
                                        new OptionData(OptionType.STRING, "header_emoji", "Emoji d'en-tête"),
                                        new OptionData(OptionType.BOOLEAN, "exclusive", "Si vrai, un seul rôle peut être choisi à la fois")
                                ),
                        new SubcommandData("delete", "Supprime un panel")
                                .addOptions(new OptionData(OptionType.INTEGER, "panel_id", "ID du panel", true)),
                        new SubcommandData("add", "Ajoute un bouton de rôle")
                                .addOptions(
                                        new OptionData(OptionType.INTEGER, "panel_id", "ID du panel", true),
                                        new OptionData(OptionType.ROLE, "role", "Rôle ciblé", true),
                                        new OptionData(OptionType.STRING, "label", "Texte du bouton/réaction", true).setMaxLength(80),
                                        new OptionData(OptionType.STRING, "emoji", "Emoji (obligatoire pour Réactions)")
                                ),
                        new SubcommandData("remove", "Retire un bouton de rôle")
                                .addOptions(
                                        new OptionData(OptionType.INTEGER, "panel_id", "ID du panel", true),
                                        new OptionData(OptionType.ROLE, "role", "Rôle ciblé", true)
                                ),
                        new SubcommandData("list", "Liste les panels actifs"),
                        new SubcommandData("update", "Met à jour tous les panels du serveur"),
                        new SubcommandData("restore", "Recrée un panel existant dans le salon courant")
                                .addOptions(new OptionData(OptionType.INTEGER, "panel_id", "ID du panel à restaurer", true))
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
            case "delete" -> handleDelete(event);
            case "add" -> handleAdd(event);
            case "remove" -> handleRemove(event);
            case "list" -> handleList(event);
            case "update" -> handleUpdate(event);
            case "restore" -> handleRestore(event);
            default -> event.reply("❌ Sous-commande inconnue.").setEphemeral(true).queue();
        }
    }

    private void handleRestore(SlashCommandInteractionEvent event) {
        event.deferReply(true).queue();
        long panelId = event.getOption("panel_id", 0L, OptionMapping::getAsLong);
        
        RolesPanelStore.RolePanel panel = rolesPanelService.getPanel(event.getGuild().getId(), panelId);
        if (panel == null) {
            event.getHook().sendMessage("❌ Panel introuvable.").queue();
            return;
        }

        // On envoie le nouveau message
        net.dv8tion.jda.api.EmbedBuilder embed = new net.dv8tion.jda.api.EmbedBuilder();
        embed.setColor(EmbedStyle.SAKURA_PINK);
        embed.setDescription("⌛ Restauration du panel...");

        event.getChannel().sendMessageEmbeds(embed.build()).queue(message -> {
            // Mise à jour de la base de données avec le nouveau salon et nouveau message
            boolean ok = rolesPanelService.updatePanelLocation(event.getGuild().getId(), panelId, event.getChannel().getId(), message.getId());
            if (!ok) {
                event.getHook().sendMessage("❌ Erreur lors de la mise à jour de l'emplacement du panel.").queue();
                return;
            }

            // Rafraîchissement immédiat du contenu (boutons/réactions)
            RolesPanelStore.RolePanel updatedPanel = rolesPanelService.getPanel(event.getGuild().getId(), panelId);
            rolesPanelService.refreshPanelMessage(event.getGuild(), updatedPanel);

            event.getHook().sendMessage("✅ Panel **" + panelId + "** restauré avec succès dans ce salon.").queue();
        }, err -> event.getHook().sendMessage("❌ Impossible d'envoyer le message dans ce salon.").queue());
    }

    private void handleUpdate(SlashCommandInteractionEvent event) {
        event.deferReply(true).queue();
        rolesPanelService.rebuildPanels(event.getGuild());
        event.getHook().sendMessage("🔄 Tous les panels du serveur ont été mis à jour (reconstruction des embeds, boutons et réactions).").queue();
    }

    private void handleCreate(SlashCommandInteractionEvent event) {
        event.deferReply(true).queue();
        String type = event.getOption("type", "BUTTON", OptionMapping::getAsString);
        String title = event.getOption("title", "Choisis tes rôles", OptionMapping::getAsString);
        String headerEmoji = event.getOption("header_emoji", "🎭", OptionMapping::getAsString);
        boolean exclusive = event.getOption("exclusive", false, OptionMapping::getAsBoolean);
        boolean useButtons = type.equals("BUTTON");

        // On envoie un embed temporaire, il sera mis à jour par refreshPanelMessage
        EmbedBuilder embed = EmbedStyle.newInfoEmbed(null, null);
        embed.setDescription("⌛ Initialisation du panel...");

        event.getChannel().sendMessageEmbeds(embed.build()).queue(message -> {
            RolesPanelStore.RolePanel panel = rolesPanelService.createPanel(
                    event.getGuild().getId(),
                    event.getChannel().getId(),
                    message.getId(),
                    exclusive,
                    useButtons,
                    title,
                    headerEmoji
            );
            if (panel == null) {
                event.getHook().sendMessage("❌ Impossible de créer le panel en base.").queue();
                return;
            }
            // Premier rafraîchissement pour afficher le style Sakura
            rolesPanelService.refreshPanelMessage(event.getGuild(), panel);
            event.getHook().sendMessage("✅ Panel créé (Type: " + (useButtons ? "Boutons" : "Réactions") + ") : ID **" + panel.id() + "**").queue();
        }, err -> event.getHook().sendMessage("❌ Impossible d'envoyer le panel.").queue());
    }

    private void handleDelete(SlashCommandInteractionEvent event) {
        long panelId = event.getOption("panel_id", 0L, OptionMapping::getAsLong);
        if (panelId <= 0) {
            event.reply("❌ ID de panel invalide.").setEphemeral(true).queue();
            return;
        }

        boolean ok = rolesPanelService.deletePanel(event.getGuild(), panelId);
        if (ok) {
            event.reply("✅ Panel **" + panelId + "** supprimé.").setEphemeral(true).queue();
        } else {
            event.reply("❌ Panel introuvable.").setEphemeral(true).queue();
        }
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

        RolesPanelStore.RolePanel panel = rolesPanelService.getPanel(guild.getId(), panelId);
        if (panel == null) {
            event.reply("❌ Panel introuvable.").setEphemeral(true).queue();
            return;
        }

        boolean ok = rolesPanelService.addButton(guild.getId(), panelId, role.getId(), label, emoji.isBlank() ? null : emoji);
        if (!ok) {
            event.reply("❌ Ajout impossible (limite de " + RolesPanelService.MAX_BUTTONS + " options atteinte).").setEphemeral(true).queue();
            return;
        }

        rolesPanelService.refreshPanelMessage(guild, panel);
        event.reply("✅ Option ajoutée au panel **" + panelId + "** pour " + role.getAsMention() + ".").setEphemeral(true).queue();
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
            event.reply("❌ Aucune option trouvée pour ce rôle/panel.").setEphemeral(true).queue();
            return;
        }

        RolesPanelStore.RolePanel panel = rolesPanelService.getPanel(event.getGuild().getId(), panelId);
        rolesPanelService.refreshPanelMessage(event.getGuild(), panel);
        event.reply("✅ Option supprimée du panel **" + panelId + "**.").setEphemeral(true).queue();
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
                    .append("> — type: ").append(panel.useButtons() ? "Boutons" : "Réactions")
                    .append(panel.exclusive() ? " (Exclusif)" : "")
                    .append(" — options: ").append(panel.buttons().size())
                    .append(" — [message](https://discord.com/channels/")
                    .append(event.getGuild().getId()).append("/")
                    .append(panel.channelId()).append("/")
                    .append(panel.messageId()).append(")\n");
        }
        event.reply(content.toString()).setEphemeral(true).queue();
    }
}
