package fr.sakura.bot.commands;

import fr.sakura.bot.database.SettingsManager;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConfigCommand implements ICommand {

    private static final Logger logger = LoggerFactory.getLogger(ConfigCommand.class);
    private final SettingsManager settingsManager;

    public ConfigCommand(SettingsManager settingsManager) {
        this.settingsManager = settingsManager;
    }

    @Override
    public String getName() {
        return "config";
    }

    @Override
    public SlashCommandData getCommandData() {
        return Commands.slash(getName(), "Configure les parametres du bot")
                .addSubcommands(
                        new SubcommandData("antispam", "Active ou desactive l'anti-spam")
                                .addOptions(new OptionData(OptionType.BOOLEAN, "etat", "True pour activer, False pour desactiver", true)),
                        new SubcommandData("antilink", "Active ou desactive le filtre de liens")
                                .addOptions(new OptionData(OptionType.BOOLEAN, "etat", "True pour activer, False pour desactiver", true)),
                        new SubcommandData("giflinks", "Autorise ou bloque les liens GIF dans l'anti-liens")
                                .addOptions(new OptionData(OptionType.BOOLEAN, "etat", "True pour autoriser, False pour bloquer", true)),
                        new SubcommandData("spamlimit", "Definit le nombre de messages max dans la fenetre anti-spam")
                                .addOptions(new OptionData(OptionType.INTEGER, "valeur", "Valeur de 3 a 20", true)
                                        .setMinValue(3)
                                        .setMaxValue(20)),
                        new SubcommandData("spamwindow", "Definit la fenetre anti-spam en secondes")
                                .addOptions(new OptionData(OptionType.INTEGER, "secondes", "Valeur de 2 a 15", true)
                                        .setMinValue(2)
                                        .setMaxValue(15)),
                        new SubcommandData("strikes", "Definit le nombre d'infractions avant timeout auto")
                                .addOptions(new OptionData(OptionType.INTEGER, "valeur", "Valeur de 1 a 10", true)
                                        .setMinValue(1)
                                        .setMaxValue(10)),
                        new SubcommandData("timeout", "Definit la duree du timeout automatique en minutes")
                                .addOptions(new OptionData(OptionType.INTEGER, "minutes", "Valeur de 1 a 1440", true)
                                        .setMinValue(1)
                                        .setMaxValue(1440))
                )
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (event.getGuild() == null) {
            event.reply("❌ Commande utilisable uniquement sur un serveur.").setEphemeral(true).queue();
            return;
        }

        String subcommand = event.getSubcommandName();
        String guildId = event.getGuild().getId();

        if (subcommand == null) {
            event.reply("❌ Sous-commande manquante.").setEphemeral(true).queue();
            return;
        }

        switch (subcommand) {
            case "antispam" -> {
                boolean etat = event.getOption("etat", false, OptionMapping::getAsBoolean);
                settingsManager.setAntiSpamEnabled(guildId, etat);
                event.reply("✅ Anti-spam **" + (etat ? "activé" : "désactivé") + "**.").setEphemeral(true).queue();
            }
            case "antilink" -> {
                boolean etat = event.getOption("etat", false, OptionMapping::getAsBoolean);
                settingsManager.setAntiLinkEnabled(guildId, etat);
                event.reply("✅ Anti-liens **" + (etat ? "activé" : "désactivé") + "**.").setEphemeral(true).queue();
            }
            case "giflinks" -> {
                boolean etat = event.getOption("etat", true, OptionMapping::getAsBoolean);
                settingsManager.setGifLinksAllowed(guildId, etat);
                event.reply("✅ Liens GIF **" + (etat ? "autorisés" : "bloqués") + "**.").setEphemeral(true).queue();
            }
            case "spamlimit" -> {
                int valeur = event.getOption("valeur", 5, OptionMapping::getAsInt);
                settingsManager.setSpamLimit(guildId, valeur);
                event.reply("✅ Limite anti-spam réglée à **" + valeur + "** message(s).")
                        .setEphemeral(true).queue();
            }
            case "spamwindow" -> {
                int secondes = event.getOption("secondes", 5, OptionMapping::getAsInt);
                settingsManager.setSpamWindowMs(guildId, secondes * 1000L);
                event.reply("✅ Fenêtre anti-spam réglée à **" + secondes + "s**.")
                        .setEphemeral(true).queue();
            }
            case "strikes" -> {
                int valeur = event.getOption("valeur", 3, OptionMapping::getAsInt);
                settingsManager.setAutomodStrikesToTimeout(guildId, valeur);
                event.reply("✅ Timeout auto après **" + valeur + "** infraction(s).")
                        .setEphemeral(true).queue();
            }
            case "timeout" -> {
                int minutes = event.getOption("minutes", 10, OptionMapping::getAsInt);
                settingsManager.setAutomodTimeoutMinutes(guildId, minutes);
                event.reply("✅ Timeout automatique réglé à **" + minutes + "** minute(s).")
                        .setEphemeral(true).queue();
            }
            default -> event.reply("❌ Sous-commande inconnue.").setEphemeral(true).queue();
        }

        logger.info("Config: subcommand={} par userId={} guildId={}", subcommand, event.getUser().getId(), guildId);
    }
}
