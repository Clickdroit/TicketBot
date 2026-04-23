package fr.sakura.bot.commands.ticket;



import fr.sakura.bot.commands.ICommand;

import fr.sakura.bot.core.util.EmbedStyle;
import fr.sakura.bot.core.service.TicketService;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;
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
    public String getCategory() {
        return "Tickets";
    }

    @Override
    public SlashCommandData getCommandData() {
        return Commands.slash(getName(), "Affiche le panel de tickets")
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MANAGE_CHANNEL));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (event.getGuild() == null) {
            event.reply("ÃƒÆ’Ã‚Â¢Ãƒâ€šÃ‚ÂÃƒâ€¦Ã¢â‚¬â„¢ Commande utilisable uniquement sur un serveur.").setEphemeral(true).queue();
            return;
        }

        EmbedBuilder embed = EmbedStyle.newActionEmbed("ÃƒÆ’Ã‚Â°Ãƒâ€¦Ã‚Â¸ÃƒÂ¢Ã¢â€šÂ¬Ã…â€œÃƒâ€šÃ‚Â©", "SystÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¨me de Support");
        embed.setAuthor("Support ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€šÃ‚Â¢ " + event.getGuild().getName(), null, event.getGuild().getIconUrl());
        
        embed.setDescription("Besoin d'aide ? CrÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â©ez un ticket et notre ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â©quipe vous assistera dans les plus brefs dÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â©lais.\n\n" + 
                EmbedStyle.sectionHeader("ÃƒÆ’Ã‚Â°Ãƒâ€¦Ã‚Â¸Ãƒâ€šÃ‚Â¤ÃƒÂ¢Ã¢â€šÂ¬Ã‚Â", "Comment ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â§a marche ?"));
        
        embed.addField("1ÃƒÆ’Ã‚Â¯Ãƒâ€šÃ‚Â¸Ãƒâ€šÃ‚ÂÃƒÆ’Ã‚Â¢Ãƒâ€ Ã¢â‚¬â„¢Ãƒâ€šÃ‚Â£ ÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã‚Â°tape 1", "SÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â©lectionnez une catÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â©gorie ci-dessous", false);
        embed.addField("2ÃƒÆ’Ã‚Â¯Ãƒâ€šÃ‚Â¸Ãƒâ€šÃ‚ÂÃƒÆ’Ã‚Â¢Ãƒâ€ Ã¢â‚¬â„¢Ãƒâ€šÃ‚Â£ ÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã‚Â°tape 2", "Un salon privÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â© sera crÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â©ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â© pour vous", false);
        embed.addField("3ÃƒÆ’Ã‚Â¯Ãƒâ€šÃ‚Â¸Ãƒâ€šÃ‚ÂÃƒÆ’Ã‚Â¢Ãƒâ€ Ã¢â‚¬â„¢Ãƒâ€šÃ‚Â£ ÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã‚Â°tape 3", "Expliquez votre demande en dÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â©tail", false);
        embed.addField("4ÃƒÆ’Ã‚Â¯Ãƒâ€šÃ‚Â¸Ãƒâ€šÃ‚ÂÃƒÆ’Ã‚Â¢Ãƒâ€ Ã¢â‚¬â„¢Ãƒâ€šÃ‚Â£ ÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã‚Â°tape 4", "Attendez qu'un membre du staff vous rÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â©ponde", false);
        embed.addField("ÃƒÆ’Ã‚Â¢Ãƒâ€¦Ã‚Â¡Ãƒâ€šÃ‚Â ÃƒÆ’Ã‚Â¯Ãƒâ€šÃ‚Â¸Ãƒâ€šÃ‚Â Important", "N'ouvrez qu'un seul ticket ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â  la fois.", false);

        if (event.getJDA().getSelfUser().getEffectiveAvatarUrl() != null) {
            embed.setThumbnail(event.getJDA().getSelfUser().getEffectiveAvatarUrl());
        }
        EmbedStyle.setFooter(embed, "Support disponible 24/7", event.getGuild().getIconUrl());

        StringSelectMenu menu = StringSelectMenu.create("ticket:category")
                .setPlaceholder("ÃƒÆ’Ã‚Â°Ãƒâ€¦Ã‚Â¸ÃƒÂ¢Ã¢â€šÂ¬Ã…â€œÃƒâ€šÃ‚Â© Choisissez une catÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â©gorie")
                .addOption("Partenariat", "ticket:partnership", "Proposer un partenariat avec le serveur", Emoji.fromUnicode("ÃƒÆ’Ã‚Â°Ãƒâ€¦Ã‚Â¸Ãƒâ€šÃ‚Â¤Ãƒâ€šÃ‚Â"))
                .addOption("Signalement", "ticket:report", "Signaler un utilisateur ou un comportement", Emoji.fromUnicode("ÃƒÆ’Ã‚Â°Ãƒâ€¦Ã‚Â¸Ãƒâ€¦Ã‚Â¡Ãƒâ€šÃ‚Â¨"))
                .addOption("Support", "ticket:support", "Aide technique ou questions gÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â©nÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â©rales", Emoji.fromUnicode("ÃƒÆ’Ã‚Â°Ãƒâ€¦Ã‚Â¸ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂºÃƒâ€šÃ‚Â ÃƒÆ’Ã‚Â¯Ãƒâ€šÃ‚Â¸Ãƒâ€šÃ‚Â"))
                .addOption("Suggestion", "ticket:suggestion", "Proposer une idÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â©e pour le serveur", Emoji.fromUnicode("ÃƒÆ’Ã‚Â°Ãƒâ€¦Ã‚Â¸ÃƒÂ¢Ã¢â€šÂ¬Ã¢â€žÂ¢Ãƒâ€šÃ‚Â¡"))
                .addOption("Autre", "ticket:other", "Toute autre demande", Emoji.fromUnicode("ÃƒÆ’Ã‚Â¢Ãƒâ€šÃ‚ÂÃƒÂ¢Ã¢â€šÂ¬Ã…â€œ"))
                .build();

        event.getChannel().sendMessageEmbeds(embed.build())
                .addActionRow(menu)
                .queue(
                        success -> {
                            event.reply("ÃƒÆ’Ã‚Â¢Ãƒâ€¦Ã¢â‚¬Å“ÃƒÂ¢Ã¢â€šÂ¬Ã‚Â¦ Panel de tickets envoyÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â©.").setEphemeral(true).queue();
                            logger.info("/ticketpanel envoye guildId={} userId={}", event.getGuild().getId(), event.getUser().getId());
                        },
                        error -> {
                            logger.error("Echec envoi panel tickets guildId={}", event.getGuild().getId(), error);
                            event.reply("ÃƒÆ’Ã‚Â¢Ãƒâ€šÃ‚ÂÃƒâ€¦Ã¢â‚¬â„¢ Impossible d'envoyer le panel de tickets.").setEphemeral(true).queue();
                        }
                );
    }
}
