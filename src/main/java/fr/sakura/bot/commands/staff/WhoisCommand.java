package fr.sakura.bot.commands.staff;

import fr.sakura.bot.commands.ICommand;
import fr.sakura.bot.core.model.LevelProfile;
import fr.sakura.bot.core.model.TicketEntry;
import fr.sakura.bot.core.service.LevelService;
import fr.sakura.bot.core.service.TicketService;
import fr.sakura.bot.core.service.WarningService;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

import java.awt.Color;
import java.time.Instant;
import java.util.stream.Collectors;

public class WhoisCommand implements ICommand {

    private final LevelService levelService;
    private final WarningService warningService;
    private final TicketService ticketService;

    public WhoisCommand(LevelService levelService, WarningService warningService, TicketService ticketService) {
        this.levelService = levelService;
        this.warningService = warningService;
        this.ticketService = ticketService;
    }

    @Override
    public String getName() {
        return "whois";
    }

    @Override
    public String getCategory() {
        return "Staff";
    }

    @Override
    public SlashCommandData getCommandData() {
        return Commands.slash(getName(), "Vue d'ensemble complète d'un membre (Infos + Modération + XP)")
                .addOptions(new OptionData(OptionType.USER, "membre", "Le membre à inspecter", true))
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MODERATE_MEMBERS));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (event.getGuild() == null) return;

        Member target = event.getOption("membre").getAsMember();
        if (target == null) {
            event.reply("❌ Utilisateur introuvable sur ce serveur.").setEphemeral(true).queue();
            return;
        }

        String guildId = event.getGuild().getId();
        String userId = target.getId();

        // 1. Informations de base
        String roles = target.getRoles().stream()
                .map(Role::getAsMention)
                .collect(Collectors.joining(", "));
        if (roles.isEmpty()) roles = "Aucun rôle";

        EmbedBuilder eb = new EmbedBuilder()
                .setTitle("🔍 Dossier Membre : " + target.getUser().getName())
                .setThumbnail(target.getUser().getEffectiveAvatarUrl())
                .setColor(Color.MAGENTA)
                .setTimestamp(Instant.now());

        eb.addField("👤 Identité", 
                "> **Surnom :** " + (target.getNickname() != null ? target.getNickname() : "Aucun") + "\n" +
                "> **ID :** " + userId + "\n" +
                "> **Bot :** " + (target.getUser().isBot() ? "Oui" : "Non"), true);

        eb.addField("📅 Dates", 
                "> **Création :** <t:" + target.getUser().getTimeCreated().toEpochSecond() + ":R>\n" +
                "> **Arrivée :** <t:" + target.getTimeJoined().toEpochSecond() + ":R>", true);

        // 2. Modération
        int warnCount = warningService.getWarningsCount(guildId, userId);
        eb.addField("⚖️ Modération", 
                "> **Avertissements :** " + warnCount, true);

        // 3. Expérience
        LevelProfile profile = levelService.getProfile(guildId, userId);
        eb.addField("✨ Expérience", 
                "> **Niveau :** " + profile.level() + "\n" +
                "> **XP :** " + profile.xp(), true);

        // 4. Tickets
        TicketEntry activeTicket = ticketService.findOpenTicket(guildId, userId);
        eb.addField("🎫 Support", 
                "> **Ticket actif :** " + (activeTicket != null ? "<#" + activeTicket.channelId() + ">" : "Aucun"), true);

        eb.addField("🎭 Rôles (" + target.getRoles().size() + ")", roles, false);

        eb.setFooter("Consulté par " + event.getUser().getName());

        event.replyEmbeds(eb.build()).queue();
    }
}
