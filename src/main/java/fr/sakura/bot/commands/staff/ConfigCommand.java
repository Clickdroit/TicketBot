package fr.sakura.bot.commands.staff;

import fr.sakura.bot.database.SettingsManager;
import fr.sakura.bot.database.SettingKey;
import fr.sakura.bot.commands.ICommand;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.EmbedBuilder;
import fr.sakura.bot.core.util.EmbedStyle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Commande de configuration du bot pour les administrateurs.
 */
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
    public String getCategory() {
        return "Staff & Divers";
    }

    @Override
    public SlashCommandData getCommandData() {
        return Commands.slash(getName(), "Configure les modules du bot")
                .addSubcommands(
                        new SubcommandData("view", "Affiche la configuration actuelle"),
                        new SubcommandData("antispam", "Active ou désactive l'anti-spam")
                                .addOptions(new OptionData(OptionType.BOOLEAN, "etat", "Activé ?", true)),
                        new SubcommandData("antilink", "Active ou désactive l'anti-lien")
                                .addOptions(new OptionData(OptionType.BOOLEAN, "etat", "Activé ?", true)),
                        new SubcommandData("allowgifs", "Autorise ou non les liens GIF auto")
                                .addOptions(new OptionData(OptionType.BOOLEAN, "etat", "Autorisé ?", true)),
                        new SubcommandData("levels", "Active ou désactive le système de niveaux et d'XP")
                                .addOptions(new OptionData(OptionType.BOOLEAN, "etat", "Activé ?", true)),
                        new SubcommandData("spamlimit", "Nb messages max dans la fenêtre")
                                .addOptions(new OptionData(OptionType.INTEGER, "limite", "Nb messages (3-20)", true).setRequiredRange(3, 20)),
                        new SubcommandData("spamwindow", "Taille de la fenêtre de détection")
                                .addOptions(new OptionData(OptionType.INTEGER, "secondes", "Secondes (2-15)", true).setRequiredRange(2, 15)),
                        new SubcommandData("xpcooldown", "Délai entre deux gains d'XP")
                                .addOptions(new OptionData(OptionType.INTEGER, "secondes", "Secondes (5-300)", true).setRequiredRange(5, 300)),
                        new SubcommandData("xpminlen", "Longueur min du message pour l'XP")
                                .addOptions(new OptionData(OptionType.INTEGER, "taille", "Caractères (1-300)", true).setRequiredRange(1, 300)),
                        new SubcommandData("xpminan", "Nb min de caractères alphanum")
                                .addOptions(new OptionData(OptionType.INTEGER, "nombre", "Caractères (1-100)", true).setRequiredRange(1, 100)),
                        new SubcommandData("xpgain", "Définit la plage de gain d'XP")
                                .addOptions(
                                        new OptionData(OptionType.INTEGER, "min", "XP Min", true).setRequiredRange(1, 1000),
                                        new OptionData(OptionType.INTEGER, "max", "XP Max", true).setRequiredRange(1, 1000)
                                ),
                        new SubcommandData("welcomechannel", "Définit le salon de bienvenue")
                                .addOptions(new OptionData(OptionType.CHANNEL, "salon", "Salon cible", true).setChannelTypes(ChannelType.TEXT)),
                        new SubcommandData("welcomeimage", "URL de l'image de bienvenue")
                                .addOptions(new OptionData(OptionType.STRING, "url", "URL HTTPS ou 'default'", true).setMaxLength(2000)),
                        new SubcommandData("logchannel", "Définit le salon de logs")
                                .addOptions(new OptionData(OptionType.CHANNEL, "salon", "Salon cible", true).setChannelTypes(ChannelType.TEXT))
                )
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String subcommand = event.getSubcommandName();
        String guildId = event.getGuild().getId();

        if (subcommand == null) return;

        switch (subcommand) {
            case "view" -> {
                EmbedBuilder embed = EmbedStyle.newInfoEmbed("⚙️", "Configuration du serveur");
                embed.addField("AutoMod - Liens", "**Anti-liens :** " + (settingsManager.isAntiLinkEnabled(guildId) ? "✅" : "❌") + "\n" +
                        "**Liens GIF auto :** " + (settingsManager.isGifLinksAllowed(guildId) ? "✅" : "❌"), false);
                embed.addField("AutoMod - Spam", "**Anti-spam :** " + (settingsManager.isAntiSpamEnabled(guildId) ? "✅" : "❌") + "\n" +
                        "**Limite spam :** " + settingsManager.getSpamLimit(guildId) + "\n" +
                        "**Fenêtre spam :** " + (settingsManager.getSpamWindowMs(guildId) / 1000) + "s", false);
                
                boolean levelsEnabled = settingsManager.isLevelsEnabled(guildId);
                embed.addField("XP", "**Système XP :** " + (levelsEnabled ? "✅" : "❌") + "\n" +
                        "**Cooldown :** " + (settingsManager.getXpCooldownMs(guildId) / 1000) + "s\n" +
                        "**Gain :** " + settingsManager.getXpMinGain(guildId) + " à " + settingsManager.getXpMaxGain(guildId) + " XP\n" +
                        "**Taille min :** " + settingsManager.getXpMinMessageLength(guildId) + "\n" +
                        "**Alnum min :** " + settingsManager.getXpMinAlnumCount(guildId), false);
                
                String logChan = settingsManager.getLogChannelId(guildId).map(id -> "<#" + id + ">").orElse("non défini");
                String welcomeChan = settingsManager.getWelcomeChannelId(guildId).map(id -> "<#" + id + ">").orElse("non défini");
                
                embed.addField("Salons", "**Logs :** " + logChan + "\n**Bienvenue :** " + welcomeChan, false);
                event.replyEmbeds(embed.build()).queue();
            }
            case "antispam" -> {
                boolean val = event.getOption("etat", true, OptionMapping::getAsBoolean);
                settingsManager.setAntiSpamEnabled(guildId, val);
                event.reply("✅ Anti-spam " + (val ? "activé" : "désactivé") + ".").setEphemeral(true).queue();
            }
            case "antilink" -> {
                boolean val = event.getOption("etat", true, OptionMapping::getAsBoolean);
                settingsManager.setAntiLinkEnabled(guildId, val);
                event.reply("✅ Anti-liens " + (val ? "activé" : "désactivé") + ".").setEphemeral(true).queue();
            }
            case "allowgifs" -> {
                boolean val = event.getOption("etat", true, OptionMapping::getAsBoolean);
                settingsManager.setGifLinksAllowed(guildId, val);
                event.reply("✅ Liens GIF " + (val ? "autorisés" : "bloqués") + ".").setEphemeral(true).queue();
            }
            case "levels" -> {
                boolean val = event.getOption("etat", true, OptionMapping::getAsBoolean);
                settingsManager.setLevelsEnabled(guildId, val);
                event.reply("✅ Système de niveaux " + (val ? "activé" : "désactivé") + ".").setEphemeral(true).queue();
            }
            case "spamlimit" -> {
                int val = event.getOption("limite", 5, OptionMapping::getAsInt);
                settingsManager.setSpamLimit(guildId, val);
                event.reply("✅ Limite de spam fixée à " + val + " messages.").setEphemeral(true).queue();
            }
            case "spamwindow" -> {
                int val = event.getOption("secondes", 5, OptionMapping::getAsInt);
                settingsManager.setSpamWindowMs(guildId, val * 1000L);
                event.reply("✅ Fenêtre de spam fixée à " + val + " secondes.").setEphemeral(true).queue();
            }
            case "xpcooldown" -> {
                int val = event.getOption("secondes", 60, OptionMapping::getAsInt);
                settingsManager.setXpCooldownMs(guildId, val * 1000L);
                event.reply("✅ Cooldown XP fixé à " + val + " secondes.").setEphemeral(true).queue();
            }
            case "xpminlen" -> {
                int val = event.getOption("taille", 5, OptionMapping::getAsInt);
                settingsManager.setXpMinMessageLength(guildId, val);
                event.reply("✅ Longueur min message fixée à " + val + ".").setEphemeral(true).queue();
            }
            case "xpminan" -> {
                int val = event.getOption("nombre", 3, OptionMapping::getAsInt);
                settingsManager.setXpMinAlnumCount(guildId, val);
                event.reply("✅ Caractères alphanum min fixés à " + val + ".").setEphemeral(true).queue();
            }
            case "xpgain" -> {
                int min = event.getOption("min", 15, OptionMapping::getAsInt);
                int max = event.getOption("max", 25, OptionMapping::getAsInt);
                settingsManager.setXpGainRange(guildId, min, max);
                event.reply("✅ Plage de gain XP : " + min + " à " + max + ".").setEphemeral(true).queue();
            }
            case "welcomechannel" -> {
                var channel = event.getOption("salon", OptionMapping::getAsChannel);
                settingsManager.setWelcomeChannelId(guildId, channel.getId());
                event.reply("✅ Salon de bienvenue défini sur " + channel.getAsMention() + ".").setEphemeral(true).queue();
            }
            case "welcomeimage" -> {
                String url = event.getOption("url", "default", OptionMapping::getAsString);
                settingsManager.setWelcomeImageUrl(guildId, url.equals("default") ? "" : url);
                event.reply("✅ Image de bienvenue mise à jour.").setEphemeral(true).queue();
            }
            case "logchannel" -> {
                var channel = event.getOption("salon", OptionMapping::getAsChannel);
                settingsManager.setLogChannelId(guildId, channel.getId());
                event.reply("✅ Salon de logs défini sur " + channel.getAsMention() + ".").setEphemeral(true).queue();
            }
        }
    }
}
