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
import net.dv8tion.jda.api.EmbedBuilder;
import fr.sakura.bot.utils.EmbedStyle;
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
        return Commands.slash(getName(), "Configure les paramètres du bot")
                .addSubcommands(
                        new SubcommandData("view", "Affiche la configuration actuelle du serveur"),
                        new SubcommandData("antispam", "Active ou désactive l'anti-spam")
                                .addOptions(new OptionData(OptionType.BOOLEAN, "etat", "True pour activer, False pour désactiver", true)),
                        new SubcommandData("antilink", "Active ou désactive le filtre de liens")
                                .addOptions(new OptionData(OptionType.BOOLEAN, "etat", "True pour activer, False pour désactiver", true)),
                        new SubcommandData("giflinks", "Autorise ou bloque les liens GIF dans l'anti-liens")
                                .addOptions(new OptionData(OptionType.BOOLEAN, "etat", "True pour autoriser, False pour bloquer", true)),
                        new SubcommandData("spamlimit", "Définit le nombre de messages max dans la fenêtre anti-spam")
                                .addOptions(new OptionData(OptionType.INTEGER, "valeur", "Valeur de 3 à 20", true).setMinValue(3).setMaxValue(20)),
                        new SubcommandData("spamwindow", "Définit la fenêtre anti-spam en secondes")
                                .addOptions(new OptionData(OptionType.INTEGER, "secondes", "Valeur de 2 à 15", true).setMinValue(2).setMaxValue(15)),
                        new SubcommandData("strikes", "Définit le nombre d'infractions avant timeout auto")
                                .addOptions(new OptionData(OptionType.INTEGER, "valeur", "Valeur de 1 à 10", true).setMinValue(1).setMaxValue(10)),
                        new SubcommandData("timeout", "Définit la durée du timeout automatique en minutes")
                                .addOptions(new OptionData(OptionType.INTEGER, "minutes", "Valeur de 1 à 1440", true).setMinValue(1).setMaxValue(1440)),
                        new SubcommandData("strikereset", "Définit le reset des strikes auto-mod en minutes")
                                .addOptions(new OptionData(OptionType.INTEGER, "minutes", "Valeur de 1 à 180", true).setMinValue(1).setMaxValue(180)),
                        new SubcommandData("noticecooldown", "Définit le cooldown des messages AutoMod en secondes")
                                .addOptions(new OptionData(OptionType.INTEGER, "secondes", "Valeur de 3 à 120", true).setMinValue(3).setMaxValue(120)),
                        new SubcommandData("xpcooldown", "Définit le cooldown XP en secondes")
                                .addOptions(new OptionData(OptionType.INTEGER, "secondes", "Valeur de 5 à 300", true).setMinValue(5).setMaxValue(300)),
                        new SubcommandData("xpminlen", "Définit la longueur minimale d'un message pour gagner de l'XP")
                                .addOptions(new OptionData(OptionType.INTEGER, "valeur", "Valeur de 1 à 300", true).setMinValue(1).setMaxValue(300)),
                        new SubcommandData("xpminalnum", "Définit le nombre minimal de caractères alphanumériques")
                                .addOptions(new OptionData(OptionType.INTEGER, "valeur", "Valeur de 1 à 100", true).setMinValue(1).setMaxValue(100)),
                        new SubcommandData("xpgain", "Définit la plage de gain XP aléatoire")
                                .addOptions(
                                        new OptionData(OptionType.INTEGER, "min", "XP minimum", true).setMinValue(1).setMaxValue(1000),
                                        new OptionData(OptionType.INTEGER, "max", "XP maximum", true).setMinValue(1).setMaxValue(1000)
                                )
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
            case "view" -> {
                EmbedBuilder embed = EmbedStyle.newInfoEmbed("⚙️", "Configuration du serveur");
                embed.addField("AutoMod - Liens", "**Anti-liens :** " + (settingsManager.isAntiLinkEnabled(guildId) ? "✅" : "❌") + "\n" +
                        "**Liens GIF auto :** " + (settingsManager.isGifLinksAllowed(guildId) ? "✅" : "❌"), false);
                embed.addField("AutoMod - Spam", "**Anti-spam :** " + (settingsManager.isAntiSpamEnabled(guildId) ? "✅" : "❌") + "\n" +
                        "**Limite spam :** " + settingsManager.getSpamLimit(guildId) + "\n" +
                        "**Fenêtre spam :** " + (settingsManager.getSpamWindowMs(guildId) / 1000) + "s", false);
                embed.addField("AutoMod - Sanctions", "**Infractions avant timeout :** " + settingsManager.getAutomodStrikesToTimeout(guildId) + "\n" +
                        "**Reset des strikes :** " + settingsManager.getAutomodStrikeResetMinutes(guildId) + " min\n" +
                        "**Durée timeout auto :** " + settingsManager.getAutomodTimeoutMinutes(guildId) + " min\n" +
                        "**Cooldown notices :** " + settingsManager.getAutomodNoticeCooldownSeconds(guildId) + "s", false);
                embed.addField("Système d'XP", "**Cooldown XP :** " + (settingsManager.getXpCooldownMs(guildId) / 1000) + "s\n" +
                        "**Gain aléatoire :** " + settingsManager.getXpMinGain(guildId) + " à " + settingsManager.getXpMaxGain(guildId) + " XP\n" +
                        "**Lettres minimum :** " + settingsManager.getXpMinMessageLength(guildId) + "\n" +
                        "**Alphanumériques min :** " + settingsManager.getXpMinAlnumCount(guildId), false);
                event.replyEmbeds(embed.build()).queue();
            }
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
                event.reply("✅ Limite anti-spam réglée à **" + valeur + "** message(s).").setEphemeral(true).queue();
            }
            case "spamwindow" -> {
                int secondes = event.getOption("secondes", 5, OptionMapping::getAsInt);
                settingsManager.setSpamWindowMs(guildId, secondes * 1000L);
                event.reply("✅ Fenêtre anti-spam réglée à **" + secondes + "s**.").setEphemeral(true).queue();
            }
            case "strikes" -> {
                int valeur = event.getOption("valeur", 3, OptionMapping::getAsInt);
                settingsManager.setAutomodStrikesToTimeout(guildId, valeur);
                event.reply("✅ Timeout auto après **" + valeur + "** infraction(s).").setEphemeral(true).queue();
            }
            case "timeout" -> {
                int minutes = event.getOption("minutes", 10, OptionMapping::getAsInt);
                settingsManager.setAutomodTimeoutMinutes(guildId, minutes);
                event.reply("✅ Timeout automatique réglé à **" + minutes + "** minute(s).").setEphemeral(true).queue();
            }
            case "strikereset" -> {
                int minutes = event.getOption("minutes", 10, OptionMapping::getAsInt);
                settingsManager.setAutomodStrikeResetMinutes(guildId, minutes);
                event.reply("✅ Reset des strikes AutoMod réglé à **" + minutes + "** minute(s).").setEphemeral(true).queue();
            }
            case "noticecooldown" -> {
                int seconds = event.getOption("secondes", 15, OptionMapping::getAsInt);
                settingsManager.setAutomodNoticeCooldownSeconds(guildId, seconds);
                event.reply("✅ Cooldown des notices AutoMod réglé à **" + seconds + "** seconde(s).").setEphemeral(true).queue();
            }
            case "xpcooldown" -> {
                int seconds = event.getOption("secondes", 60, OptionMapping::getAsInt);
                settingsManager.setXpCooldownMs(guildId, seconds * 1000);
                event.reply("✅ Cooldown XP réglé à **" + seconds + "** seconde(s).").setEphemeral(true).queue();
            }
            case "xpminlen" -> {
                int value = event.getOption("valeur", 5, OptionMapping::getAsInt);
                settingsManager.setXpMinMessageLength(guildId, value);
                event.reply("✅ Longueur minimale XP réglée à **" + value + "**.").setEphemeral(true).queue();
            }
            case "xpminalnum" -> {
                int value = event.getOption("valeur", 3, OptionMapping::getAsInt);
                settingsManager.setXpMinAlnumCount(guildId, value);
                event.reply("✅ Seuil alphanumérique XP réglé à **" + value + "**.").setEphemeral(true).queue();
            }
            case "xpgain" -> {
                int min = event.getOption("min", 15, OptionMapping::getAsInt);
                int max = event.getOption("max", 25, OptionMapping::getAsInt);
                settingsManager.setXpGainRange(guildId, min, max);
                event.reply("✅ Gain XP réglé sur la plage **" + Math.min(min, max) + " - " + Math.max(min, max) + "**.")
                        .setEphemeral(true)
                        .queue();
            }
            default -> event.reply("❌ Sous-commande inconnue.").setEphemeral(true).queue();
        }

        logger.info("Config: subcommand={} par userId={} guildId={}", subcommand, event.getUser().getId(), guildId);
    }
}
