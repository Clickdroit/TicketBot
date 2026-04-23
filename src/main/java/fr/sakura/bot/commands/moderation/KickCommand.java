package fr.sakura.bot.commands.moderation;



import fr.sakura.bot.commands.ICommand;

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

public class KickCommand implements ICommand {

    private static final Logger logger = LoggerFactory.getLogger(KickCommand.class);

    private final ModerationLogListener moderationLogListener;

    public KickCommand(ModerationLogListener moderationLogListener) {
        this.moderationLogListener = moderationLogListener;
    }

    @Override
    public String getName() {
        return "kick";
    }

    @Override
    public String getCategory() {
        return "ModÃƒÆ’Ã‚Â©ration";
    }

    @Override
    public SlashCommandData getCommandData() {
        return Commands.slash(getName(), "Expulse un membre du serveur")
                .addOptions(
                        new OptionData(OptionType.USER, "membre", "Le membre ÃƒÆ’Ã‚Â  expulser", true),
                        new OptionData(OptionType.STRING, "raison", "La raison de l'expulsion", false)
                )
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.KICK_MEMBERS));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        logger.debug("Execution /kick par userId={}", event.getUser().getId());

        if (event.getGuild() == null) {
            event.reply("ÃƒÂ¢Ã‚ÂÃ…â€™ Cette commande doit etre utilisee dans un serveur.").setEphemeral(true).queue();
            return;
        }

        var guild = event.getGuild();

        OptionMapping memberOption = event.getOption("membre");
        OptionMapping reasonOption = event.getOption("raison");
        String reason = reasonOption != null ? reasonOption.getAsString() : "Aucune raison spÃƒÆ’Ã‚Â©cifiÃƒÆ’Ã‚Â©e";

        if (memberOption == null) {
            event.reply("ÃƒÂ¢Ã‚ÂÃ…â€™ Membre introuvable.").setEphemeral(true).queue();
            return;
        }

        var targetUser = memberOption.getAsUser();
        if (targetUser == null) {
            event.reply("ÃƒÂ¢Ã‚ÂÃ…â€™ Utilisateur introuvable.").setEphemeral(true).queue();
            return;
        }

        Member target = guild.getMember(targetUser);

        if (target == null) {
            logger.warn("/kick cible non membre du serveur userId={} demandeurId={}",
                    targetUser.getId(), event.getUser().getId());
            event.reply("ÃƒÂ¢Ã‚ÂÃ…â€™ **" + targetUser.getName() + "** n'est plus membre de ce serveur.").setEphemeral(true).queue();
            return;
        }

        if (event.getMember() == null || !event.getMember().canInteract(target)) {
            logger.warn("/kick refuse hierarchie: modId={}, targetId={}", event.getUser().getId(), target.getId());
            event.reply("ÃƒÂ¢Ã‚ÂÃ…â€™ Vous ne pouvez pas expulser cet utilisateur (rÃƒÆ’Ã‚Â´le supÃƒÆ’Ã‚Â©rieur).").setEphemeral(true).queue();
            return;
        }

        logger.info("/kick demande: modId={}, targetId={}, reason={}", event.getUser().getId(), target.getId(), reason);

        guild.kick(target).reason(reason).queue(
                success -> {
                    event.reply("ÃƒÂ¢Ã…â€œÃ¢â‚¬Â¦ **" + target.getUser().getName() + "** a ÃƒÆ’Ã‚Â©tÃƒÆ’Ã‚Â© expulsÃƒÆ’Ã‚Â©. Raison : " + reason).queue();
                    logger.info("/kick reussi: modId={}, targetId={}", event.getUser().getId(), target.getId());

                    moderationLogListener.logAction(guild, "KICK", event.getMember(), target, reason, null);
                },
                error -> {
                    logger.error("/kick echec API: modId={}, targetId={}", event.getUser().getId(), target.getId(), error);
                    event.reply("ÃƒÂ¢Ã‚ÂÃ…â€™ Une erreur est survenue (Ai-je les bonnes permissions ?).").setEphemeral(true).queue();
                }
        );
    }
}
