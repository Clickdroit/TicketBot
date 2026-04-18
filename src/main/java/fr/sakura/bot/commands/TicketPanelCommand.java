package fr.sakura.bot.commands;

import fr.sakura.bot.utils.EmbedStyle;
import fr.sakura.bot.utils.TicketService;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TicketPanelCommand implements ICommand {

    private static final Logger logger = LoggerFactory.getLogger(TicketPanelCommand.class);
    private final TicketService ticketService;

    public TicketPanelCommand(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @Override
    public String getName() {
        return "ticketpanel";
    }

    @Override
    public SlashCommandData getCommandData() {
        return Commands.slash(getName(), "Affiche le panel de tickets")
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MANAGE_CHANNEL));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (event.getGuild() == null) {
            event.reply("❌ Commande utilisable uniquement sur un serveur.").setEphemeral(true).queue();
            return;
        }

        EmbedBuilder embed = EmbedStyle.newInfoEmbed("🎫", "Support / Tickets");
        embed.setDescription("Clique sur le bouton ci-dessous pour ouvrir un ticket privé avec l'équipe de support.");
        embed.addField("Disponibilité", "Un seul ticket actif par membre.", true);
        embed.addField("Confidentialité", "Salon privé visible uniquement par toi et le staff.", true);
        if (event.getJDA().getSelfUser().getEffectiveAvatarUrl() != null) {
            embed.setThumbnail(event.getJDA().getSelfUser().getEffectiveAvatarUrl());
        }
        EmbedStyle.setFooter(embed, "Panel de tickets");

        event.getChannel().sendMessageEmbeds(embed.build())
                .setActionRow(ticketService.createButton())
                .queue(
                        success -> {
                            event.reply("✅ Panel de tickets envoyé.").setEphemeral(true).queue();
                            logger.info("/ticketpanel envoye guildId={} userId={}", event.getGuild().getId(), event.getUser().getId());
                        },
                        error -> {
                            logger.error("Echec envoi panel tickets guildId={}", event.getGuild().getId(), error);
                            event.reply("❌ Impossible d'envoyer le panel de tickets.").setEphemeral(true).queue();
                        }
                );
    }
}
