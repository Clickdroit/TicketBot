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

import java.io.IOException;
import java.util.List;

public class WarningsCommand implements ICommand {

    private static final Logger logger = LoggerFactory.getLogger(WarningsCommand.class);

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

        try {
            List<WarningEntry> warnings = warningService.getWarnings(event.getGuild().getId(), target.getId());
            if (warnings.isEmpty()) {
                logger.info("/warnings aucun resultat pour targetId={}", target.getId());
                event.reply("ℹ️ Aucun avertissement pour **" + target.getUser().getName() + "**.")
                        .setEphemeral(true)
                        .queue();
                return;
            }

            StringBuilder message = new StringBuilder();
            message.append("📋 Warnings de **").append(target.getUser().getName()).append("** (total: ")
                    .append(warnings.size()).append(")\n");

            int max = Math.min(10, warnings.size());
            for (int i = 0; i < max; i++) {
                WarningEntry warning = warnings.get(i);
                message.append(i + 1)
                        .append(". ")
                        .append(warning.getReason())
                        .append(" | mod: ")
                        .append(warning.getModeratorId())
                        .append(" | ")
                        .append(warning.getTimestamp())
                        .append("\n");
            }

            if (warnings.size() > max) {
                message.append("... et ").append(warnings.size() - max).append(" warning(s) supplementaire(s).\n");
            }

            event.reply(message.toString()).setEphemeral(true).queue();
            logger.info("/warnings reussi: targetId={}, total={}", target.getId(), warnings.size());
        } catch (IOException ex) {
            logger.error("/warnings echec JSON: modId={}, targetId={}", event.getUser().getId(), target.getId(), ex);
            event.reply("❌ Impossible de lire les warnings (erreur JSON).")
                    .setEphemeral(true)
                    .queue();
        }
    }
}

