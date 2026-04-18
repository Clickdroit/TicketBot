package fr.sakura.bot.commands;

import fr.sakura.bot.utils.ModerationLogger;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

public class BanCommand implements ICommand {

    private static final Logger logger = LoggerFactory.getLogger(BanCommand.class);

    private final ModerationLogger moderationLogger;

    public BanCommand(ModerationLogger moderationLogger) {
        this.moderationLogger = moderationLogger;
    }

    @Override
    public String getName() {
        return "ban";
    }

    @Override
    public SlashCommandData getCommandData() {
        return Commands.slash(getName(), "Bannit un membre du serveur")
                .addOptions(
                        new OptionData(OptionType.USER, "membre", "Le membre à bannir", true),
                        new OptionData(OptionType.STRING, "raison", "La raison du bannissement", false)
                )
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.BAN_MEMBERS));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        logger.debug("Execution /ban par userId={}", event.getUser().getId());
        Member target = event.getOption("membre").getAsMember();
        OptionMapping reasonOption = event.getOption("raison");
        String reason = reasonOption != null ? reasonOption.getAsString() : "Aucune raison spécifiée";

        if (target == null) {
            logger.warn("/ban cible introuvable userId={}", event.getUser().getId());
            event.reply("❌ Utilisateur introuvable dans ce serveur.").setEphemeral(true).queue();
            return;
        }

        if (!event.getMember().canInteract(target)) {
            logger.warn("/ban refuse hierarchie: modId={}, targetId={}", event.getUser().getId(), target.getId());
            event.reply("❌ Vous ne pouvez pas bannir cet utilisateur (rôle supérieur).").setEphemeral(true).queue();
            return;
        }

        logger.info("/ban demande: modId={}, targetId={}, reason={}", event.getUser().getId(), target.getId(), reason);

        event.getGuild().ban(target, 0, TimeUnit.SECONDS).reason(reason).queue(
            success -> {
                event.reply("✅ **" + target.getUser().getName() + "** a été banni. Raison : " + reason).queue();
                logger.info("/ban reussi: modId={}, targetId={}", event.getUser().getId(), target.getId());

                if (moderationLogger.isEnabled()) {
                    TextChannel logChannel = event.getGuild().getTextChannelById(moderationLogger.getLogChannelId());
                    moderationLogger.log(logChannel, "BAN", event.getMember(), target, reason, null);
                }
            },
            error -> {
                logger.error("/ban echec API: modId={}, targetId={}", event.getUser().getId(), target.getId(), error);
                event.reply("❌ Une erreur est survenue (Ai-je les bonnes permissions ?). ").setEphemeral(true).queue();
            }
        );
    }
}
