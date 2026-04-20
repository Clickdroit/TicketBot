package fr.sakura.bot.commands;

import fr.sakura.bot.utils.EmbedStyle;
import fr.sakura.bot.utils.TicketService;
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

        EmbedBuilder embed = EmbedStyle.newActionEmbed("📩", "Système de Support");
        embed.setAuthor("Support • " + event.getGuild().getName(), null, event.getGuild().getIconUrl());
        
        embed.setDescription("Besoin d'aide ? Créez un ticket et notre équipe vous assistera dans les plus brefs délais.\n\n" + 
                EmbedStyle.sectionHeader("🤔", "Comment ça marche ?"));
        
        embed.addField("1️⃣ Étape 1", "Sélectionnez une catégorie ci-dessous", false);
        embed.addField("2️⃣ Étape 2", "Un salon privé sera créé pour vous", false);
        embed.addField("3️⃣ Étape 3", "Expliquez votre demande en détail", false);
        embed.addField("4️⃣ Étape 4", "Attendez qu'un membre du staff vous réponde", false);
        embed.addField("⚠️ Important", "N'ouvrez qu'un seul ticket à la fois.", false);

        if (event.getJDA().getSelfUser().getEffectiveAvatarUrl() != null) {
            embed.setThumbnail(event.getJDA().getSelfUser().getEffectiveAvatarUrl());
        }
        EmbedStyle.setFooter(embed, "Support disponible 24/7", event.getGuild().getIconUrl());

        StringSelectMenu menu = StringSelectMenu.create("ticket:category")
                .setPlaceholder("📩 Choisissez une catégorie")
                .addOption("Partenariat", "ticket:partnership", "Proposer un partenariat avec le serveur", Emoji.fromUnicode("🤝"))
                .addOption("Signalement", "ticket:report", "Signaler un utilisateur ou un comportement", Emoji.fromUnicode("🚨"))
                .addOption("Support", "ticket:support", "Aide technique ou questions générales", Emoji.fromUnicode("🛠️"))
                .addOption("Suggestion", "ticket:suggestion", "Proposer une idée pour le serveur", Emoji.fromUnicode("💡"))
                .addOption("Autre", "ticket:other", "Toute autre demande", Emoji.fromUnicode("❓"))
                .build();

        event.getChannel().sendMessageEmbeds(embed.build())
                .addActionRow(menu)
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
