package fr.sakura.bot.commands.info;

import fr.sakura.bot.commands.ICommand;
import fr.sakura.bot.core.model.LevelProfile;
import fr.sakura.bot.core.model.TicketEntry;
import fr.sakura.bot.core.service.LevelService;
import fr.sakura.bot.core.service.TicketService;
import fr.sakura.bot.core.service.WarningService;
import fr.sakura.bot.core.util.EmbedStyle;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.time.Instant;
import java.util.stream.Collectors;

public class UserInfoCommand implements ICommand {

    private static final Logger logger = LoggerFactory.getLogger(UserInfoCommand.class);
    private final LevelService levelService;
    private final WarningService warningService;
    private final TicketService ticketService;

    public UserInfoCommand(LevelService levelService, WarningService warningService, TicketService ticketService) {
        this.levelService = levelService;
        this.warningService = warningService;
        this.ticketService = ticketService;
    }

    @Override
    public String getName() {
        return "userinfo";
    }

    @Override
    public SlashCommandData getCommandData() {
        return Commands.slash(getName(), "Affiche les informations détaillées d'un membre")
                .addOptions(new OptionData(OptionType.USER, "membre", "Le membre à inspecter", false));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (event.getGuild() == null) return;

        Member target = event.getOption("membre") != null ? event.getOption("membre").getAsMember() : event.getMember();
        if (target == null) {
            event.reply("❌ Utilisateur introuvable.").setEphemeral(true).queue();
            return;
        }

        boolean isStaff = event.getMember().hasPermission(Permission.MODERATE_MEMBERS);
        EmbedBuilder eb = EmbedStyle.newInfoEmbed("👤", "Informations : " + target.getUser().getName());
        eb.setThumbnail(target.getUser().getEffectiveAvatarUrl());

        // Infos publiques
        eb.addField("📅 Dates", 
                "> **Création :** <t:" + target.getUser().getTimeCreated().toEpochSecond() + ":R>\n" +
                "> **Arrivée :** <t:" + target.getTimeJoined().toEpochSecond() + ":R>", true);

        String roles = target.getRoles().stream().map(Role::getAsMention).collect(Collectors.joining(", "));
        eb.addField("🎭 Rôles (" + target.getRoles().size() + ")", roles.isEmpty() ? "Aucun" : EmbedStyle.truncate(roles, 1024), false);

        // Infos Staff / XP (Conditionnel)
        if (isStaff) {
            eb.setColor(Color.MAGENTA);
            int warns = warningService.getWarningsCount(event.getGuild().getId(), target.getId());
            LevelProfile profile = levelService.getProfile(event.getGuild().getId(), target.getId());
            TicketEntry ticket = ticketService.findOpenTicket(event.getGuild().getId(), target.getId());

            eb.addField("⚖️ Modération", "> **Avertissements :** " + warns, true);
            eb.addField("✨ Expérience", "> **Niveau :** " + profile.level() + " (" + profile.xp() + " XP)", true);
            eb.addField("🎫 Support", "> **Ticket :** " + (ticket != null ? "<#" + ticket.channelId() + ">" : "Aucun"), true);
        }

        EmbedStyle.setInfoFooterWithId(eb, target.getId());
        event.replyEmbeds(eb.build()).queue();
    }
}

