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

public class KickCommand implements ICommand {

    private static final Logger logger = LoggerFactory.getLogger(KickCommand.class);

    private final ModerationLogger moderationLogger;

    public KickCommand(ModerationLogger moderationLogger) {
        this.moderationLogger = moderationLogger;
    }

    @Override
    public String getName() {
        return "kick";
    }

    @Override
    public SlashCommandData getCommandData() {
        return Commands.slash(getName(), "Expulse un membre du serveur")
                .addOptions(
                        new OptionData(OptionType.USER, "membre", "Le membre à expulser", true),
                        new OptionData(OptionType.STRING, "raison", "La raison de l'expulsion", false)
                )
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.KICK_MEMBERS));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        logger.debug("Execution /kick par userId={}", event.getUser().getId());
        Member target = event.getOption("membre").getAsMember();
        OptionMapping reasonOption = event.getOption("raison");
        String reason = reasonOption != null ? reasonOption.getAsString() : "Aucune raison spécifiée";

        if (target == null) {
            logger.warn("/kick cible introuvable userId={}", event.getUser().getId());
            event.reply("❌ Utilisateur introuvable dans ce serveur.").setEphemeral(true).queue();
            return;
        }

        if (!event.getMember().canInteract(target)) {
            logger.warn("/kick refuse hierarchie: modId={}, targetId={}", event.getUser().getId(), target.getId());
            event.reply("❌ Vous ne pouvez pas expulser cet utilisateur (rôle supérieur).").setEphemeral(true).queue();
            return;
        }

        logger.info("/kick demande: modId={}, targetId={}, reason={}", event.getUser().getId(), target.getId(), reason);

        event.getGuild().kick(target).reason(reason).queue(
            success -> {
                event.reply("✅ **" + target.getUser().getName() + "** a été expulsé. Raison : " + reason).queue();
                logger.info("/kick reussi: modId={}, targetId={}", event.getUser().getId(), target.getId());

                if (moderationLogger.isEnabled()) {
                    TextChannel logChannel = event.getGuild().getTextChannelById(moderationLogger.getLogChannelId());
                    moderationLogger.log(logChannel, "KICK", event.getMember(), target, reason, null);
                }
            },
            error -> {
                logger.error("/kick echec API: modId={}, targetId={}", event.getUser().getId(), target.getId(), error);
                event.reply("❌ Une erreur est survenue (Ai-je les bonnes permissions ?). ").setEphemeral(true).queue();
            }
        );
    }
}
