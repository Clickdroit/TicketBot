package fr.sakura.bot.commands.ticket;

import fr.sakura.bot.commands.ICommand;
import fr.sakura.bot.core.util.EmbedStyle;
import fr.sakura.bot.database.SettingsManager;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Commande d'affichage du panneau de configuration de TicketBot.
 * Affiche l'état actuel et propose des boutons interactifs pour modifier les options.
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
        return Commands.slash(getName(), "Affiche le panneau de configuration interactif de TicketBot")
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MANAGE_SERVER));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (event.getGuild() == null) {
            event.reply("❌ Commande utilisable uniquement sur un serveur.").setEphemeral(true).queue();
            return;
        }

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
        
        embed.setDescription("Bienvenue dans le panneau de configuration interactif de votre système de support.\n" +
                "Cliquez sur les boutons ci-dessous pour modifier les salons de logs, de transcriptions ou configurer le rôle de support à ping.\n\n" +
                EmbedStyle.sectionHeader("🔧", "Paramètres des salons") + "\n" +
                EmbedStyle.detailLine("Salon de Logs", logsChannelMention) + "\n" +
                EmbedStyle.detailLine("Salon Transcriptions", transcriptsChannelMention) + "\n\n" +
                EmbedStyle.sectionHeader("👥", "Rôles de support") + "\n" +
                EmbedStyle.detailLine("Rôle à ping", supportRoleMention) + "\n\n" +
                "💡 *Les boutons ci-dessous afficheront des menus de sélection privés et sécurisés.*");

        EmbedStyle.setFooter(embed, "TicketBot Administration", event.getGuild().getIconUrl());

        // Création des boutons interactifs de JDA 5
        Button btnLogs = Button.secondary("config:logs", "📝 Salon de Logs");
        Button btnTranscripts = Button.secondary("config:transcripts", "📂 Salon Transcriptions");
        Button btnSupportRole = Button.secondary("config:support-role", "👥 Rôle Support");

        event.replyEmbeds(embed.build())
                .setComponents(ActionRow.of(btnLogs, btnTranscripts, btnSupportRole))
                .queue(
                        success -> logger.info("Panneau ticketconfig affiché guildId={} par userId={}", guildId, event.getUser().getId()),
                        error -> logger.error("Échec affichage panneau ticketconfig guildId={}", guildId, error)
                );
    }
}
