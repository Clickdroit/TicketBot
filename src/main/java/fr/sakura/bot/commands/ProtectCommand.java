package fr.sakura.bot.commands;

import fr.sakura.bot.database.ProtectSettingsManager;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandGroupData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProtectCommand implements ICommand {

    private static final Logger logger = LoggerFactory.getLogger(ProtectCommand.class);
    private final ProtectSettingsManager protectSettingsManager;

    public ProtectCommand(ProtectSettingsManager protectSettingsManager) {
        this.protectSettingsManager = protectSettingsManager;
    }

    @Override
    public String getName() {
        return "protect";
    }

    @Override
    public SlashCommandData getCommandData() {
        return Commands.slash(getName(), "Configure les protections avancées (Sakura Protect)")
                .addSubcommandGroups(
                        new SubcommandGroupData("whitelist", "Gère la liste blanche des utilisateurs de confiance")
                                .addSubcommands(
                                        new SubcommandData("add", "Ajoute un utilisateur à la whitelist")
                                                .addOptions(new OptionData(OptionType.USER, "utilisateur", "L'utilisateur à ajouter", true)),
                                        new SubcommandData("remove", "Retire un utilisateur de la whitelist")
                                                .addOptions(new OptionData(OptionType.USER, "utilisateur", "L'utilisateur à retirer", true)),
                                        new SubcommandData("list", "Affiche la liste blanche")
                                )
                )
                .addSubcommands(
                        new SubcommandData("module", "Active ou désactive un module de protection")
                                .addOptions(
                                        new OptionData(OptionType.STRING, "type", "Le module à configurer", true)
                                                .addChoice("Anti-Bot (Joins)", "antibot")
                                                .addChoice("Anti-Raid (Vandalisme)", "antiraid")
                                                .addChoice("Anti-Phishing (Liens)", "antiphishing"),
                                        new OptionData(OptionType.BOOLEAN, "etat", "True pour activer, False pour désactiver", true)
                                ),
                        new SubcommandData("accountage", "Définit l'âge minimum du compte (en heures) pour rejoindre")
                                .addOptions(new OptionData(OptionType.INTEGER, "heures", "Âge en heures (ex: 24)", true).setMinValue(0))
                )
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (event.getGuild() == null) {
            event.reply("❌ Commande utilisable uniquement sur un serveur.").setEphemeral(true).queue();
            return;
        }

        String guildId = event.getGuild().getId();
        String group = event.getSubcommandGroup();
        String subcommand = event.getSubcommandName();

        if (group != null && group.equals("whitelist")) {
            handleWhitelist(event, guildId, subcommand);
            return;
        }

        if (subcommand != null) {
            switch (subcommand) {
                case "module" -> {
                    String type = event.getOption("type", "", OptionMapping::getAsString);
                    boolean etat = event.getOption("etat", false, OptionMapping::getAsBoolean);
                    switch (type) {
                        case "antibot" -> protectSettingsManager.setAntiBotEnabled(guildId, etat);
                        case "antiraid" -> protectSettingsManager.setAntiRaidEnabled(guildId, etat);
                        case "antiphishing" -> protectSettingsManager.setAntiPhishingEnabled(guildId, etat);
                    }
                    event.reply("✅ Module **" + type + "** désormais **" + (etat ? "activé" : "désactivé") + "**.").setEphemeral(true).queue();
                }
                case "accountage" -> {
                    int heures = event.getOption("heures", 24, OptionMapping::getAsInt);
                    protectSettingsManager.setMinAccountAgeHours(guildId, heures);
                    event.reply("✅ Âge minimum du compte réglé à **" + heures + " heures**.").setEphemeral(true).queue();
                }
                default -> event.reply("❌ Sous-commande inconnue.").setEphemeral(true).queue();
            }
        }
    }

    private void handleWhitelist(SlashCommandInteractionEvent event, String guildId, String subcommand) {
        switch (subcommand) {
            case "add" -> {
                User user = event.getOption("utilisateur", OptionMapping::getAsUser);
                if (user == null) return;
                protectSettingsManager.addToWhitelist(guildId, user.getId());
                event.reply("✅ " + user.getAsMention() + " a été ajouté à la whitelist Protect.").setEphemeral(true).queue();
            }
            case "remove" -> {
                User user = event.getOption("utilisateur", OptionMapping::getAsUser);
                if (user == null) return;
                protectSettingsManager.removeFromWhitelist(guildId, user.getId());
                event.reply("✅ " + user.getAsMention() + " a été retiré de la whitelist Protect.").setEphemeral(true).queue();
            }
            case "list" -> {
                var list = protectSettingsManager.getWhitelist(guildId);
                if (list.isEmpty()) {
                    event.reply("La whitelist est vide.").setEphemeral(true).queue();
                } else {
                    StringBuilder sb = new StringBuilder("**Whitelist Sakura Protect :**\n");
                    for (String id : list) {
                        sb.append("- <@").append(id).append("> (").append(id).append(")\n");
                    }
                    event.reply(sb.toString()).setEphemeral(true).queue();
                }
            }
        }
    }
}
