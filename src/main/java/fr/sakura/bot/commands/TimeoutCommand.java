package fr.sakura.bot.commands;

import fr.sakura.bot.listeners.log.ModerationLogListener;
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

import java.time.Duration;

public class TimeoutCommand implements ICommand {

    private static final Logger logger = LoggerFactory.getLogger(TimeoutCommand.class);

    private final ModerationLogListener moderationLogListener;

    public TimeoutCommand(ModerationLogListener moderationLogListener) {
        this.moderationLogListener = moderationLogListener;
    }

    @Override
    public String getName() {
        return "timeout";
    }

    @Override
    public SlashCommandData getCommandData() {
        return Commands.slash(getName(), "Mute temporairement un membre")
                .addOptions(
                        new OptionData(OptionType.USER, "membre", "Le membre a timeout", true),
                        new OptionData(OptionType.INTEGER, "minutes", "Duree du timeout en minutes (1-10080)", true)
                                .setMinValue(1)
                                .setMaxValue(10080),
                        new OptionData(OptionType.STRING, "raison", "La raison du timeout", false)
                )
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MODERATE_MEMBERS));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        logger.debug("Execution /timeout par userId={}", event.getUser().getId());
        Member target = event.getOption("membre", OptionMapping::getAsMember);
        Integer minutes = event.getOption("minutes", OptionMapping::getAsInt);
        String reason = event.getOption("raison", OptionMapping::getAsString);

        if (target == null || minutes == null) {
            logger.warn("/timeout invalide: target ou minutes absent userId={}", event.getUser().getId());
            event.reply("❌ Parametres invalides.").setEphemeral(true).queue();
            return;
        }

        if (event.getMember() == null || !event.getMember().canInteract(target)) {
            logger.warn("/timeout refuse hierarchie: modId={}, targetId={}", event.getUser().getId(), target.getId());
            event.reply("❌ Vous ne pouvez pas timeout cet utilisateur (role superieur).")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        String finalReason = (reason == null || reason.isEmpty()) ? "Aucune raison specifiee" : reason;
        Duration duration = Duration.ofMinutes(minutes.longValue());
        logger.info("/timeout demande: modId={}, targetId={}, minutes={}, reason={}",
                event.getUser().getId(), target.getId(), minutes, finalReason);

        target.timeoutFor(duration).reason(finalReason).queue(
                success -> {
                    event.reply("✅ **" + target.getUser().getName() + "** a ete timeout pendant " + minutes + " minute(s). Raison : " + finalReason)
                            .queue();
                    logger.info("/timeout reussi: modId={}, targetId={}, minutes={}", event.getUser().getId(), target.getId(), minutes);

                    moderationLogListener.logAction(
                            event.getGuild(),
                            "TIMEOUT",
                            event.getMember(),
                            target,
                            finalReason,
                            "Duree: " + minutes + " minute(s)"
                    );
                },
                error -> {
                    logger.error("/timeout echec API: modId={}, targetId={}, minutes={}",
                            event.getUser().getId(), target.getId(), minutes, error);
                    event.reply("❌ Une erreur est survenue (permissions manquantes ?).")
                            .setEphemeral(true)
                            .queue();
                }
        );
    }
}
