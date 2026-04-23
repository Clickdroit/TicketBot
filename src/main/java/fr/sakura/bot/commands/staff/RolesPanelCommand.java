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
        return Commands.slash(getName(), "Gestion des panels de rÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â´les")
                .addSubcommands(
                        new SubcommandData("create", "CrÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â©e un panel dans le salon courant"),
                        new SubcommandData("add", "Ajoute un bouton de rÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â´le")
                                .addOptions(
                                        new OptionData(OptionType.INTEGER, "panel_id", "ID du panel", true),
                                        new OptionData(OptionType.ROLE, "role", "RÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â´le ciblÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â©", true),
                                        new OptionData(OptionType.STRING, "label", "Texte du bouton", true).setMaxLength(80),
                                        new OptionData(OptionType.STRING, "emoji", "Emoji (optionnel)")
                                ),
                        new SubcommandData("remove", "Retire un bouton de rÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â´le")
                                .addOptions(
                                        new OptionData(OptionType.INTEGER, "panel_id", "ID du panel", true),
                                        new OptionData(OptionType.ROLE, "role", "RÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â´le ciblÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â©", true)
                                ),
                        new SubcommandData("list", "Liste les panels actifs")
                )
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MANAGE_ROLES));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (event.getGuild() == null || event.getMember() == null) {
            event.reply("ÃƒÆ’Ã‚Â¢Ãƒâ€šÃ‚ÂÃƒâ€¦Ã¢â‚¬â„¢ Commande utilisable uniquement sur un serveur.").setEphemeral(true).queue();
            return;
        }

        String sub = event.getSubcommandName();
        if (sub == null) {
            event.reply("ÃƒÆ’Ã‚Â¢Ãƒâ€šÃ‚ÂÃƒâ€¦Ã¢â‚¬â„¢ Sous-commande manquante.").setEphemeral(true).queue();
            return;
        }

        switch (sub) {
            case "create" -> handleCreate(event);
            case "add" -> handleAdd(event);
            case "remove" -> handleRemove(event);
            case "list" -> handleList(event);
            default -> event.reply("ÃƒÆ’Ã‚Â¢Ãƒâ€šÃ‚ÂÃƒâ€¦Ã¢â‚¬â„¢ Sous-commande inconnue.").setEphemeral(true).queue();
        }
    }

    private void handleCreate(SlashCommandInteractionEvent event) {
        event.deferReply(true).queue();
        EmbedBuilder embed = EmbedStyle.newInfoEmbed("ÃƒÆ’Ã‚Â°Ãƒâ€¦Ã‚Â¸Ãƒâ€¦Ã‚Â½Ãƒâ€šÃ‚Â­", "Choisis tes rÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â´les");
        embed.setDescription("Clique sur un bouton pour ajouter/retirer un rÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â´le.");
        EmbedStyle.setFooter(embed, "Panel auto-rÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â´les");

        event.getChannel().sendMessageEmbeds(embed.build()).queue(message -> {
            RolesPanelStore.RolePanel panel = rolesPanelService.createPanel(
                    event.getGuild().getId(),
                    event.getChannel().getId(),
                    message.getId()
            );
            if (panel == null) {
                event.getHook().sendMessage("ÃƒÆ’Ã‚Â¢Ãƒâ€šÃ‚ÂÃƒâ€¦Ã¢â‚¬â„¢ Impossible de crÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â©er le panel en base.").queue();
                return;
            }
            event.getHook().sendMessage("ÃƒÆ’Ã‚Â¢Ãƒâ€¦Ã¢â‚¬Å“ÃƒÂ¢Ã¢â€šÂ¬Ã‚Â¦ Panel crÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â©ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â© : ID **" + panel.id() + "** dans " + event.getChannel().getAsMention()).queue();
        }, err -> event.getHook().sendMessage("ÃƒÆ’Ã‚Â¢Ãƒâ€šÃ‚ÂÃƒâ€¦Ã¢â‚¬â„¢ Impossible d'envoyer le panel.").queue());
    }

    private void handleAdd(SlashCommandInteractionEvent event) {
        long panelId = event.getOption("panel_id", 0L, OptionMapping::getAsLong);
        Role role = event.getOption("role", OptionMapping::getAsRole);
        String label = event.getOption("label", "", OptionMapping::getAsString);
        String emoji = event.getOption("emoji", "", OptionMapping::getAsString).trim();

        if (role == null || panelId <= 0) {
            event.reply("ÃƒÆ’Ã‚Â¢Ãƒâ€šÃ‚ÂÃƒâ€¦Ã¢â‚¬â„¢ ParamÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¨tres invalides.").setEphemeral(true).queue();
            return;
        }

        var guild = event.getGuild();
        var self = guild.getSelfMember();
        if (!self.hasPermission(Permission.MANAGE_ROLES) || !self.canInteract(role)) {
            event.reply("ÃƒÆ’Ã‚Â¢Ãƒâ€šÃ‚ÂÃƒâ€¦Ã¢â‚¬â„¢ Je ne peux pas gÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â©rer ce rÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â´le (permission/hierarchie).").setEphemeral(true).queue();
            return;
        }

        boolean ok = rolesPanelService.addButton(guild.getId(), panelId, role.getId(), label, emoji.isBlank() ? null : emoji);
        if (!ok) {
            event.reply("ÃƒÆ’Ã‚Â¢Ãƒâ€šÃ‚ÂÃƒâ€¦Ã¢â‚¬â„¢ Ajout impossible (panel introuvable ou limite de 5 boutons atteinte).").setEphemeral(true).queue();
            return;
        }

        RolesPanelStore.RolePanel panel = rolesPanelService.getPanel(guild.getId(), panelId);
        rolesPanelService.refreshPanelMessage(guild, panel);
        event.reply("ÃƒÆ’Ã‚Â¢Ãƒâ€¦Ã¢â‚¬Å“ÃƒÂ¢Ã¢â€šÂ¬Ã‚Â¦ Bouton ajoutÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â© au panel **" + panelId + "** pour " + role.getAsMention() + ".").setEphemeral(true).queue();
    }

    private void handleRemove(SlashCommandInteractionEvent event) {
        long panelId = event.getOption("panel_id", 0L, OptionMapping::getAsLong);
        Role role = event.getOption("role", OptionMapping::getAsRole);
        if (role == null || panelId <= 0) {
            event.reply("ÃƒÆ’Ã‚Â¢Ãƒâ€šÃ‚ÂÃƒâ€¦Ã¢â‚¬â„¢ ParamÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¨tres invalides.").setEphemeral(true).queue();
            return;
        }

        boolean removed = rolesPanelService.removeButton(event.getGuild().getId(), panelId, role.getId());
        if (!removed) {
            event.reply("ÃƒÆ’Ã‚Â¢Ãƒâ€šÃ‚ÂÃƒâ€¦Ã¢â‚¬â„¢ Aucun bouton trouvÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â© pour ce rÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â´le/panel.").setEphemeral(true).queue();
            return;
        }

        RolesPanelStore.RolePanel panel = rolesPanelService.getPanel(event.getGuild().getId(), panelId);
        rolesPanelService.refreshPanelMessage(event.getGuild(), panel);
        event.reply("ÃƒÆ’Ã‚Â¢Ãƒâ€¦Ã¢â‚¬Å“ÃƒÂ¢Ã¢â€šÂ¬Ã‚Â¦ Bouton supprimÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â© du panel **" + panelId + "**.").setEphemeral(true).queue();
    }

    private void handleList(SlashCommandInteractionEvent event) {
        List<RolesPanelStore.RolePanel> panels = rolesPanelService.listPanels(event.getGuild().getId());
        if (panels.isEmpty()) {
            event.reply("ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¾Ãƒâ€šÃ‚Â¹ÃƒÆ’Ã‚Â¯Ãƒâ€šÃ‚Â¸Ãƒâ€šÃ‚Â Aucun panel actif.").setEphemeral(true).queue();
            return;
        }

        StringBuilder content = new StringBuilder("ÃƒÆ’Ã‚Â°Ãƒâ€¦Ã‚Â¸ÃƒÂ¢Ã¢â€šÂ¬Ã…â€œÃƒÂ¢Ã¢â€šÂ¬Ã‚Â¹ Panels actifs :\n");
        for (RolesPanelStore.RolePanel panel : panels) {
            content.append("ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€šÃ‚Â¢ ID **").append(panel.id())
                    .append("** ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÂ¢Ã¢â€šÂ¬Ã‚Â salon <#").append(panel.channelId())
                    .append("> ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÂ¢Ã¢â€šÂ¬Ã‚Â boutons: ").append(panel.buttons().size())
                    .append(" ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÂ¢Ã¢â€šÂ¬Ã‚Â [message](https://discord.com/channels/")
                    .append(event.getGuild().getId()).append("/")
                    .append(panel.channelId()).append("/")
                    .append(panel.messageId()).append(")\n");
        }
        event.reply(content.toString()).setEphemeral(true).queue();
    }
}
