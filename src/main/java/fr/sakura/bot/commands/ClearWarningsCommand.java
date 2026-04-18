package fr.sakura.bot.commands;

import fr.sakura.bot.utils.ModerationLogger;
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

public class ClearWarningsCommand implements ICommand {

    private static final Logger logger = LoggerFactory.getLogger(ClearWarningsCommand.class);

    private final ModerationLogger moderationLogger;
    private final WarningService warningService;

    public ClearWarningsCommand(ModerationLogger moderationLogger, WarningService warningService) {
        this.moderationLogger = moderationLogger;
        this.warningService = warningService;
    }

    @Override
    public String getName() {
        return "clearwarnings";
    }

    @Override
    public SlashCommandData getCommandData() {
        return Commands.slash(getName(), "Supprime tous les avertissements d'un membre")
                .addOptions(new OptionData(OptionType.USER, "membre", "Le membre cible", true))
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MODERATE_MEMBERS));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        logger.debug("Execution /clearwarnings par userId={}", event.getUser().getId());
        Member target = event.getOption("membre", OptionMapping::getAsMember);

        if (event.getGuild() == null || event.getMember() == null) {
            logger.warn("/clearwarnings appelee hors serveur ou member null userId={}", event.getUser().getId());
            event.reply("❌ Cette commande doit etre utilisee dans un serveur.").setEphemeral(true).queue();
            return;
        }

        if (target == null) {
            logger.warn("/clearwarnings cible introuvable modId={}", event.getUser().getId());
            event.reply("❌ Membre introuvable.").setEphemeral(true).queue();
            return;
        }

        logger.info("/clearwarnings demande: modId={}, targetId={}", event.getUser().getId(), target.getId());

        try {
            int removed = warningService.clearWarnings(event.getGuild().getId(), target.getId());
            if (removed == 0) {
                logger.info("/clearwarnings aucun warning a supprimer targetId={}", target.getId());
                event.reply("ℹ️ Aucun warning a supprimer pour **" + target.getUser().getName() + "**.")
                        .setEphemeral(true)
                        .queue();
                return;
            }

            event.reply("✅ " + removed + " warning(s) supprime(s) pour **" + target.getUser().getName() + "**.")
                    .queue();
            logger.info("/clearwarnings reussi: targetId={}, removed={}", target.getId(), removed);

            moderationLogger.logInGuild(
                    event.getGuild(),
                    "CLEARWARN",
                    event.getMember(),
                    target,
                    "Reset des warnings",
                    removed + " warning(s) retire(s)"
            );
        } catch (IOException ex) {
            logger.error("/clearwarnings echec JSON: modId={}, targetId={}", event.getUser().getId(), target.getId(), ex);
            event.reply("❌ Impossible de supprimer les warnings (erreur JSON).")
                    .setEphemeral(true)
                    .queue();
        }
    }
}

