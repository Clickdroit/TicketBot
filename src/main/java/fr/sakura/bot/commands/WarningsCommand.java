package fr.sakura.bot.commands;

import fr.sakura.bot.utils.WarningEntry;
import fr.sakura.bot.utils.WarningService;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class WarningsCommand implements ICommand {

    private static final Logger logger = LoggerFactory.getLogger(WarningsCommand.class);
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy à HH:mm");

    private final WarningService warningService;

    public WarningsCommand(WarningService warningService) {
        this.warningService = warningService;
    }

    @Override
    public String getName() {
        return "warnings";
    }

    @Override
    public SlashCommandData getCommandData() {
        return Commands.slash(getName(), "Affiche les avertissements d'un membre")
                .addOptions(new OptionData(OptionType.USER, "membre", "Le membre cible", true))
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MODERATE_MEMBERS));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        logger.debug("Execution /warnings par userId={}", event.getUser().getId());
        Member target = event.getOption("membre", OptionMapping::getAsMember);

        if (event.getGuild() == null) {
            logger.warn("/warnings appelee hors serveur userId={}", event.getUser().getId());
            event.reply("❌ Cette commande doit etre utilisee dans un serveur.").setEphemeral(true).queue();
            return;
        }

        if (target == null) {
            logger.warn("/warnings cible introuvable modId={}", event.getUser().getId());
            event.reply("❌ Membre introuvable.").setEphemeral(true).queue();
            return;
        }

        logger.info("/warnings demande: modId={}, targetId={}", event.getUser().getId(), target.getId());

        List<WarningEntry> warnings = warningService.getWarnings(event.getGuild().getId(), target.getId());

        if (warnings.isEmpty()) {
            logger.info("/warnings aucun resultat pour targetId={}", target.getId());
            event.reply("ℹ️ Aucun avertissement pour **" + target.getUser().getName() + "**.")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        int total = warnings.size();
        int displayed = Math.min(10, total);

        StringBuilder message = new StringBuilder();
        message.append("📋 Warnings de **")
                .append(target.getUser().getName())
                .append("** (total: ")
                .append(total)
                .append(")\n\n");

        for (int i = 0; i < displayed; i++) {
            WarningEntry warning = warnings.get(i);

            // Formatage du timestamp ISO en date lisible, avec fallback si invalide
            String formattedDate;
            try {
                formattedDate = OffsetDateTime.parse(warning.timestamp()).format(TIMESTAMP_FORMATTER);
            } catch (Exception e) {
                formattedDate = warning.timestamp();
            }

            String moderatorDisplay = formatModerator(event, warning.moderatorId());

            message.append("**").append(i + 1).append(".** ")
                    .append(warning.reason())
                    .append("\n")
                    .append("   › Mod : ").append(moderatorDisplay)
                    .append(" • ").append(formattedDate)
                    .append("\n\n");
        }

        if (total > displayed) {
            message.append("*… et ").append(total - displayed).append(" warning(s) supplémentaire(s).*");
        }

        event.reply(message.toString()).setEphemeral(true).queue();
        logger.info("/warnings reussi: targetId={}, total={}", target.getId(), total);
    }

    private String formatModerator(SlashCommandInteractionEvent event, String moderatorId) {
        if (moderatorId == null || moderatorId.isBlank()) {
            return "Inconnu";
        }

        if (event.getGuild() != null) {
            Member moderatorMember = event.getGuild().getMemberById(moderatorId);
            if (moderatorMember != null) {
                return moderatorMember.getAsMention() + " (" + moderatorMember.getEffectiveName() + ")";
            }
        }

        return "<@" + moderatorId + ">";
    }
}