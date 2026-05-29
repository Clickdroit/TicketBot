package fr.sakura.bot.commands.ticket;

import fr.sakura.bot.commands.ICommand;
import fr.sakura.bot.core.util.EmbedStyle;
import fr.sakura.bot.database.SettingsManager;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
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

/**
 * Commande de configuration du système de tickets (/ticketconfig).
 * Permet de définir interactivement le salon de logs, le salon de transcription et le rôle de support.
 */
public class TicketConfigCommand implements ICommand {

    private static final Logger logger = LoggerFactory.getLogger(TicketConfigCommand.class);
    private final SettingsManager settingsManager;

    public TicketConfigCommand(SettingsManager settingsManager) {
        this.settingsManager = settingsManager;
    }

    @Override
    public String getName() {
        return "ticketconfig";
    }

    @Override
    public String getCategory() {
        return "Tickets";
    }

    @Override
    public SlashCommandData getCommandData() {
        return Commands.slash(getName(), "Configure les options du système de tickets")
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MANAGE_SERVER))
                .addSubcommands(
                        new SubcommandData("logs", "Définit le salon des logs d'activité de tickets")
                                .addOptions(new OptionData(OptionType.CHANNEL, "salon", "Le salon textuel de logs", true)
                                        .setChannelTypes(ChannelType.TEXT)),
                        new SubcommandData("transcripts", "Définit le salon où seront envoyées les transcriptions de tickets clos")
                                .addOptions(new OptionData(OptionType.CHANNEL, "salon", "Le salon textuel de transcription", true)
                                        .setChannelTypes(ChannelType.TEXT)),
                        new SubcommandData("support-role", "Définit le rôle du staff de support à ping (et à autoriser sur les tickets)")
                                .addOptions(new OptionData(OptionType.ROLE, "role", "Le rôle de support", true)),
                        new SubcommandData("view", "Affiche la configuration actuelle de TicketBot")
                );
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (event.getGuild() == null) {
            event.reply("❌ Commande utilisable uniquement sur un serveur.").setEphemeral(true).queue();
            return;
        }

        String subcommand = event.getSubcommandName();
        if (subcommand == null) return;

        switch (subcommand) {
            case "logs" -> handleLogs(event);
            case "transcripts" -> handleTranscripts(event);
            case "support-role" -> handleSupportRole(event);
            case "view" -> handleView(event);
        }
    }

    private void handleLogs(SlashCommandInteractionEvent event) {
        TextChannel channel = event.getOption("salon", OptionMapping::getAsChannel).asTextChannel();
        String guildId = event.getGuild().getId();

        settingsManager.setLogChannelId(guildId, channel.getId());

        EmbedBuilder embed = EmbedStyle.newActionEmbed("⚙️", "Configuration des Logs");
        embed.setDescription("Le salon des logs de tickets a été configuré avec succès !\n\n" +
                EmbedStyle.detailLine("Salon de Logs", channel.getAsMention()) + "\n" +
                EmbedStyle.detailLine("Configuré par", event.getUser().getAsMention()));
        
        event.replyEmbeds(embed.build()).queue(
                success -> logger.info("Salon logs ticket configuré guildId={} par userId={}", guildId, event.getUser().getId()),
                error -> event.reply("❌ Impossible de configurer le salon.").setEphemeral(true).queue()
        );
    }

    private void handleTranscripts(SlashCommandInteractionEvent event) {
        TextChannel channel = event.getOption("salon", OptionMapping::getAsChannel).asTextChannel();
        String guildId = event.getGuild().getId();

        settingsManager.setTranscriptChannelId(guildId, channel.getId());

        EmbedBuilder embed = EmbedStyle.newActionEmbed("⚙️", "Configuration des Transcriptions");
        embed.setDescription("Le salon de transcription des tickets clos a été configuré avec succès !\n\n" +
                EmbedStyle.detailLine("Salon Transcriptions", channel.getAsMention()) + "\n" +
                EmbedStyle.detailLine("Configuré par", event.getUser().getAsMention()));

        event.replyEmbeds(embed.build()).queue(
                success -> logger.info("Salon transcription ticket configuré guildId={} par userId={}", guildId, event.getUser().getId()),
                error -> event.reply("❌ Impossible de configurer le salon.").setEphemeral(true).queue()
        );
    }

    private void handleSupportRole(SlashCommandInteractionEvent event) {
        Role role = event.getOption("role", OptionMapping::getAsRole);
        String guildId = event.getGuild().getId();

        settingsManager.setSupportRoleId(guildId, role.getId());

        EmbedBuilder embed = EmbedStyle.newActionEmbed("⚙️", "Configuration du Rôle Support");
        embed.setDescription("Le rôle de support à ping a été configuré avec succès !\n\n" +
                EmbedStyle.detailLine("Rôle Support", role.getAsMention()) + "\n" +
                EmbedStyle.detailLine("Configuré par", event.getUser().getAsMention()));

        event.replyEmbeds(embed.build()).queue(
                success -> logger.info("Rôle support ticket configuré guildId={} par userId={}", guildId, event.getUser().getId()),
                error -> event.reply("❌ Impossible de configurer le rôle.").setEphemeral(true).queue()
        );
    }

    private void handleView(SlashCommandInteractionEvent event) {
        String guildId = event.getGuild().getId();

        String logsChannelMention = settingsManager.getLogChannelId(guildId)
                .map(id -> "<#" + id + ">")
                .orElse("❌ *Non configuré*");

        String transcriptsChannelMention = settingsManager.getTranscriptChannelId(guildId)
                .map(id -> "<#" + id + ">")
                .orElse("❌ *Non configuré*");

        String supportRoleMention = settingsManager.getSupportRoleId(guildId)
                .map(id -> "<@&" + id + ">")
                .orElse("⚠️ *Détection automatique (fallback)*");

        EmbedBuilder embed = EmbedStyle.newInfoEmbed("⚙️", "Configuration de TicketBot");
        embed.setAuthor("Panneau d'Administration • " + event.getGuild().getName(), null, event.getGuild().getIconUrl());
        
        embed.setDescription("Voici les paramètres actuels du système de support sur ce serveur :\n\n" +
                EmbedStyle.sectionHeader("🔧", "Paramètres des salons") + "\n" +
                EmbedStyle.detailLine("Salon de Logs", logsChannelMention) + "\n" +
                EmbedStyle.detailLine("Salon Transcriptions", transcriptsChannelMention) + "\n\n" +
                EmbedStyle.sectionHeader("👥", "Rôles de support") + "\n" +
                EmbedStyle.detailLine("Rôle à ping", supportRoleMention) + "\n\n" +
                "💡 *Utilisez `/ticketconfig <logs | transcripts | support-role>` pour modifier ces paramètres.*");

        EmbedStyle.setFooter(embed, "TicketBot Administration", event.getGuild().getIconUrl());

        event.replyEmbeds(embed.build()).queue();
    }
}
